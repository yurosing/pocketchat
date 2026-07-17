package com.pmchat.client;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NEW (5.1): мост к yt-dlp для проигрывания YouTube во встроенном плеере.
 *
 * Почему через yt-dlp: к 2026 напрямую отдать VLC ссылку на YouTube нельзя —
 * потоки заперты «proof-of-origin» токеном (докачивается только первый ~1 МБ,
 * дальше 403), комбинированных форматов почти не осталось, HLS не отдают.
 * yt-dlp умеет всё это обходить и постоянно обновляется под изменения сайта.
 *
 * Схема: находим (а при первом запуске — скачиваем в config/pmchat-bin)
 * yt-dlp, скачиваем им ролик в один mp4-файл (itag 18, 360p — без ffmpeg,
 * звук+видео уже вместе), а VLC играет уже локальный файл. Клиент
 * {@code default,mweb} обычно пролезает анонимно; если видео требует вход,
 * можно положить экспортированные куки в {@code config/pmchat-cookies.txt}.
 */
public final class PmYtDlp {

    private static final Logger LOGGER = LoggerFactory.getLogger("pmchat-ytdlp");

    /** Формат: только одиночные файлы со звуком И видео — иначе без ffmpeg не свести. */
    private static final String FORMAT = "b[vcodec!=none][acodec!=none][ext=mp4]/b[vcodec!=none][acodec!=none]/b";
    private static final String EXTRACTOR_ARGS = "youtube:player_client=default,mweb";
    private static final Pattern PROGRESS = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)%");

    private static volatile boolean downloadingBinary = false;

    private PmYtDlp() {
    }

    /** Папка для бинарника и временных видео: config/pmchat-bin. */
    private static File binDir() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "config/pmchat-bin");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private static String binName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "yt-dlp.exe";
        if (os.contains("mac")) return "yt-dlp_macos";
        return "yt-dlp_linux";
    }

    /** GitHub-релиз последней версии под текущую ОС. */
    private static String releaseUrl() {
        return "https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + binName();
    }

    /**
     * Путь к yt-dlp: сначала config/pmchat-bin, потом PATH. null — не нашли и
     * ещё не скачивали (скачивание запускается в {@link #ensureBinary}).
     */
    private static File existingBinary() {
        File local = new File(binDir(), binName());
        if (local.isFile()) return local;
        // Может, стоит системно (в PATH) — тогда используем короткое имя.
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String cmd = os.contains("win") ? "yt-dlp.exe" : "yt-dlp";
        for (String p : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            if (p.isBlank()) continue;
            File f = new File(p, cmd);
            if (f.isFile()) return f;
        }
        return null;
    }

    /**
     * Гарантирует наличие yt-dlp: возвращает путь к нему, при необходимости
     * скачивая бинарник с GitHub. {@code status} получает человекочитаемые
     * стадии. Возвращает null, если скачать не удалось.
     */
    private static File ensureBinary(Consumer<String> status) {
        File existing = existingBinary();
        if (existing != null) return existing;

        downloadingBinary = true;
        try {
            status.accept("yt-dlp");
            File dest = new File(binDir(), binName());
            File tmp = new File(binDir(), binName() + ".part");
            LOGGER.info("Downloading yt-dlp from {}", releaseUrl());
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(releaseUrl()))
                    .timeout(Duration.ofMinutes(3))
                    .header("User-Agent", "pmchat-mod")
                    .GET().build();
            HttpResponse<Path> resp = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp.toPath()));
            if (resp.statusCode() != 200 || !tmp.isFile() || tmp.length() < 1_000_000) {
                LOGGER.warn("yt-dlp download failed: HTTP {}, size {}", resp.statusCode(), tmp.length());
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return null;
            }
            Files.move(tmp.toPath(), dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            //noinspection ResultOfMethodCallIgnored
            dest.setExecutable(true);
            LOGGER.info("yt-dlp ready at {}", dest);
            return dest;
        } catch (Exception e) {
            LOGGER.warn("yt-dlp download error: {}", e.toString());
            return null;
        } finally {
            downloadingBinary = false;
        }
    }

    /**
     * Скачивает ролик в локальный mp4 и возвращает файл (звать с фонового
     * потока — блокирует!). {@code status} получает стадии («yt-dlp» пока
     * качается бинарник, «12%» — прогресс загрузки). null — не удалось
     * (нет yt-dlp / бот-проверка без кук / формат недоступен).
     */
    public static File download(String youtubeUrl, Consumer<String> status) {
        File bin = ensureBinary(status);
        if (bin == null) return null;

        String id = PmYouTube.videoId(youtubeUrl);
        if (id == null) id = "video";
        File out = new File(binDir(), "yt-" + id + ".mp4");
        // Чистим прошлый результат для этого id, чтобы не проиграть устаревший
        deleteQuiet(out);

        List<String> cmd = new ArrayList<>();
        cmd.add(bin.getAbsolutePath());
        cmd.add("--no-playlist");
        cmd.add("--no-warnings");
        cmd.add("--no-part");
        cmd.add("--extractor-args");
        cmd.add(EXTRACTOR_ARGS);
        cmd.add("-f");
        cmd.add(FORMAT);
        // Файл заранее не знаем по расширению — но формат ограничен mp4/одиночным,
        // ext будет mp4; фиксируем шаблон и потом ищем результат.
        cmd.add("-o");
        cmd.add(new File(binDir(), "yt-" + id + ".%(ext)s").getAbsolutePath());
        File cookies = new File(MinecraftClient.getInstance().runDirectory, "config/pmchat-cookies.txt");
        if (cookies.isFile()) {
            cmd.add("--cookies");
            cmd.add(cookies.getAbsolutePath());
        }
        cmd.add(youtubeUrl);

        Process proc = null;
        try {
            status.accept("0%");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            proc = pb.start();
            readProgress(proc.getInputStream(), status);
            boolean done = proc.waitFor(3, java.util.concurrent.TimeUnit.MINUTES);
            if (!done) {
                proc.destroyForcibly();
                LOGGER.warn("yt-dlp timed out for {}", youtubeUrl);
                return null;
            }
            if (proc.exitValue() != 0) {
                LOGGER.warn("yt-dlp exited {} for {}", proc.exitValue(), youtubeUrl);
                return null;
            }
            // Ищем реально созданный файл yt-<id>.*
            File produced = findProduced(id);
            if (produced == null || produced.length() == 0) {
                LOGGER.warn("yt-dlp finished but no output file for {}", youtubeUrl);
                return null;
            }
            return produced;
        } catch (Exception e) {
            LOGGER.warn("yt-dlp run failed: {}", e.toString());
            if (proc != null) proc.destroyForcibly();
            return null;
        }
    }

    /** Читает вывод yt-dlp (прогресс идёт через \r) и дёргает status процентами. */
    private static void readProgress(InputStream in, Consumer<String> status) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder tok = new StringBuilder();
        int c;
        while ((c = r.read()) != -1) {
            if (c == '\r' || c == '\n') {
                String line = tok.toString();
                tok.setLength(0);
                if (line.contains("[download]")) {
                    Matcher m = PROGRESS.matcher(line);
                    if (m.find()) {
                        int pct = (int) Math.round(Double.parseDouble(m.group(1)));
                        status.accept(pct + "%");
                    }
                }
            } else {
                tok.append((char) c);
            }
        }
    }

    private static File findProduced(String id) {
        File[] files = binDir().listFiles((d, name) ->
                name.startsWith("yt-" + id + ".") && !name.endsWith(".part"));
        if (files == null || files.length == 0) return null;
        File best = files[0];
        for (File f : files) if (f.lastModified() > best.lastModified()) best = f;
        return best;
    }

    private static void deleteQuiet(File f) {
        try {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        } catch (Exception ignored) {
        }
    }

    /** Удалить временный видеофайл, скачанный для проигрывания. */
    public static void cleanup(File f) {
        if (f != null) deleteQuiet(f);
    }

    public static boolean isDownloadingBinary() {
        return downloadingBinary;
    }
}
