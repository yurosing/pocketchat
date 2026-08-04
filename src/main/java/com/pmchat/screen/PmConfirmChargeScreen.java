package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Предупреждение перед отправкой платного ЛС: получатель настроил цену за
 * входящее сообщение (фича «Платные ЛС» из магазина возможностей, см.
 * {@link PmScreen#chargeIfNeeded}) — отправитель явно соглашается заплатить,
 * прежде чем монеты спишутся и сообщение уйдёт. Отмена просто закрывает
 * экран, ничего не отправляя и не списывая.
 */
@Environment(EnvType.CLIENT)
public class PmConfirmChargeScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int PANEL_H = 76;

    private final Screen parent;
    private final String target;
    private final long price;
    private final Runnable onConfirmed;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private boolean sending = false;

    public PmConfirmChargeScreen(Screen parent, String target, long price, Runnable onConfirmed) {
        super(Text.translatable("pmchat.dm.confirm.title"));
        this.parent = parent;
        this.target = target;
        this.price = price;
        this.onConfirmed = onConfirmed;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    @Override
    protected void init() {
        applyTheme();
        clearChildren();
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int halfW = (PANEL_W - 24 - 6) / 2;
        addDrawableChild(FlatButton.centered(textRenderer, px + 12, py + PANEL_H - 22, halfW, 16,
                Text.translatable("pmchat.dm.confirm.cancel"), BTN_BG, BTN_HOVER, BTN_BORDER, LABEL,
                btn -> close()));
        addDrawableChild(FlatButton.centered(textRenderer, px + 18 + halfW, py + PANEL_H - 22, halfW, 16,
                Text.translatable("pmchat.dm.confirm.send"), 0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA,
                btn -> confirm()));
    }

    private void confirm() {
        if (sending) return;
        sending = true;
        MinecraftClient.getInstance().setScreen(parent);
        onConfirmed.run();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90000000);
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, PANEL_H, BORDER);

        Text title = getTitle();
        context.drawText(textRenderer, title, px + (PANEL_W - textRenderer.getWidth(title)) / 2, py + 8, TITLE, false);

        Text ask = Text.translatable("pmchat.dm.confirm.ask", target, PmBackend.formatCoins(price));
        drawWrapped(context, ask, px + 12, py + 24, PANEL_W - 24, LABEL);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawWrapped(DrawContext context, Text text, int x, int y, int maxW, int color) {
        for (var line : textRenderer.wrapLines(text, maxW)) {
            context.drawText(textRenderer, line, x, y, color, false);
            y += 10;
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
