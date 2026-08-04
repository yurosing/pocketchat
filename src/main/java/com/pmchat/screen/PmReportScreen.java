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
 * Жалоба на игрока (кнопка «Пожаловаться» в чужом профиле) — короткая причина
 * уходит на бэкенд ({@code POST /v1/report}), видна только админу.
 */
@Environment(EnvType.CLIENT)
public class PmReportScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 110;

    private final Screen parent;
    private final String target;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private TextFieldWidget reasonField;
    private Text status = Text.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmReportScreen(Screen parent, String target) {
        super(Text.translatable("pmchat.report.title", target));
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
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int fx = px + 16;
        int fw = PANEL_W - 32;
        int y = py + 26;

        reasonField = new TextFieldWidget(textRenderer, fx, y, fw, 16, Text.translatable("pmchat.report.hint"));
        reasonField.setMaxLength(300);
        String hint = Text.translatable("pmchat.report.hint").getString();
        reasonField.setSuggestion(hint);
        reasonField.setChangedListener(s -> reasonField.setSuggestion(s.isEmpty() ? hint : null));
        addDrawableChild(reasonField);
        y += 24;

        addDrawableChild(FlatButton.centered(textRenderer, fx, y, fw, 16,
                Text.translatable("pmchat.report.send"), 0xFF5A2A22, 0xFF6E332A, 0xFFA0463A, 0xFFE07A6A,
                btn -> send()));

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    private void send() {
        String reason = reasonField.getText().trim();
        if (reason.isEmpty()) return;
        PmBackend.report(target, reason, (ok, v, err) -> {
            if (ok) {
                status = Text.translatable("pmchat.report.ok");
                statusColor = 0xFF8FD8A8;
                reasonField.setText("");
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
