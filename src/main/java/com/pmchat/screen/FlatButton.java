package com.pmchat.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Плоская белая кнопка "в стиле кошелька" — без ванильной каменной текстуры,
 * просто заливка + тонкая рамка, как современная карточка в UI.
 */
public class FlatButton extends ClickableWidget {

    public interface PressAction {
        void onPress(FlatButton button);
    }

    private final TextRenderer textRenderer;
    private final PressAction action;
    private final int bg;
    private final int bgHover;
    private final int border;
    private final int textColor;
    private final boolean centered;
    /** Если задан — вместо текста рисуется своя пиксельная иконка (см. PmIcons). */
    private String[] iconBitmap;

    /** Круглая кнопка (радиус = высота/2) — для кнопки отправки. */
    private boolean circular;
    /** Радиус скругления прямоугольной кнопки. */
    private int radius = 4;

    // Анимация: плавное наведение (0..1) и вспышка нажатия (спадает к 0)
    private float hoverAnim = 0f;
    private float pressAnim = 0f;
    private long lastRenderMs = 0L;

    public FlatButton(TextRenderer textRenderer, int x, int y, int width, int height, Text message,
                       int bg, int bgHover, int border, int textColor, boolean centered, PressAction action) {
        super(x, y, width, height, message);
        this.textRenderer = textRenderer;
        this.action = action;
        this.bg = bg;
        this.bgHover = bgHover;
        this.border = border;
        this.textColor = textColor;
        this.centered = centered;
    }

    public static FlatButton centered(TextRenderer textRenderer, int x, int y, int width, int height, Text message,
                                       int bg, int bgHover, int border, int textColor, PressAction action) {
        return new FlatButton(textRenderer, x, y, width, height, message, bg, bgHover, border, textColor, true, action);
    }

    /** Кнопка с собственной пиксельной иконкой вместо символа шрифта. */
    public FlatButton withIcon(String[] bitmap) {
        this.iconBitmap = bitmap;
        return this;
    }

    /** Сделать кнопку круглой (для кнопки отправки ➤). */
    public FlatButton circular() {
        this.circular = true;
        return this;
    }

    /** Задать радиус скругления прямоугольной кнопки. */
    public FlatButton radius(int r) {
        this.radius = r;
        return this;
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        pressAnim = 1f;
        action.onPress(this);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int rr = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x0 = getX();
        int y0 = getY();
        int w = getWidth();
        int h = getHeight();

        // Плавные анимации по времени (независимо от FPS)
        long now = System.currentTimeMillis();
        float dt = lastRenderMs == 0 ? 0f : Math.min(0.1f, (now - lastRenderMs) / 1000f);
        lastRenderMs = now;
        float target = isHovered() ? 1f : 0f;
        hoverAnim += (target - hoverAnim) * Math.min(1f, dt * 12f);
        pressAnim += (0f - pressAnim) * Math.min(1f, dt * 8f);

        int fill = lerpColor(bg, bgHover, hoverAnim);
        // Вспышка нажатия — подсветка поверх
        if (pressAnim > 0.01f) fill = lerpColor(fill, 0xFFFFFFFF, pressAnim * 0.18f);
        int r = circular ? h / 2 : radius;
        // Тонкая скруглённая рамка для чёткости + заливка поверх с отступом 1px
        PmScreen.fillRound(context, x0, y0, w, h, r, border);
        PmScreen.fillRound(context, x0 + 1, y0 + 1, w - 2, h - 2, Math.max(0, r - 1), fill);

        if (iconBitmap != null) {
            PmIcons.draw(context, iconBitmap, x0, y0, w, h, textColor);
            return;
        }

        Text message = getMessage();
        int textY = y0 + (getHeight() - textRenderer.fontHeight) / 2;
        if (centered) {
            int textX = x0 + getWidth() / 2 - textRenderer.getWidth(message) / 2;
            context.drawText(textRenderer, message, textX, textY, textColor, false);
        } else {
            context.drawText(textRenderer, message, x0 + 6, textY, textColor, false);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
