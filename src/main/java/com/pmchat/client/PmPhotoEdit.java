package com.pmchat.client;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Правка фото перед отправкой: поворот, зеркало, рисование от руки. Чистые
 * AWT-операции над {@link BufferedImage}, без обращений к Minecraft — экран
 * {@code com.pmchat.screen.PmPhotoEditScreen} держит текущий кадр и дёргает эти
 * методы на каждое действие пользователя, сам превращая результат в текстуру.
 */
public final class PmPhotoEdit {

    private PmPhotoEdit() {
    }

    /** Читает файл в ARGB-буфер, готовый к правке. */
    public static BufferedImage load(Path file) throws IOException {
        BufferedImage src = ImageIO.read(file.toFile());
        if (src == null) throw new IOException("Unsupported image: " + file);
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        return toArgb(src);
    }

    private static BufferedImage toArgb(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Поворот на 90° по часовой стрелке; ширина и высота меняются местами. */
    public static BufferedImage rotate90(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform t = new AffineTransform();
        t.translate(h, 0);
        t.rotate(Math.PI / 2);
        g.setTransform(t);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Зеркало по горизонтали. */
    public static BufferedImage flipHorizontal(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform t = new AffineTransform();
        t.translate(w, 0);
        t.scale(-1, 1);
        g.setTransform(t);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Рисует один сегмент от руки прямо поверх изображения (мутирует {@code img}). */
    public static void drawSegment(BufferedImage img, float x1, float y1, float x2, float y2,
                                    int argbColor, float strokeWidth) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(argbColor, true));
        g.draw(new Line2D.Float(x1, y1, x2, y2));
        g.dispose();
    }

    /** Глубокая копия — снимок для стека отмены. */
    public static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Кодирует в PNG и пишет во временный файл — тем же путём, что и вставка из буфера обмена. */
    public static Path saveTemp(BufferedImage img) throws IOException {
        File out = File.createTempFile("pmchat-edit", ".png");
        out.deleteOnExit();
        ImageIO.write(img, "png", out);
        return out.toPath();
    }

    /** PNG-байты без временного файла — для перезаливки текстуры превью в редакторе. */
    public static byte[] toPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }
}
