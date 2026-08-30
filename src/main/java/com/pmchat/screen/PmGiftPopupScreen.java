package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Всплывающая анимация полученного подарка (4.2) — в духе Telegram: значок
 * влетает с "отскоком", вокруг него кружат искры цвета редкости подарка,
 * подпись проявляется плавно. Показывается вместо обычного тоста, когда у
 * игрока сейчас не открыт другой экран (см. {@code PmChatClient} — фоновая
 * проверка {@link PmBackend#checkNewGifts}), иначе используется тост как
 * раньше, чтобы не перекрывать то, чем игрок сейчас занят.
 */
@Environment(EnvType.CLIENT)
public class PmGiftPopupScreen extends Screen {

    private static final int SPARKLES = 7;

    private final String from;
    private final PmBackend.Gift gift;
    private final String giftIdFallback;
    private final long openedAt = System.currentTimeMillis();
    private final int rarityColor;

    public PmGiftPopupScreen(String from, PmBackend.Gift gift, String giftIdFallback) {
        super(Component.translatable("pmchat.gift.popup.title"));
        this.from = from == null ? "" : from;
        this.gift = gift;
        this.giftIdFallback = giftIdFallback == null ? "" : giftIdFallback;
        this.rarityColor = PmBackend.rarityColor(gift != null ? gift.rarity : null);
    }

    @Override
    protected void init() {
        clearChildren();
        int cw = 90;
        addDrawableChild(FlatButton.centered(textRenderer, width / 2 - cw / 2, height / 2 + 88, cw, 18,
                Component.translatable("pmchat.gift.popup.thanks"), 0xFF20321F, 0xFF2A422A, rarityColor, 0xFFE8F0E0,
                btn -> close()));
    }

    private static float easeOutBack(float t) {
        t = Math.min(1f, Math.max(0f, t));
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        float p = t - 1;
        return 1 + c3 * p * p * p + c1 * p * p;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float t = (System.currentTimeMillis() - openedAt) / 1000f;
        int dimAlpha = (int) Math.min(150, t / 0.25f * 150);
        context.fill(0, 0, width, height, withAlpha(0x000000, dimAlpha));

        int cx = width / 2, cy = height / 2 - 8;
        float intro = easeOutBack(Math.min(1f, t / 0.45f));
        float fade = Math.min(1f, t / 0.35f);

        String icon = gift != null ? gift.icon : (giftIdFallback.isEmpty() ? "🎁" : giftIdFallback);
        String name = gift != null ? gift.name : giftIdFallback;

        // Свечение вокруг значка — несколько колец убывающей яркости цвета редкости.
        int glowBase = (int) (70 * fade);
        for (int ring = 4; ring >= 1; ring--) {
            int r = 40 + ring * 13;
            fillCircle(context, cx, cy, r, withAlpha(rarityColor, glowBase / ring));
        }

        // Искры, кружащие вокруг значка — угол вращения и лёгкая пульсация радиуса.
        for (int i = 0; i < SPARKLES; i++) {
            double angle = t * 1.6 + i * (Math.PI * 2 / SPARKLES);
            double radius = 58 + 8 * Math.sin(t * 3 + i);
            int sx = cx + (int) (Math.cos(angle) * radius);
            int sy = cy + (int) (Math.sin(angle) * radius * 0.7);
            float sparkleAlpha = (float) (0.5 + 0.5 * Math.sin(t * 4 + i * 1.3));
            String mark = (i % 2 == 0) ? "✦" : "·";
            drawCentered(context, mark, sx, sy, withAlpha(rarityColor, (int) (220 * fade * sparkleAlpha)));
        }

        // Значок подарка — крупно, с "отскоком" при появлении и лёгкой пульсацией после.
        float pulse = t > 0.45f ? 1f + 0.05f * (float) Math.sin(t * 4) : 1f;
        drawScaledCentered(context, icon, cx, cy - 18, 5.2f * intro * pulse, withAlpha(0xFFFFFF, (int) (255 * fade)));

        // Подпись: от кого и что подарили.
        Component fromText = Component.translatable("pmchat.gift.popup.from", from);
        Component nameText = Component.literal(name);
        drawCentered(context, fromText.getString(), cx, cy + 56, withAlpha(0xFFFFFF, (int) (220 * fade)));
        drawCentered(context, nameText.getString(), cx, cy + 70, withAlpha(rarityColor, (int) (255 * fade)));

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawCentered(GuiGraphicsExtractor ctx, String s, int centerX, int y, int color) {
        ctx.text(textRenderer, Component.literal(s), centerX - textRenderer.getWidth(s) / 2, y, color, false);
    }

    /** Текст с масштабом вокруг точки (centerX, y — центр по вертикали). */
    private void drawScaledCentered(GuiGraphicsExtractor ctx, String s, int centerX, int y, float scale, int color) {
        var m = ctx.pose();
        m.pushMatrix();
        m.translate(centerX, y);
        m.scale(scale, scale);
        ctx.text(textRenderer, Component.literal(s), -textRenderer.getWidth(s) / 2, -textRenderer.fontHeight / 2, color, false);
        m.popMatrix();
    }

    private static void fillCircle(GuiGraphicsExtractor ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) r * r - dy * dy);
            ctx.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        // Клик мимо кнопки тоже закрывает всплывашку — но не раньше, чем анимация
        // появления закончится, иначе случайный клик в момент показа её сразу скроет.
        if (super.mouseClicked(click, doubled)) return true;
        if ((System.currentTimeMillis() - openedAt) > 450) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
