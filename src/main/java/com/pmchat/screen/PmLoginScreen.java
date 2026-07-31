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
 * Экран своего логина/пароля к бэкенду PocketChat (не Mojang) — регистрация
 * либо вход существующим аккаунтом. Токен, который выдаёт бэкенд, сохраняется
 * в {@link PmConfig#backendToken} и дальше используется для кошелька, галочки
 * верификации и (если это аккаунт tyurvib) админ-панели.
 */
@Environment(EnvType.CLIENT)
public class PmLoginScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 150;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private TextFieldWidget userField;
    private TextFieldWidget passField;
    private Text status = Text.empty();
    private int statusColor = 0xFFAAAAAA;
    private boolean busy;

    public PmLoginScreen(Screen parent) {
        super(Text.translatable("pmchat.login.title"));
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
        int y = py + 30;

        userField = new TextFieldWidget(textRenderer, fx, y, fw, 16, Text.translatable("pmchat.login.username"));
        userField.setMaxLength(32);
        userField.setText(config.backendToken.isBlank() ? PmChatClient.selfName() : "");
        addDrawableChild(userField);
        y += 24;

        passField = new TextFieldWidget(textRenderer, fx, y, fw, 16, Text.translatable("pmchat.login.password"));
        passField.setMaxLength(64);
        addDrawableChild(passField);
        y += 30;

        addDrawableChild(FlatButton.centered(textRenderer, fx, y, (fw - 6) / 2, 18,
                Text.translatable("pmchat.login.login"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> doLogin()));
        addDrawableChild(FlatButton.centered(textRenderer, fx + (fw - 6) / 2 + 6, y, (fw - 6) / 2, 18,
                Text.translatable("pmchat.login.register"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> doRegister()));
        y += 26;

        if (PmBackend.hasAccount()) {
            addDrawableChild(FlatButton.centered(textRenderer, fx, y, fw, 16,
                    Text.translatable("pmchat.login.logout"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFFE07A6A,
                    btn -> {
                        config.backendToken = "";
                        config.save();
                        init();
                    }));
        }

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    private void doLogin() {
        String u = userField.getText().trim();
        String p = passField.getText();
        if (u.isEmpty() || p.length() < 6) {
            setStatus(Text.translatable("pmchat.login.short"), 0xFFE07A6A);
            return;
        }
        setBusy(true);
        PmBackend.login(u, p, (ok, v, err) -> {
            setBusy(false);
            if (ok) {
                setStatus(Text.translatable("pmchat.login.ok"), 0xFF8FD8A8);
            } else {
                setStatus(Text.translatable("pmchat.login.fail", String.valueOf(err)), 0xFFE07A6A);
            }
        });
    }

    private void doRegister() {
        String u = userField.getText().trim();
        String p = passField.getText();
        if (u.isEmpty() || p.length() < 6) {
            setStatus(Text.translatable("pmchat.login.short"), 0xFFE07A6A);
            return;
        }
        setBusy(true);
        PmBackend.register(u, p, (ok, v, err) -> {
            setBusy(false);
            if (ok) {
                setStatus(Text.translatable("pmchat.login.ok"), 0xFF8FD8A8);
            } else {
                setStatus(Text.translatable("pmchat.login.fail", String.valueOf(err)), 0xFFE07A6A);
            }
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        if (userField != null) userField.active = !b;
        if (passField != null) passField.active = !b;
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
