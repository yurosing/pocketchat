package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
        super(Component.translatable("pmchat.dm.confirm.title"));
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
        clearWidgets();
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int halfW = (PANEL_W - 24 - 6) / 2;
        addRenderableWidget(FlatButton.centered(font, px + 12, py + PANEL_H - 22, halfW, 16,
                Component.translatable("pmchat.dm.confirm.cancel"), BTN_BG, BTN_HOVER, BTN_BORDER, LABEL,
                btn -> onClose()));
        addRenderableWidget(FlatButton.centered(font, px + 18 + halfW, py + PANEL_H - 22, halfW, 16,
                Component.translatable("pmchat.dm.confirm.send"), 0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA,
                btn -> confirm()));
    }

    private void confirm() {
        if (sending) return;
        sending = true;
        Minecraft.getInstance().gui.setScreen(parent);
        onConfirmed.run();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90000000);
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, BG);
        context.outline(px, py, PANEL_W, PANEL_H, BORDER);

        Component title = getTitle();
        context.text(font, title, px + (PANEL_W - font.width(title)) / 2, py + 8, TITLE, false);

        Component ask = Component.translatable("pmchat.dm.confirm.ask", target, PmBackend.formatCoins(price));
        drawWrapped(context, ask, px + 12, py + 24, PANEL_W - 24, LABEL);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawWrapped(GuiGraphicsExtractor context, Component text, int x, int y, int maxW, int color) {
        for (var line : font.split(text, maxW)) {
            context.text(font, line, x, y, color, false);
            y += 10;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
