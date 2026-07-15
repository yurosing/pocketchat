package com.pmchat.client;

import net.minecraft.client.texture.NativeImage;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Инлайн-превью видео (5.9): чистый Java-декодер JCodec извлекает кадры MP4/H.264
 * и отдаёт их как NativeImage для проигрывания в пузыре (без звука — звук через
 * внешний плеер по клику). Поддержка кодеков ограничена JCodec (в основном H.264
 * в MP4/MOV); что не декодируется — пузырь остаётся ссылкой на внешний плеер.
 */
public final class PmVideo {

    private static final int MAX_FRAMES = 48;   // сколько кадров показываем в цикле
    private static final int MAX_DECODE = 360;  // не декодируем длиннее (~первые секунды)
    private static final int MAX_SIDE = 240;     // ограничение размера кадра

    private PmVideo() {
    }

    /** Расширения, которые пробуем декодировать через JCodec. */
    public static boolean isVideo(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".m4v");
    }

    /** Декодирует видео в набор кадров (равномерная выборка) + фиксированная задержка. */
    public static PmGif.Frames decode(byte[] bytes) throws Exception {
        File tmp = File.createTempFile("pmchat-vid", ".mp4");
        tmp.deleteOnExit();
        Files.write(tmp.toPath(), bytes);
        try (SeekableByteChannel ch = NIOUtils.readableChannel(tmp)) {
            FrameGrab grab = FrameGrab.createFrameGrab(ch);

            // Первый проход: декодируем последовательно, берём каждый step-й кадр
            List<BufferedImage> raw = new ArrayList<>();
            int idx = 0;
            Picture pic;
            // сначала прикинем шаг: декодируем до MAX_DECODE, выбирая до MAX_FRAMES
            int step = 1;
            while ((pic = grab.getNativeFrame()) != null && idx < MAX_DECODE) {
                if (idx % step == 0) {
                    raw.add(AWTUtil.toBufferedImage(pic));
                    if (raw.size() > MAX_FRAMES) {
                        // слишком часто — прореживаем уже собранное и увеличиваем шаг
                        List<BufferedImage> thin = new ArrayList<>();
                        for (int i = 0; i < raw.size(); i += 2) thin.add(raw.get(i));
                        raw.clear();
                        raw.addAll(thin);
                        step *= 2;
                    }
                }
                idx++;
            }
            if (raw.isEmpty()) throw new IllegalStateException("no frames decoded");

            List<NativeImage> images = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            for (BufferedImage bi : raw) {
                images.add(toNative(scale(bi)));
                delays.add(90); // ~11 fps превью
            }
            return new PmGif.Frames(images, delays);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static BufferedImage scale(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= MAX_SIDE && h <= MAX_SIDE) return toArgb(src);
        float s = Math.min((float) MAX_SIDE / w, (float) MAX_SIDE / h);
        int nw = Math.max(1, Math.round(w * s)), nh = Math.max(1, Math.round(h * s));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static NativeImage toNative(BufferedImage img) throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        return NativeImage.read(png.toByteArray());
    }
}
