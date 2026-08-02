package com.pmchat.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Аватар официального аккаунта PocketChat — единственная встроенная в мод PNG-
 * текстура (assets/pmchat/textures/gui/pocketchat_logo.png), в остальном мод
 * рисует всё через PmIcons (битмапы) или локально/по сети загруженные картинки.
 * Регистрируется как обычная текстура один раз при первом использовании.
 */
public final class PmOfficialIcon {

    private static final Identifier RESOURCE = Identifier.of("pmchat", "textures/gui/pocketchat_logo.png");
    private static final Identifier TEXTURE_ID = Identifier.of("pmchat", "pocketchat_logo");

    private static boolean attempted;
    private static boolean ready;
    private static int width;
    private static int height;

    private PmOfficialIcon() {
    }

    private static void ensureLoaded() {
        if (attempted) return;
        attempted = true;
        MinecraftClient client = MinecraftClient.getInstance();
        Optional<Resource> res = client.getResourceManager().getResource(RESOURCE);
        if (res.isEmpty()) {
            PmChatClient.LOGGER.debug("PocketChat official icon resource not found: {}", RESOURCE);
            return;
        }
        try (InputStream in = res.get().getInputStream()) {
            byte[] bytes = in.readAllBytes();
            NativeImage image = NativeImage.read(bytes);
            width = image.getWidth();
            height = image.getHeight();
            client.getTextureManager().registerTexture(TEXTURE_ID,
                    new NativeImageBackedTexture(() -> "pmchat-official-icon", image));
            ready = true;
        } catch (IOException e) {
            PmChatClient.LOGGER.debug("PocketChat official icon load failed: {}", e.toString());
        }
    }

    public static boolean isReady() {
        ensureLoaded();
        return ready;
    }

    /** Рисует иконку вписанной в квадрат size×size (обрезая лишнее по центру, как аватар). */
    public static void draw(DrawContext context, int x, int y, int size) {
        ensureLoaded();
        if (!ready) return;
        float scale = Math.max((float) size / width, (float) size / height);
        int w = Math.round(width * scale);
        int h = Math.round(height * scale);
        int ix = x + (size - w) / 2;
        int iy = y + (size - h) / 2;
        context.enableScissor(x, y, x + size, y + size);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, ix, iy,
                0f, 0f, w, h, width, height, width, height);
        context.disableScissor();
    }
}
