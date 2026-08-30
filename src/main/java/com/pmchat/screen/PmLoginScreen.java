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
 * Экран своего логина/пароля к бэкенду PocketChat (не Mojang) — регистрация
 * либо вход существующим аккаунтом. Токен, который выдаёт бэкенд, сохраняется
 * в {@link PmConfig#backendToken} и дальше используется для кошелька, галочки
 * верификации и (если это аккаунт tyurvib) админ-панели.
 */
@Environment(EnvType.CLIENT)
public class PmLoginScreen extends Screen {

    /** Не static final — подгоняются под размер экрана в init() (GUI Scale 4 и т.п.). */
    private int PANEL_W = 240;
    private int PANEL_H = 172;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private EditBox userField;
    private EditBox passField;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;
    private boolean busy;

    public PmLoginScreen(Screen parent) {
        super(Component.translatable("pmchat.login.title"));
        this.parent = parent;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    private final java.util.List<Object[]> fieldLabels = new java.util.ArrayList<>();

    @Override
    protected void init() {
        applyTheme();
        clearWidgets();
        fieldLabels.clear();
        PANEL_W = Math.max(180, Math.min(240, width - 24));
        PANEL_H = Math.min(172, height - 16);
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int fx = px + 16;
        int fw = PANEL_W - 32;
        int y = py + 34;

        fieldLabels.add(new Object[]{"pmchat.login.username", fx, y - 10});
        userField = new EditBox(font, fx, y, fw, 16, Component.translatable("pmchat.login.username"));
        userField.setMaxLength(32);
        String prefillUser = config.backendToken.isBlank() ? PmChatClient.selfName() : "";
        userField.setValue(prefillUser);
        userField.setSuggestion(prefillUser.isEmpty() ? Component.translatable("pmchat.login.username").getString() : null);
        userField.setResponder(s -> userField.setSuggestion(
                s.isEmpty() ? Component.translatable("pmchat.login.username").getString() : null));
        addRenderableWidget(userField);
        y += 30;

        fieldLabels.add(new Object[]{"pmchat.login.password", fx, y - 10});
        passField = new EditBox(font, fx, y, fw, 16, Component.translatable("pmchat.login.password"));
        passField.setMaxLength(64);
        passField.setSuggestion(Component.translatable("pmchat.login.password").getString());
        passField.setResponder(s -> passField.setSuggestion(
                s.isEmpty() ? Component.translatable("pmchat.login.password").getString() : null));
        addRenderableWidget(passField);
        y += 30;

        addRenderableWidget(FlatButton.centered(font, fx, y, (fw - 6) / 2, 18,
                Component.translatable("pmchat.login.login"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> doLogin()));
        addRenderableWidget(FlatButton.centered(font, fx + (fw - 6) / 2 + 6, y, (fw - 6) / 2, 18,
                Component.translatable("pmchat.login.register"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> doRegister()));
        y += 26;

        if (PmBackend.hasAccount()) {
            addRenderableWidget(FlatButton.centered(font, fx, y, fw, 16,
                    Component.translatable("pmchat.login.logout"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFFE07A6A,
                    btn -> {
                        config.backendToken = "";
                        config.save();
                        init();
                    }));
        }

        addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    private void doLogin() {
        String u = userField.getValue().trim();
        String p = passField.getValue();
        if (u.isEmpty() || p.length() < 6) {
            setStatus(Component.translatable("pmchat.login.short"), 0xFFE07A6A);
            return;
        }
        setBusy(true);
        PmBackend.login(u, p, (ok, v, err) -> {
            setBusy(false);
            if (ok) {
                setStatus(Component.translatable("pmchat.login.ok"), 0xFF8FD8A8);
            } else {
                setStatus(Component.translatable("pmchat.login.fail", String.valueOf(err)), 0xFFE07A6A);
            }
        });
    }

    private void doRegister() {
        String u = userField.getValue().trim();
        String p = passField.getValue();
        if (u.isEmpty() || p.length() < 6) {
            setStatus(Component.translatable("pmchat.login.short"), 0xFFE07A6A);
            return;
        }
        setBusy(true);
        PmBackend.register(u, p, (ok, v, err) -> {
            setBusy(false);
            if (ok) {
                setStatus(Component.translatable("pmchat.login.ok"), 0xFF8FD8A8);
            } else {
                setStatus(Component.translatable("pmchat.login.fail", String.valueOf(err)), 0xFFE07A6A);
            }
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        if (userField != null) userField.active = !b;
        if (passField != null) passField.active = !b;
    }

    private void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, BG);
        context.outline(px, py, PANEL_W, PANEL_H, BORDER);

        context.text(font, getTitle(), px + (PANEL_W - font.getWidth(getTitle())) / 2, py + 8, TITLE, false);

        for (Object[] entry : fieldLabels) {
            context.text(font, Component.translatable((String) entry[0]), (int) entry[1], (int) entry[2], LABEL, false);
        }

        if (!status.getString().isEmpty()) {
            context.text(font, status, px + (PANEL_W - font.getWidth(status)) / 2, py + PANEL_H - 40, statusColor, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
