package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Прямой перевод монет игроку (кнопка «Отправить монеты» в чужом профиле) —
 * не подарок, просто перевод валюты ({@code POST /v1/coins/send}).
 */
@Environment(EnvType.CLIENT)
public class PmSendCoinsScreen extends Screen {

    private int PANEL_W = 220;
    private int PANEL_H = 122;

    private final Screen parent;
    private final String target;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private EditBox amountField;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmSendCoinsScreen(Screen parent, String target) {
        super(Component.translatable("pmchat.coins.title", target));
        this.parent = parent;
        this.target = target;
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
        PANEL_W = Math.max(160, Math.min(220, width - 24));
        PANEL_H = Math.min(122, height - 16);
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int fx = px + 16;
        int fw = PANEL_W - 32;
        int y = py + 34;

        amountField = new EditBox(font, fx, y, fw, 16, Component.translatable("pmchat.coins.hint"));
        amountField.setMaxLength(10);
        String hint = Component.translatable("pmchat.coins.hint").getString();
        amountField.setSuggestion(hint);
        amountField.setResponder(s -> amountField.setSuggestion(s.isEmpty() ? hint : null));
        addRenderableWidget(amountField);
        y += 24;

        addRenderableWidget(FlatButton.centered(font, fx, y, fw, 16,
                Component.translatable("pmchat.coins.send"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> send()));

        addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> onClose()));
    }

    private void send() {
        long amount;
        try {
            amount = Long.parseLong(amountField.getValue().trim());
        } catch (NumberFormatException e) {
            status = Component.translatable("pmchat.admin.badamount");
            statusColor = 0xFFE07A6A;
            return;
        }
        if (amount <= 0) return;
        PmBackend.sendCoins(target, amount, (ok, v, err) -> {
            if (ok) {
                status = Component.translatable("pmchat.coins.ok");
                statusColor = 0xFF8FD8A8;
                amountField.setValue("");
            } else {
                status = Component.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, BG);
        context.outline(px, py, PANEL_W, PANEL_H, BORDER);

        Component title = getTitle();
        context.text(font, title, px + (PANEL_W - font.width(title)) / 2, py + 8, TITLE, false);

        Long bal = PmBackend.cachedSelfBalance();
        String balStr = Component.translatable("pmchat.shop.balance", PmBackend.formatCoins(bal != null ? bal : 0L)).getString();
        context.text(font, balStr, px + 16, py + 20, PmBackend.CURRENCY_COLOR, false);

        if (!status.getString().isEmpty()) {
            context.text(font, status, px + (PANEL_W - font.width(status)) / 2, py + PANEL_H - 34, statusColor, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
