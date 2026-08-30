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
 * Жалоба на игрока (кнопка «Пожаловаться» в чужом профиле) — короткая причина
 * уходит на бэкенд ({@code POST /v1/report}), видна только админу.
 */
@Environment(EnvType.CLIENT)
public class PmReportScreen extends Screen {

    /** Не static final — подгоняются под размер экрана в init() (GUI Scale 4 и т.п.). */
    private int PANEL_W = 240;
    private int PANEL_H = 110;

    private final Screen parent;
    private final String target;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private EditBox reasonField;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmReportScreen(Screen parent, String target) {
        super(Component.translatable("pmchat.report.title", target));
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
        PANEL_W = Math.max(160, Math.min(240, width - 24));
        PANEL_H = Math.min(110, height - 16);
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int fx = px + 16;
        int fw = PANEL_W - 32;
        int y = py + 26;

        reasonField = new EditBox(font, fx, y, fw, 16, Component.translatable("pmchat.report.hint"));
        reasonField.setMaxLength(300);
        String hint = Component.translatable("pmchat.report.hint").getString();
        reasonField.setSuggestion(hint);
        reasonField.setResponder(s -> reasonField.setSuggestion(s.isEmpty() ? hint : null));
        addRenderableWidget(reasonField);
        y += 24;

        addRenderableWidget(FlatButton.centered(font, fx, y, fw, 16,
                Component.translatable("pmchat.report.send"), 0xFF5A2A22, 0xFF6E332A, 0xFFA0463A, 0xFFE07A6A,
                btn -> send()));

        addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    private void send() {
        String reason = reasonField.getValue().trim();
        if (reason.isEmpty()) return;
        PmBackend.report(target, reason, (ok, v, err) -> {
            if (ok) {
                status = Component.translatable("pmchat.report.ok");
                statusColor = 0xFF8FD8A8;
                reasonField.setValue("");
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
