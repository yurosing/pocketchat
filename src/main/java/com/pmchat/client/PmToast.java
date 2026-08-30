package com.pmchat.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

/** Уведомление о новом ЛС: тёмная карточка с ником и превью. */
@Environment(EnvType.CLIENT)
public class PmToast implements Toast {

    private static final long DISPLAY_DURATION = 4000L;
    private static final int ACCENT = 0xFF6FBF8B;

    private final String sender;
    private final String preview;
    private long startTime = -1L;
    private Visibility visibility = Visibility.SHOW;

    public PmToast(String sender, String preview) {
        this.sender = sender;
        this.preview = preview;
    }

    @Override
    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (startTime < 0L) startTime = time;
        if (time - startTime >= DISPLAY_DURATION) {
            visibility = Visibility.HIDE;
        }
    }

    @Override
    public void draw(GuiGraphicsExtractor context, Font font, long startTime) {
        context.fill(0, 0, getWidth(), getHeight(), 0xE61C3644);
        context.outline(0, 0, getWidth(), getHeight(), ACCENT);
        context.fill(0, 0, 3, getHeight(), ACCENT);

        context.text(font, Component.literal("✉ " + sender), 9, 7, ACCENT, false);
        String text = preview;
        int maxW = getWidth() - 18;
        if (font.getWidth(text) > maxW) {
            text = font.trimToWidth(text, maxW - font.getWidth("…")) + "…";
        }
        context.text(font, Component.literal(text), 9, 19, 0xFFEDF3F0, false);
    }

    @Override
    public int getWidth() {
        return 190;
    }

    @Override
    public int getHeight() {
        return 32;
    }
}
