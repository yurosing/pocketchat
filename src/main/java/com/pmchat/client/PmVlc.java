package com.pmchat.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NEW (4.3): встроенный видеоплеер — играет .mp4/.webm и т.п. прямо в окне
 * мода через системный VLC (библиотека vlcj). Кадры приходят с потока VLC
 * сырым буфером RV32 (BGRA), мы копируем их в NativeImage и заливаем в
 * текстуру на игровом потоке — сам рендеринг VLC не трогает GL напрямую.
 *
 * Требует установленный VLC media player у игрока. Если VLC не найден,
 * {@link #isAvailable()} вернёт false и вызывающий код должен откатиться
 * на старое поведение (открыть ссылку во внешнем плеере).
 */
public final class PmVlc {

    private static final Logger LOGGER = LoggerFactory.getLogger("pmchat-vlc");
    private static volatile Boolean available; // null — ещё не проверяли
    private static MediaPlayerFactory factory;

    private PmVlc() {
    }

    /**
     * Проверка (один раз, лениво) — установлен ли VLC на этом компьютере.
     * Сначала пробуем стандартный автопоиск vlcj; если он не сработал (бывает
     * на Windows, если реестр читается иначе, чем ожидает библиотека),
     * дополнительно ищем сами — по реестру и по обычным путям установки —
     * и явно подсказываем JNA, где искать libvlc.dll.
     */
    public static synchronized boolean isAvailable() {
        if (available == null) {
            boolean found = false;
            try {
                found = new NativeDiscovery().discover();
            } catch (Throwable t) {
                LOGGER.warn("vlcj NativeDiscovery threw: {}", t.toString());
            }
            if (!found) {
                found = tryManualDiscovery();
            }
            if (found) {
                try {
                    factory = new MediaPlayerFactory("--no-video-title-show", "--quiet");
                } catch (Throwable t) {
                    LOGGER.warn("VLC found but MediaPlayerFactory failed to start: {}", t.toString());
                    found = false;
                }
            } else {
                LOGGER.warn("VLC not found — video will open externally instead of the built-in player. "
                        + "Checked the registry and the usual install paths.");
            }
            available = found;
        }
        return available;
    }

    /**
     * Ручной поиск VLC на Windows: читаем InstallDir из реестра (тот же ключ,
     * что пишет сам инсталлятор VLC) и на всякий случай проверяем обычные
     * пути установки. Найденную папку явно добавляем в пути поиска JNA —
     * после этого либо повторный автопоиск, либо сама библиотека найдёт
     * libvlc.dll при следующей попытке создать плеер.
     */
    private static boolean tryManualDiscovery() {
        java.util.List<String> candidates = new java.util.ArrayList<>();
        try {
            String fromRegistry = com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                    com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\VideoLAN\\VLC", "InstallDir");
            if (fromRegistry != null && !fromRegistry.isBlank()) candidates.add(fromRegistry);
        } catch (Throwable ignored) {
            // не Windows, ключа нет, или JNA ещё не готова читать реестр — не страшно
        }
        candidates.add("C:\\Program Files\\VideoLAN\\VLC");
        candidates.add("C:\\Program Files (x86)\\VideoLAN\\VLC");

        for (String dir : candidates) {
            java.io.File f = new java.io.File(dir, "libvlc.dll");
            if (f.isFile()) {
                LOGGER.info("Found VLC manually at: {}", dir);
                com.sun.jna.NativeLibrary.addSearchPath("libvlc", dir);
                com.sun.jna.NativeLibrary.addSearchPath("libvlccore", dir);
                try {
                    return new NativeDiscovery().discover();
                } catch (Throwable t) {
                    LOGGER.warn("Re-discovery after manual path hint still failed: {}", t.toString());
                    return false;
                }
            }
        }
        return false;
    }

    /** Один открытый сеанс проигрывания — одно видео, один плеер VLC. */
    public static class Session {
        private final EmbeddedMediaPlayer player;
        private final Identifier textureId;
        private final NativeImageBackedTexture texture;

        private volatile byte[] pendingFrame;
        private volatile int frameW, frameH;
        private final AtomicBoolean frameDirty = new AtomicBoolean(false);
        private final AtomicBoolean released = new AtomicBoolean(false);

        private volatile boolean playing = false;
        private volatile long lengthMs = 0;
        private volatile boolean error = false;
        private volatile float bufferPct = -1; // -1 — буферизация не сообщалась

        Session(String url) {
            this(url, null);
        }

        Session(String url, String audioSlaveUrl) {
            this.player = factory.mediaPlayers().newEmbeddedMediaPlayer();
            this.textureId = Identifier.of("pmchat", "video/" + System.nanoTime());
            NativeImage placeholder = new NativeImage(NativeImage.Format.RGBA, 2, 2, false);
            this.texture = new NativeImageBackedTexture(() -> "pmchat-video", placeholder);
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);

            BufferFormatCallback formatCb = new BufferFormatCallback() {
                @Override
                public uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
                getBufferFormat(int sourceWidth, int sourceHeight) {
                    frameW = sourceWidth;
                    frameH = sourceHeight;
                    return new RV32BufferFormat(sourceWidth, sourceHeight);
                }

                @Override
                public void allocatedBuffers(ByteBuffer[] buffers) {
                }
            };
            RenderCallback renderCb = (mediaPlayer, nativeBuffers, bufferFormat) -> {
                if (released.get()) return;
                ByteBuffer buf = nativeBuffers[0];
                int w = bufferFormat.getWidth(), h = bufferFormat.getHeight();
                byte[] copy = new byte[w * h * 4];
                buf.rewind();
                buf.get(copy);
                pendingFrame = copy;
                frameW = w;
                frameH = h;
                frameDirty.set(true);
            };
            player.videoSurface().set(new CallbackVideoSurface(
                    formatCb, renderCb, true, VideoSurfaceAdapters.getVideoSurfaceAdapter()));

            player.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
                @Override
                public void playing(MediaPlayer mp) {
                    playing = true;
                }

                @Override
                public void paused(MediaPlayer mp) {
                    playing = false;
                }

                @Override
                public void stopped(MediaPlayer mp) {
                    playing = false;
                }

                @Override
                public void lengthChanged(MediaPlayer mp, long newLength) {
                    lengthMs = newLength;
                }

                @Override
                public void buffering(MediaPlayer mp, float newCache) {
                    bufferPct = newCache;
                }

                @Override
                public void error(MediaPlayer mp) {
                    // VLC не смог открыть/декодировать — UI покажет кнопку
                    // «Открыть в браузере» сразу, а не по таймауту.
                    error = true;
                    LOGGER.warn("VLC failed to play: {}", url);
                }
            });

            // network-caching — запас на сетевые потоки (HTTP), иначе VLC с
            // дефолтным кэшем любит заикаться на первых секундах. Для YouTube
            // видео и звук приходят РАЗНЫМИ ссылками: звук отдаём VLC как
            // input-slave, он сводит их в один поток на лету.
            java.util.List<String> opts = new java.util.ArrayList<>();
            opts.add(":network-caching=3000");
            if (audioSlaveUrl != null && !audioSlaveUrl.isBlank()) {
                opts.add(":input-slave=" + audioSlaveUrl);
            }
            player.media().play(url, opts.toArray(new String[0]));
        }

        /** Вызывать каждый кадр рендера — переносит новый буфер VLC в текстуру Minecraft. */
        public void tick() {
            if (released.get() || !frameDirty.compareAndSet(true, false)) return;
            byte[] data = pendingFrame;
            int w = frameW, h = frameH;
            if (data == null || w <= 0 || h <= 0 || data.length < w * h * 4) return;
            try {
                NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
                int i = 0;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        // RV32 = BGRA байт-порядок в буфере
                        int b = data[i] & 0xFF;
                        int g = data[i + 1] & 0xFF;
                        int r = data[i + 2] & 0xFF;
                        int a = data[i + 3] & 0xFF;
                        img.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                        i += 4;
                    }
                }
                texture.setImage(img);
                texture.upload();
            } catch (Exception e) {
                LOGGER.debug("Video frame upload failed: {}", e.toString());
            }
        }

        public Identifier textureId() {
            return textureId;
        }

        public int width() {
            return frameW;
        }

        public int height() {
            return frameH;
        }

        public boolean isPlaying() {
            return playing;
        }

        /** true — VLC сообщил об ошибке воспроизведения (кадров уже не будет). */
        public boolean hasError() {
            return error;
        }

        /** Процент буферизации 0..100; -1 — VLC её не сообщал. */
        public float bufferPercent() {
            return bufferPct;
        }

        public void togglePause() {
            player.controls().pause();
        }

        public void setVolume(int percent) {
            player.audio().setVolume(Math.max(0, Math.min(200, percent)));
        }

        public int getVolume() {
            int v = player.audio().volume();
            return v < 0 ? 100 : v;
        }

        public void setRate(float rate) {
            player.controls().setRate(rate);
        }

        public float getRate() {
            return player.status().rate();
        }

        /** 0..1 */
        public void seekFraction(float fraction) {
            player.controls().setPosition(Math.max(0f, Math.min(1f, fraction)));
        }

        public float positionFraction() {
            return player.status().position();
        }

        public long timeMs() {
            return player.status().time();
        }

        public long lengthMs() {
            return lengthMs > 0 ? lengthMs : player.status().length();
        }

        public void release() {
            if (released.compareAndSet(false, true)) {
                try {
                    player.controls().stop();
                } catch (Exception ignored) {
                }
                try {
                    player.release();
                } catch (Exception ignored) {
                }
                MinecraftClient.getInstance().execute(() ->
                        MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId));
            }
        }
    }

    /** Открывает URL (http-ссылку на видео) как новый сеанс. Вызывать только если {@link #isAvailable()}. */
    public static Session open(String url) {
        return new Session(url);
    }

    /**
     * Как {@link #open(String)}, но со звуком из отдельной ссылки (input-slave) —
     * для YouTube, где видео и звук отдаются раздельными потоками.
     * {@code audioSlaveUrl} может быть null (тогда как обычный {@link #open(String)}).
     */
    public static Session open(String url, String audioSlaveUrl) {
        return new Session(url, audioSlaveUrl);
    }
}
