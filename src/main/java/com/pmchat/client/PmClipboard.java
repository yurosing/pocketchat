package com.pmchat.client;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Достаёт картинку из системного буфера обмена (Ctrl+V).
 * Основной путь — AWT (работает, если headless удалось отключить),
 * запасной — PowerShell (только Windows).
 */
public final class PmClipboard {

    private PmClipboard() {
    }

    /** Быстрая синхронная попытка через AWT. null — картинки нет или AWT недоступен. */
    public static Path tryAwtImage() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return null;
            }
            Image image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
            int w = image.getWidth(null);
            int h = image.getHeight(null);
            if (w <= 0 || h <= 0) return null;

            BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = buffered.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();

            File out = File.createTempFile("pmchat-clip", ".png");
            out.deleteOnExit();
            ImageIO.write(buffered, "png", out);
            return out.toPath();
        } catch (Throwable t) {
            PmChatClient.LOGGER.debug("AWT clipboard unavailable: {}", t.toString());
            return null;
        }
    }

    /** Запасной путь: PowerShell с -STA (только Windows). null — картинки нет. */
    public static CompletableFuture<Path> tryPowershellImage() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                File out = File.createTempFile("pmchat-clip", ".png");
                out.deleteOnExit();
                String script = "Add-Type -AssemblyName System.Windows.Forms;"
                        + "Add-Type -AssemblyName System.Drawing;"
                        + "$i=[System.Windows.Forms.Clipboard]::GetImage();"
                        + "if($i -ne $null){$i.Save('" + out.getAbsolutePath().replace("\\", "\\\\")
                        + "',[System.Drawing.Imaging.ImageFormat]::Png);exit 0}else{exit 1}";
                Process process = new ProcessBuilder(
                        "powershell", "-NoProfile", "-STA", "-Command", script)
                        .redirectErrorStream(true)
                        .start();
                if (!process.waitFor(8, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return null;
                }
                if (process.exitValue() == 0 && Files.exists(out.toPath()) && Files.size(out.toPath()) > 0) {
                    return out.toPath();
                }
                return null;
            } catch (Exception e) {
                PmChatClient.LOGGER.debug("PowerShell clipboard failed: {}", e.toString());
                return null;
            }
        });
    }
}
