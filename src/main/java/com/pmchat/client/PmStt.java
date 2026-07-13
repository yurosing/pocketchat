package com.pmchat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Speech-to-text через Vosk: полностью оффлайн после однократной
 * загрузки модели (~45 МБ). Говоришь — распознанный текст
 * вставляется в поле ввода.
 */
public final class PmStt {

    public enum State { NONE, DOWNLOADING, UNPACKING, LOADING, READY, LISTENING, ERROR }

    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);

    public static volatile State state = State.NONE;
    public static volatile int progressPct = 0;
    public static volatile String partialText = "";
    public static volatile String error = "";

    private static volatile boolean dirty = false;
    private static Model model;
    private static volatile boolean listening = false;
    private static TargetDataLine line;
    private static Consumer<String> onFinal;

    private PmStt() {
    }

    /** Экран забирает флаг «состояние поменялось» и перестраивает кнопку. */
    public static boolean consumeDirty() {
        if (dirty) {
            dirty = false;
            return true;
        }
        return false;
    }

    private static void setState(State s) {
        state = s;
        dirty = true;
    }

    private static Path modelRoot() {
        return FabricLoader.getInstance().getConfigDir().resolve("pmchat-stt");
    }

    /** Какой язык загружен в модель сейчас (-1 — ничего). */
    private static volatile int loadedLang = -1;

    private static int currentLang() {
        return PmChatClient.getConfig().sttLang == 1 ? 1 : 0;
    }

    private static String langCode(int lang) {
        return lang == 1 ? "en" : "ru";
    }

    private static String modelUrl(int lang) {
        PmConfig cfg = PmChatClient.getConfig();
        return lang == 1 ? cfg.sttModelUrlEn : cfg.sttModelUrlRu;
    }

    private static Path langRoot(int lang) {
        return modelRoot().resolve(langCode(lang));
    }

    /** Ищем распакованную модель: папка, где есть подкаталог conf. */
    private static Path findModelDir(int lang) {
        Path dir = walkForModel(langRoot(lang), false);
        if (dir == null && lang == 0) {
            // Старое расположение русской модели — прямо в pmchat-stt/,
            // но НЕ заходя в языковые подпапки (en/), иначе схватим чужую модель.
            dir = walkForModel(modelRoot(), true);
        }
        return dir;
    }

    private static boolean isModelDir(Path p) {
        return Files.isDirectory(p.resolve("conf")) || Files.exists(p.resolve("am/final.mdl"));
    }

    /**
     * @param skipLangDirs пропускать содержимое подпапок en/ru (для legacy-обхода корня)
     */
    private static Path walkForModel(Path root, boolean skipLangDirs) {
        if (!Files.isDirectory(root)) return null;
        try (var stream = Files.walk(root, 3)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> {
                        if (!skipLangDirs) return true;
                        // Отбрасываем всё, что лежит внутри en/ или ru/
                        Path rel = root.relativize(p);
                        String first = rel.getNameCount() > 0 ? rel.getName(0).toString() : "";
                        return !first.equals("en") && !first.equals("ru");
                    })
                    .filter(PmStt::isModelDir)
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Вызывается при смене языка в настройках: выгружаем чужую модель. */
    public static synchronized void onLanguageChanged() {
        if (listening) {
            stopListening();
        }
        if (model != null && loadedLang != currentLang()) {
            try {
                model.close();
            } catch (Exception ignored) {
            }
            model = null;
            loadedLang = -1;
            setState(State.NONE);
        }
    }

    /**
     * Модель занимает ~300–400 МБ нативной памяти. Если у системы нет
     * запаса — не грузим (иначе JVM умрёт без шанса поймать ошибку).
     */
    private static boolean enoughMemory() {
        try {
            var os = (com.sun.management.OperatingSystemMXBean)
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long free = os.getFreeMemorySize() + os.getFreeSwapSpaceSize();
            return free > 800L * 1024 * 1024;
        } catch (Throwable t) {
            return true; // не смогли измерить — не блокируем
        }
    }

    private static volatile long lastUseMs = 0;
    private static Thread idleWatcher;

    /** Выгружает модель после минуты простоя — память возвращается системе. */
    private static synchronized void startIdleWatcher() {
        if (idleWatcher != null) return;
        idleWatcher = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    return;
                }
                if (state == State.READY && !listening && model != null
                        && System.currentTimeMillis() - lastUseMs > 60000) {
                    try {
                        model.close();
                    } catch (Exception ignored) {
                    }
                    model = null;
                    setState(State.NONE);
                    PmChatClient.LOGGER.info("STT model unloaded after idle");
                }
            }
        }, "pmchat-stt-idle");
        idleWatcher.setDaemon(true);
        idleWatcher.start();
    }

    /** Скачивает (при необходимости) и загружает модель в фоне. */
    public static void ensureModelAsync() {
        if (state == State.READY || state == State.DOWNLOADING
                || state == State.UNPACKING || state == State.LOADING) {
            return;
        }
        int lang = currentLang();
        Thread thread = new Thread(() -> {
            try {
                // Загружена модель другого языка — освобождаем перед новой
                if (model != null && loadedLang != lang) {
                    try {
                        model.close();
                    } catch (Exception ignored) {
                    }
                    model = null;
                    loadedLang = -1;
                }

                Path dir = findModelDir(lang);
                if (dir == null) {
                    downloadAndUnpack(lang);
                    dir = findModelDir(lang);
                }
                if (dir == null) throw new IllegalStateException("model not found after unpack");

                if (!enoughMemory()) {
                    throw new IllegalStateException(
                            "мало свободной памяти (нужно ~800 МБ) — закройте лишние программы");
                }

                setState(State.LOADING);
                LibVosk.setLogLevel(LogLevel.WARNINGS);
                model = new Model(dir.toAbsolutePath().toString());
                loadedLang = lang;
                lastUseMs = System.currentTimeMillis();
                startIdleWatcher();
                setState(State.READY);
            } catch (Throwable t) {
                PmChatClient.LOGGER.warn("STT model failed: {}", t.toString());
                error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                setState(State.ERROR);
            }
        }, "pmchat-stt-model");
        thread.setDaemon(true);
        thread.start();
        setState(State.DOWNLOADING);
        progressPct = 0;
    }

    private static void downloadAndUnpack(int lang) throws Exception {
        Path root = langRoot(lang);
        Files.createDirectories(root);
        Path zip = root.resolve("model.zip");

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(modelUrl(lang)))
                        .timeout(Duration.ofMinutes(10))
                        .header("User-Agent", "pmchat-mod/1.0")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("download HTTP " + response.statusCode());
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);

        try (InputStream in = new BufferedInputStream(response.body());
             OutputStream out = Files.newOutputStream(zip)) {
            byte[] buffer = new byte[65536];
            long done = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                done += n;
                if (total > 0) {
                    int pct = (int) (done * 100 / total);
                    if (pct != progressPct) {
                        progressPct = pct;
                        dirty = true;
                    }
                }
            }
        }

        setState(State.UNPACKING);
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) continue; // zip-slip guard
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zin, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.deleteIfExists(zip);
    }

    // ---------- Распознавание ----------

    public static boolean isListening() {
        return listening;
    }

    /** Начать слушать микрофон; финальный текст придёт в callback. */
    public static void startListening(Consumer<String> callback) {
        if (listening || PmVoice.isRecording()) return;
        // Язык переключили, а модель осталась старая — перезагружаем
        if (state == State.READY && loadedLang != currentLang()) {
            ensureModelAsync();
            return;
        }
        if (state != State.READY) return;
        lastUseMs = System.currentTimeMillis();
        onFinal = callback;
        listening = true;
        partialText = "";
        setState(State.LISTENING);

        Thread thread = new Thread(() -> {
            Recognizer recognizer = null;
            try {
                recognizer = new Recognizer(model, 16000f);
                line = AudioSystem.getTargetDataLine(FORMAT);
                line.open(FORMAT);
                line.start();

                byte[] chunk = new byte[3200];
                while (listening) {
                    int n = line.read(chunk, 0, chunk.length);
                    if (n <= 0) continue;
                    if (recognizer.acceptWaveForm(chunk, n)) {
                        String text = extract(recognizer.getResult(), "text");
                        if (!text.isBlank()) {
                            deliver(text);
                        }
                        partialText = "";
                    } else {
                        partialText = extract(recognizer.getPartialResult(), "partial");
                    }
                }

                String finalText = extract(recognizer.getFinalResult(), "text");
                if (!finalText.isBlank()) {
                    deliver(finalText);
                }
            } catch (Throwable t) {
                PmChatClient.LOGGER.warn("STT listen failed: {}", t.toString());
                error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                setState(State.ERROR);
            } finally {
                listening = false;
                partialText = "";
                if (line != null) {
                    try {
                        line.stop();
                        line.close();
                    } catch (Exception ignored) {
                    }
                }
                if (recognizer != null) {
                    try {
                        recognizer.close();
                    } catch (Exception ignored) {
                    }
                }
                lastUseMs = System.currentTimeMillis();
                if (state == State.LISTENING) {
                    setState(State.READY);
                }
            }
        }, "pmchat-stt-listen");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stopListening() {
        listening = false;
    }

    private static void deliver(String text) {
        Consumer<String> callback = onFinal;
        if (callback != null) {
            MinecraftClient.getInstance().execute(() -> callback.accept(text));
        }
    }

    private static String extract(String json, String field) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String value = obj.has(field) ? obj.get(field).getAsString().trim() : "";
            return fixEncoding(value);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Vosk отдаёт UTF-8, но JNA на Windows декодирует строку системной
     * кодировкой (Cp1251) — русский текст превращается в «РїСЂРёРІРµС‚».
     * Критерий выбора — количество «мусорных» символов (ї, ‚, Ђ…):
     * в нормальном тексте их ноль, в кракозябрах — половина строки.
     */
    static String fixEncoding(String s) {
        if (s == null || s.isEmpty()) return s;
        int weirdOriginal = weirdScore(s);
        if (weirdOriginal == 0) {
            return s; // текст уже нормальный
        }
        try {
            byte[] raw = s.getBytes("windows-1251");
            String utf = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            if (!utf.contains("�") && weirdScore(utf) < weirdOriginal) {
                return utf;
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    /** Символы вне печатного ASCII, кириллицы и пробелов — признак кракозябр. */
    private static int weirdScore(String s) {
        int score = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 32 && c < 127)
                    || (c >= 'а' && c <= 'я') || (c >= 'А' && c <= 'Я')
                    || c == 'ё' || c == 'Ё';
            if (!ok) score++;
        }
        return score;
    }
}
