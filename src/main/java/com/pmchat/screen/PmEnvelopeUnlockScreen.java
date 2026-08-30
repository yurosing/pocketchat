package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmMessage;
import com.pmchat.client.PmWire;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Вопрос-пароль конверта (5.7) — таймер уже вышел, но открытие требует ещё
 * и верного ответа (см. {@link PmWire#checkEnvelopeAnswer}).
 */
@Environment(EnvType.CLIENT)
public class PmEnvelopeUnlockScreen extends Screen {

    private final Screen parent;
    private final PmMessage msg;
    private final String question;
    private final String answerHash;
    private final PmConfig config = PmChatClient.getConfig();

    private int PANEL_W = 220;
    private int PANEL_H = 100;
    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private EditBox answerField;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmEnvelopeUnlockScreen(Screen parent, PmMessage msg, String question, String answerHash) {
        super(Component.translatable("pmchat.envelope.unlock.title"));
        this.parent = parent;
        this.msg = msg;
        this.question = question;
        this.answerHash = answerHash;
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
        PANEL_W = Math.max(180, Math.min(220, width - 24));
        PANEL_H = Math.min(100, height - 16);
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int fx = px + 14;
        int fw = PANEL_W - 28;
        int y = py + 40;

        answerField = new EditBox(font, fx, y, fw, 16, Component.translatable("pmchat.envelope.answer"));
        answerField.setMaxLength(60);
        addRenderableWidget(answerField);
        y += 22;

        addRenderableWidget(FlatButton.centered(font, fx, y, (fw - 6) / 2, 18,
                Component.translatable("pmchat.envelope.unlock.open"), 0xFF244A33, 0xFF2E5C40, 0xFF4C8A66, 0xFFCFEEDA,
                btn -> tryOpen()));
        addRenderableWidget(FlatButton.centered(font, fx + (fw - 6) / 2 + 6, y, (fw - 6) / 2, 18,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> onClose()));
    }

    private void tryOpen() {
        String answer = answerField.getValue();
        if (PmWire.checkEnvelopeAnswer(answer, answerHash)) {
            msg.envelopeOpened = true;
            PmChatClient.getHistory().save();
            onClose();
        } else {
            status = Component.translatable("pmchat.envelope.unlock.wrong");
            statusColor = 0xFFE07A6A;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, BG);
        context.outline(px, py, PANEL_W, PANEL_H, BORDER);

        context.text(font, getTitle(), px + (PANEL_W - font.width(getTitle())) / 2, py + 8, TITLE, false);
        context.text(font, trim(question, PANEL_W - 24), px + 14, py + 24, LABEL, false);

        if (!status.getString().isEmpty()) {
            context.text(font, status, px + (PANEL_W - font.width(status)) / 2, py + PANEL_H - 12, statusColor, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private String trim(String s, int maxW) {
        if (s == null) return "";
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
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
