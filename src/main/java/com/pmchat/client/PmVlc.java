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

    /** Проверка (один раз, лениво) — установлен ли VLC на этом компьютере. */
    public static synchronized boolean isAvailable() {
        if (available == null) {
            try {
                available = new NativeDiscovery().discover();
                if (available) {
                    factory = new MediaPlayerFactory("--no-video-title-show", "--quiet");
                }
            } catch (Throwable t) {
                LOGGER.warn("VLC not available: {}", t.toString());
                available = false;
            }
        }
        return available;
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

        Session(String url) {
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
            });

            player.media().play(url);
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
}
