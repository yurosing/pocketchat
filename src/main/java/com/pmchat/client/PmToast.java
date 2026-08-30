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
    public Visibility getWantedVisibility() {
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
    public void extractRenderState(GuiGraphicsExtractor context, Font font, long startTime) {
        context.fill(0, 0, width(), height(), 0xE61C3644);
        context.outline(0, 0, width(), height(), ACCENT);
        context.fill(0, 0, 3, height(), ACCENT);

        context.text(font, Component.literal("✉ " + sender), 9, 7, ACCENT, false);
        String text = preview;
        int maxW = width() - 18;
        if (font.width(text) > maxW) {
            text = font.plainSubstrByWidth(text, maxW - font.width("…")) + "…";
        }
        context.text(font, Component.literal(text), 9, 19, 0xFFEDF3F0, false);
    }

    @Override
    public int width() {
        return 190;
    }

    @Override
    public int height() {
        return 32;
    }
}
