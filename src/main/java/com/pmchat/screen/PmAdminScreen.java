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
 * Админ-панель PocketChat: рассылка всем игрокам, выдача своей валюты
 * (пока только так игроки могут её получить) и галочка верификации/
 * официальный аккаунт. Работает только если сервер ({@code server-pocketchat})
 * узнаёт в токене аккаунт ADMIN_USERNAME (по умолчанию tyurvib) и совпадает
 * {@link PmConfig#backendAdminSecret} — без этого все вызовы вернут 403.
 */
@Environment(EnvType.CLIENT)
public class PmAdminScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 210;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private TextFieldWidget broadcastField;
    private TextFieldWidget targetField;
    private TextFieldWidget amountField;
    private Text status = Text.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmAdminScreen(Screen parent) {
        super(Text.translatable("pmchat.admin.title"));
        this.parent = parent;
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

        int fx = px + 16;
        int fw = PANEL_W - 32;
        int y = py + 26;

        broadcastField = new TextFieldWidget(textRenderer, fx, y, fw, 16, Text.translatable("pmchat.admin.broadcast.hint"));
        broadcastField.setMaxLength(500);
        addDrawableChild(broadcastField);
        y += 20;
        addDrawableChild(FlatButton.centered(textRenderer, fx, y, fw, 16,
                Text.translatable("pmchat.admin.broadcast.send"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> doBroadcast()));
        y += 28;

        targetField = new TextFieldWidget(textRenderer, fx, y, fw, 16, Text.translatable("pmchat.admin.target.hint"));
        targetField.setMaxLength(32);
        addDrawableChild(targetField);
        y += 20;

        amountField = new TextFieldWidget(textRenderer, fx, y, (fw - 6) / 2, 16, Text.translatable("pmchat.admin.amount.hint"));
        amountField.setMaxLength(10);
        addDrawableChild(amountField);
        addDrawableChild(FlatButton.centered(textRenderer, fx + (fw - 6) / 2 + 6, y, (fw - 6) / 2, 16,
                Text.translatable("pmchat.admin.grant"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> doGrant()));
        y += 24;

        addDrawableChild(FlatButton.centered(textRenderer, fx, y, (fw - 6) / 2, 16,
                Text.translatable("pmchat.admin.verify.on"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFF8FD8A8,
                btn -> doVerify(true)));
        addDrawableChild(FlatButton.centered(textRenderer, fx + (fw - 6) / 2 + 6, y, (fw - 6) / 2, 16,
                Text.translatable("pmchat.admin.verify.off"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFFE07A6A,
                btn -> doVerify(false)));
        y += 24;

        addDrawableChild(FlatButton.centered(textRenderer, fx, y, fw, 16,
                Text.translatable("pmchat.admin.official"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> doOfficial()));

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    private void doBroadcast() {
        String msg = broadcastField.getText().trim();
        if (msg.isEmpty()) return;
        PmBackend.adminBroadcast(msg, (ok, v, err) -> {
            if (ok) {
                broadcastField.setText("");
                setStatus(Text.translatable("pmchat.admin.ok"), 0xFF8FD8A8);
            } else {
                setStatus(Text.translatable("pmchat.admin.fail", String.valueOf(err)), 0xFFE07A6A);
            }
        });
    }

    private void doGrant() {
        String target = targetField.getText().trim();
        long amount;
        try {
            amount = Long.parseLong(amountField.getText().trim());
        } catch (NumberFormatException e) {
            setStatus(Text.translatable("pmchat.admin.badamount"), 0xFFE07A6A);
            return;
        }
        if (target.isEmpty() || amount == 0) return;
        PmBackend.adminGrantCurrency(target, amount, (ok, v, err) -> {
            setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                    ok ? 0xFF8FD8A8 : 0xFFE07A6A);
        });
    }

    private void doVerify(boolean verified) {
        String target = targetField.getText().trim();
        if (target.isEmpty()) return;
        PmBackend.adminVerify(target, verified, (ok, v, err) -> {
            setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                    ok ? 0xFF8FD8A8 : 0xFFE07A6A);
        });
    }

    private void doOfficial() {
        String target = targetField.getText().trim();
        if (target.isEmpty()) return;
        PmBackend.adminSetOfficial(target, true, null, (ok, v, err) -> {
            setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                    ok ? 0xFF8FD8A8 : 0xFFE07A6A);
        });
    }

    private void setStatus(Text text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, PANEL_H, BORDER);

        context.drawText(textRenderer, getTitle(), px + (PANEL_W - textRenderer.getWidth(getTitle())) / 2, py + 8, TITLE, false);

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
