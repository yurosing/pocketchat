package com.pmchat.client;

import net.minecraft.client.texture.NativeImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Декодер анимированных GIF: собирает кадры (с учётом смещений частичных
 * кадров) и задержки, отдаёт готовые NativeImage для текстур.
 */
public final class PmGif {

    public record Frames(List<NativeImage> images, List<Integer> delaysMs) {
    }

    private static final int MAX_FRAMES = 60;
    private static final int MAX_SIDE = 512;

    private PmGif() {
    }

    public static Frames decode(byte[] bytes) throws Exception {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in, false);
            int count = Math.min(reader.getNumImages(true), MAX_FRAMES);
            if (count <= 0) throw new IllegalStateException("no frames");

            BufferedImage canvas = null;
            List<NativeImage> images = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                int[] meta = frameMeta(reader.getImageMetadata(i)); // {delayMs, x, y}

                if (canvas == null) {
                    int w = Math.min(frame.getWidth() + meta[1], MAX_SIDE);
                    int h = Math.min(frame.getHeight() + meta[2], MAX_SIDE);
                    canvas = new BufferedImage(Math.max(w, 1), Math.max(h, 1), BufferedImage.TYPE_INT_ARGB);
                }
                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, meta[1], meta[2], null);
                g.dispose();

                // BufferedImage -> PNG -> NativeImage (простой и надёжный путь)
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(canvas, "png", png);
                images.add(NativeImage.read(png.toByteArray()));
                delays.add(Math.max(20, meta[0]));
            }
            return new Frames(images, delays);
        } finally {
            reader.dispose();
        }
    }

    /** Задержка кадра (мс) и смещение из GIF-метаданных. */
    private static int[] frameMeta(IIOMetadata metadata) {
        int delay = 100, x = 0, y = 0;
        try {
            Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
            for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node.getNodeName().equals("GraphicControlExtension")) {
                    NamedNodeMap attrs = node.getAttributes();
                    delay = Integer.parseInt(attrs.getNamedItem("delayTime").getNodeValue()) * 10;
                } else if (node.getNodeName().equals("ImageDescriptor")) {
                    NamedNodeMap attrs = node.getAttributes();
                    x = Integer.parseInt(attrs.getNamedItem("imageLeftPosition").getNodeValue());
                    y = Integer.parseInt(attrs.getNamedItem("imageTopPosition").getNodeValue());
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{delay, x, y};
    }
}
