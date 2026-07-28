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

    /** Вырезает прямоугольник {@code (x,y,w,h)} из изображения (координаты — в пикселях картинки). */
    public static BufferedImage crop(BufferedImage src, int x, int y, int w, int h) {
        int cx = Math.max(0, Math.min(x, src.getWidth() - 1));
        int cy = Math.max(0, Math.min(y, src.getHeight() - 1));
        int cw = Math.max(1, Math.min(w, src.getWidth() - cx));
        int ch = Math.max(1, Math.min(h, src.getHeight() - cy));
        BufferedImage out = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, cw, ch, cx, cy, cx + cw, cy + ch, null);
        g.dispose();
        return out;
    }

    /**
     * Добавляет подпись под фото — как в Telegram: холст расширяется вниз на
     * тёмную плашку с текстом. Подпись впечатывается в саму картинку, так что
     * дальше едет обычным файлом по существующему конвейеру отправки.
     */
    public static BufferedImage addCaption(BufferedImage src, String text, int argbColor) {
        if (text == null || text.isBlank()) return src;
        int w = src.getWidth();
        java.awt.Font font = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN,
                Math.max(12, w / 22));
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        java.awt.FontMetrics fm = pg.getFontMetrics();
        int maxTextW = w - 24;
        java.util.List<String> lines = wrap(text, fm, maxTextW);
        pg.dispose();

        int lineH = fm.getHeight();
        int barH = lines.size() * lineH + 20;
        BufferedImage out = new BufferedImage(w, src.getHeight() + barH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, null);
        g.setColor(new Color(0xFF000000, true));
        g.fillRect(0, src.getHeight(), w, barH);
        g.setFont(font);
        g.setColor(new Color(argbColor, true));
        int ty = src.getHeight() + 14 + fm.getAscent();
        for (String line : lines) {
            int tx = (w - fm.stringWidth(line)) / 2;
            g.drawString(line, Math.max(12, tx), ty);
            ty += lineH;
        }
        g.dispose();
        return out;
    }

    private static java.util.List<String> wrap(String text, java.awt.FontMetrics fm, int maxW) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (fm.stringWidth(test) > maxW && !cur.isEmpty()) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
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
