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
 * Обращение в поддержку (из настроек, вкладка «Аккаунт») — уходит на бэкенд
 * ({@code POST /v1/support}), видно только админу.
 */
@Environment(EnvType.CLIENT)
public class PmSupportScreen extends Screen {

    /** Не static final — подгоняются под размер экрана в init() (GUI Scale 4 и т.п.). */
    private int PANEL_W = 260;
    private int PANEL_H = 110;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private EditBox messageField;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmSupportScreen(Screen parent) {
        super(Component.translatable("pmchat.support.title"));
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
        clearWidgets();
        PANEL_W = Math.max(180, Math.min(260, width - 24));
        PANEL_H = Math.min(110, height - 16);
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int fx = px + 16;
        int fw = PANEL_W - 32;
        int y = py + 26;

        messageField = new EditBox(font, fx, y, fw, 16, Component.translatable("pmchat.support.hint"));
        messageField.setMaxLength(500);
        String hint = Component.translatable("pmchat.support.hint").getString();
        messageField.setSuggestion(hint);
        messageField.setResponder(s -> messageField.setSuggestion(s.isEmpty() ? hint : null));
        addRenderableWidget(messageField);
        y += 24;

        addRenderableWidget(FlatButton.centered(font, fx, y, fw, 16,
                Component.translatable("pmchat.support.send"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> send()));

        addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    private void send() {
        String message = messageField.getValue().trim();
        if (message.isEmpty()) return;
        PmBackend.support(message, (ok, v, err) -> {
            if (ok) {
                status = Component.translatable("pmchat.support.ok");
                statusColor = 0xFF8FD8A8;
                messageField.setValue("");
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
        context.text(font, title, px + (PANEL_W - font.getWidth(title)) / 2, py + 8, TITLE, false);

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
