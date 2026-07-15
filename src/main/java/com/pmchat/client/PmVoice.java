package com.pmchat.client;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Голосовые: запись с микрофона в WAV (16 кГц моно), загрузка на хостинг
 * как у фото, воспроизведение скачанных голосовых через Clip.
 */
public final class PmVoice {

    public static final int MAX_SECONDS = 20;

    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile boolean recording = false;
    private static TargetDataLine line;
    private static ByteArrayOutputStream recorded;
    private static long recordStart;

    private static final Map<String, byte[]> AUDIO_CACHE = new ConcurrentHashMap<>();
    private static volatile Clip playingClip;
    private static volatile String playingId;

    private PmVoice() {
    }

    // ---------- Запись ----------

    public static boolean isRecording() {
        return recording;
    }

    public static int recordedSeconds() {
        return recording ? (int) ((System.currentTimeMillis() - recordStart) / 1000) : 0;
    }

    public static boolean startRecording() {
        if (recording) return false;
        try {
            line = AudioSystem.getTargetDataLine(FORMAT);
            line.open(FORMAT);
            line.start();
            recorded = new ByteArrayOutputStream();
            recordStart = System.currentTimeMillis();
            recording = true;

            Thread reader = new Thread(() -> {
                byte[] chunk = new byte[3200];
                while (recording) {
                    int n = line.read(chunk, 0, chunk.length);
                    if (n > 0) recorded.write(chunk, 0, n);
                    if (recordedSeconds() >= MAX_SECONDS) break;
                }
            }, "pmchat-voice-rec");
            reader.setDaemon(true);
            reader.start();
            return true;
        } catch (Exception e) {
            PmChatClient.LOGGER.warn("Mic unavailable: {}", e.toString());
            recording = false;
            return false;
        }
    }

    /** Останавливает запись и сохраняет WAV во временный файл. */
    public static Path stopRecording() {
        if (!recording) return null;
        recording = false;
        int seconds = (int) Math.max(1, (System.currentTimeMillis() - recordStart) / 1000);
        try {
            line.stop();
            line.close();
            byte[] bytes = recorded.toByteArray();
            if (bytes.length < 3200) return null; // меньше десятой секунды — мусор

            File out = File.createTempFile("pmchat-voice", ".wav");
            out.deleteOnExit();
            try (AudioInputStream stream = new AudioInputStream(
                    new ByteArrayInputStream(bytes), FORMAT, bytes.length / FORMAT.getFrameSize())) {
                AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out);
            }
            lastDurationSeconds = Math.min(seconds, MAX_SECONDS);
            return out.toPath();
        } catch (Exception e) {
            PmChatClient.LOGGER.warn("Voice save failed: {}", e.toString());
            return null;
        }
    }

    /** Длительность аудиофайла (WAV/AU/AIFF) в секундах, или 0 если не удалось. */
    public static int fileDurationSeconds(Path file) {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat fmt = in.getFormat();
            long frames = in.getFrameLength();
            if (frames > 0 && fmt.getFrameRate() > 0) {
                return Math.max(1, Math.round(frames / fmt.getFrameRate()));
            }
        } catch (Exception e) {
            PmChatClient.LOGGER.warn("Audio duration failed: {}", e.toString());
        }
        return 0;
    }

    private static int lastDurationSeconds = 1;

    public static int lastDuration() {
        return lastDurationSeconds;
    }

    // ---------- Воспроизведение ----------

    public static boolean isPlaying(String id) {
        return id.equals(playingId) && playingClip != null && playingClip.isRunning();
    }

    /** Прогресс воспроизведения 0..1 для полоски в пузыре. */
    public static float progress(String id) {
        Clip clip = playingClip;
        if (!id.equals(playingId) || clip == null || clip.getMicrosecondLength() <= 0) return 0f;
        return (float) clip.getMicrosecondPosition() / clip.getMicrosecondLength();
    }

    private static final Map<String, Boolean> FAILED = new ConcurrentHashMap<>();

    /** Кладёт свою запись в кэш (память + диск) — играет без скачивания. */
    public static void cache(String hostCode, String id, byte[] bytes) {
        AUDIO_CACHE.put(id, bytes);
        PmImages.saveToDisk(hostCode, id, bytes);
    }

    public static boolean isFailed(String id) {
        return FAILED.getOrDefault(id, false);
    }

    public static void togglePlay(String hostCode, String id) {
        if (isPlaying(id)) {
            stopPlayback();
            return;
        }
        stopPlayback();
        playingId = id;

        CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = AUDIO_CACHE.get(id);
                if (bytes == null) {
                    try {
                        java.nio.file.Path cached = PmImages.mediaFile(hostCode, id);
                        if (java.nio.file.Files.exists(cached)) {
                            bytes = java.nio.file.Files.readAllBytes(cached);
                            AUDIO_CACHE.put(id, bytes);
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (bytes == null) {
                    HttpResponse<byte[]> response = HTTP.send(HttpRequest.newBuilder()
                                    .uri(URI.create(PmHosts.baseUrl(hostCode) + id))
                                    .timeout(Duration.ofSeconds(15))
                                    .header("User-Agent", "pmchat-mod/1.0")
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() != 200) {
                        FAILED.put(id, true);
                        return;
                    }
                    bytes = response.body();
                    AUDIO_CACHE.put(id, bytes);
                    PmImages.saveToDisk(hostCode, id, bytes);
                    FAILED.remove(id);
                }
                if (!id.equals(playingId)) return;

                AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                playingClip = clip;
                clip.start();
            } catch (Exception e) {
                PmChatClient.LOGGER.warn("Voice playback failed: {}", e.toString());
                FAILED.put(id, true);
            }
        });
    }

    public static void stopPlayback() {
        Clip clip = playingClip;
        if (clip != null) {
            try {
                clip.stop();
                clip.close();
            } catch (Exception ignored) {
            }
        }
        playingClip = null;
        playingId = null;
    }
}
