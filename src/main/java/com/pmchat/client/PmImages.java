package com.pmchat.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Отправка фото без серверного плагина: файл грузится на анонимный
 * хостинг (catbox.moe), по чату летит только короткая метка [img:id],
 * получатель с модом скачивает и рендерит картинку в переписке.
 */
public final class PmImages {

    public enum State { LOADING, READY, FAILED }

    public static class Entry {
        public volatile State state = State.LOADING;
        public volatile Identifier textureId;
        public volatile int width;
        public volatile int height;

        // Анимация (GIF): кадры и задержки
        public volatile Identifier[] frames;
        public volatile int[] delaysMs;
        public volatile int totalDelayMs;

        /** Текущая текстура: для GIF — кадр по времени, иначе статичная. */
        public Identifier currentTexture() {
            Identifier[] f = frames;
            if (f == null || f.length == 0 || totalDelayMs <= 0) return textureId;
            long t = System.currentTimeMillis() % totalDelayMs;
            for (int i = 0; i < f.length; i++) {
                t -= delaysMs[i];
                if (t < 0) return f[i];
            }
            return f[f.length - 1];
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "pmchat-images");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private PmImages() {
    }

    // ---------- Постоянный кэш медиа на диске ----------

    static Path mediaDir() {
        Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("pmchat-media");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir;
    }

    static Path mediaFile(String hostCode, String id) {
        String safe = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return mediaDir().resolve(hostCode + "_" + safe);
    }

    static void saveToDisk(String hostCode, String id, byte[] bytes) {
        try {
            Files.write(mediaFile(hostCode, id), bytes);
        } catch (Exception e) {
            PmChatClient.LOGGER.debug("media cache write failed: {}", e.toString());
        }
    }

    /**
     * Грузит файл с фолбэком по хостам.
     * @return {код хоста, id файла} — например {"q", "abc123.png"}
     */
    public static CompletableFuture<String[]> upload(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] fileBytes = Files.readAllBytes(file);
                return PmHosts.upload(fileBytes, file.getFileName().toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, EXECUTOR);
    }

    /** Регистрирует байты как текстуру(ы) в Entry: PNG статично, GIF — кадрами. */
    private static void register(String id, byte[] bytes, Entry entry) {
        MinecraftClient client = MinecraftClient.getInstance();
        String safe = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        try {
            if (safe.endsWith(".gif") || PmVideo.isVideo(safe)) {
                PmGif.Frames gif = safe.endsWith(".gif") ? PmGif.decode(bytes) : PmVideo.decode(bytes);
                client.execute(() -> {
                    try {
                        int n = gif.images().size();
                        Identifier[] ids = new Identifier[n];
                        int[] delays = new int[n];
                        int total = 0;
                        for (int i = 0; i < n; i++) {
                            Identifier texId = Identifier.of("pmchat", "img/" + safe + "_f" + i);
                            NativeImage img = gif.images().get(i);
                            final String frameName = safe + "_f" + i;
                            client.getTextureManager().registerTexture(texId,
                                    new NativeImageBackedTexture(() -> "pmchat-" + frameName, img));
                            ids[i] = texId;
                            delays[i] = gif.delaysMs().get(i);
                            total += delays[i];
                        }
                        entry.width = gif.images().get(0).getWidth();
                        entry.height = gif.images().get(0).getHeight();
                        entry.frames = ids;
                        entry.delaysMs = delays;
                        entry.totalDelayMs = Math.max(total, 1);
                        entry.textureId = ids[0];
                        entry.state = State.READY;
                    } catch (Exception e) {
                        PmChatClient.LOGGER.warn("GIF register failed {}: {}", id, e.toString());
                        entry.state = State.FAILED;
                    }
                });
            } else {
                NativeImage image = NativeImage.read(bytes);
                client.execute(() -> {
                    try {
                        Identifier texId = Identifier.of("pmchat", "img/" + safe);
                        client.getTextureManager().registerTexture(texId,
                                new NativeImageBackedTexture(() -> "pmchat-" + safe, image));
                        entry.width = image.getWidth();
                        entry.height = image.getHeight();
                        entry.textureId = texId;
                        entry.state = State.READY;
                    } catch (Exception e) {
                        entry.state = State.FAILED;
                    }
                });
            }
        } catch (Exception e) {
            PmChatClient.LOGGER.warn("Image decode failed {}: {}", id, e.toString());
            entry.state = State.FAILED;
        }
    }

    /** Загружает локальный файл (обои/аватарка) как текстуру, кэш по ключу. */
    public static Entry loadLocal(String key, Path file) {
        return CACHE.computeIfAbsent("local|" + key, k -> {
            Entry entry = new Entry();
            try {
                register(key, Files.readAllBytes(file), entry);
            } catch (Exception e) {
                entry.state = State.FAILED;
            }
            return entry;
        });
    }

    /** Сбрасывает кэш локального файла (например при смене обоев). */
    public static void forgetLocal(String key) {
        CACHE.remove("local|" + key);
    }

    /** Регистрирует картинку сразу из локальных байтов (своя загрузка). */
    public static void preload(String hostCode, String id, byte[] bytes) {
        String key = hostCode + "|" + id;
        if (CACHE.containsKey(key)) return;
        Entry entry = new Entry();
        CACHE.put(key, entry);
        saveToDisk(hostCode, id, bytes);
        register(id, bytes, entry);
    }

    /** Достаёт (или начинает грузить) текстуру картинки по хосту и id. */
    public static Entry get(String hostCode, String id) {
        return CACHE.computeIfAbsent(hostCode + "|" + id, key -> {
            Entry entry = new Entry();
            EXECUTOR.submit(() -> {
                // Сначала постоянный кэш на диске — работает без сети
                try {
                    Path cached = mediaFile(hostCode, id);
                    if (Files.exists(cached)) {
                        register(id, Files.readAllBytes(cached), entry);
                        return;
                    }
                } catch (Exception ignored) {
                }
                download(hostCode, id, entry);
            });
            return entry;
        });
    }

    private static void download(String hostCode, String id, Entry entry) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PmHosts.baseUrl(hostCode) + id))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "pmchat-mod/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("download failed: " + response.statusCode());
            }
            saveToDisk(hostCode, id, response.body());
            register(id, response.body(), entry);
        } catch (Exception e) {
            PmChatClient.LOGGER.warn("Failed to fetch image {}: {}", id, e.getMessage());
            entry.state = State.FAILED;
        }
    }
}
