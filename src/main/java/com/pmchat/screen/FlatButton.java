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

    @Override
    public void onClick(Click click, boolean doubled) {
        action.onPress(this);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();

        int fill = isHovered() ? bgHover : bg;
        context.fill(x0, y0, x1, y1, fill);
        context.drawStrokedRectangle(x0, y0, getWidth(), getHeight(), border);

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
