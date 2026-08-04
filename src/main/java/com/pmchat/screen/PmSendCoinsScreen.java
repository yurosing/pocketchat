package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Прямой перевод монет игроку (кнопка «Отправить монеты» в чужом профиле) —
 * не подарок, просто перевод валюты ({@code POST /v1/coins/send}).
 */
@Environment(EnvType.CLIENT)
public class PmSendCoinsScreen extends Screen {

    private int PANEL_W = 220;
    private int PANEL_H = 108;

    private final Screen parent;
    private final String target;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private TextFieldWidget amountField;
    private Text status = Text.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmSendCoinsScreen(Screen parent, String target) {
        super(Text.translatable("pmchat.coins.title", target));
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
        clearChildren();
        PANEL_W = Math.max(160, Math.min(220, width - 24));
        PANEL_H = Math.min(108, height - 16);
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int fx = px + 16;
        int fw = PANEL_W - 32;
        int y = py + 34;

        amountField = new TextFieldWidget(textRenderer, fx, y, fw, 16, Text.translatable("pmchat.coins.hint"));
        amountField.setMaxLength(10);
        String hint = Text.translatable("pmchat.coins.hint").getString();
        amountField.setSuggestion(hint);
        amountField.setChangedListener(s -> amountField.setSuggestion(s.isEmpty() ? hint : null));
        addDrawableChild(amountField);
        y += 24;

        addDrawableChild(FlatButton.centered(textRenderer, fx, y, fw, 16,
                Text.translatable("pmchat.coins.send"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> send()));

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    private void send() {
        long amount;
        try {
            amount = Long.parseLong(amountField.getText().trim());
        } catch (NumberFormatException e) {
            status = Text.translatable("pmchat.admin.badamount");
            statusColor = 0xFFE07A6A;
            return;
        }
        if (amount <= 0) return;
        PmBackend.sendCoins(target, amount, (ok, v, err) -> {
            if (ok) {
                status = Text.translatable("pmchat.coins.ok");
                statusColor = 0xFF8FD8A8;
                amountField.setText("");
            } else {
                status = Text.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, PANEL_H, BORDER);

        Text title = getTitle();
        context.drawText(textRenderer, title, px + (PANEL_W - textRenderer.getWidth(title)) / 2, py + 8, TITLE, false);

        Long bal = PmBackend.cachedSelfBalance();
        String balStr = Text.translatable("pmchat.shop.balance", PmBackend.formatCoins(bal != null ? bal : 0L)).getString();
        context.drawText(textRenderer, balStr, px + 16, py + 20, PmBackend.CURRENCY_COLOR, false);

        if (!status.getString().isEmpty()) {
            context.drawText(textRenderer, status, px + (PANEL_W - textRenderer.getWidth(status)) / 2, py + PANEL_H - 40, statusColor, false);
        }

        super.render(context, mouseX, mouseY, delta);
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
