package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmWire;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Составление сообщения-конверта (5.7): скин + обязательный таймер + опциональный
 * вопрос-пароль. Открытие описано в {@link PmWire#parseEnvelope} / рендерится
 * в {@link PmScreen} как обычная реплика с особым содержимым.
 */
@Environment(EnvType.CLIENT)
public class PmEnvelopeComposeScreen extends Screen {

    /** Пресеты таймера в минутах — как в спец. envelope-приложениях: от «уже почти» до «через сутки». */
    private static final int[] PRESETS_MIN = {1, 15, 60, 1440};

    private final Screen parent;
    private final String target;
    private final PmConfig config = PmChatClient.getConfig();

    private int PANEL_W = 260;
    private int PANEL_H = 210;
    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private int skinIndex = 0;
    private boolean withQuestion = false;
    private TextFieldWidget contentField;
    private TextFieldWidget minutesField;
    private TextFieldWidget questionField;
    private TextFieldWidget answerField;
    private Text status = Text.empty();
    private int statusColor = 0xFFAAAAAA;

    // Значения полей переживают переинициализацию (toggle «вопрос-пароль» вызывает
    // init() заново и пересоздаёт все TextFieldWidget) — как composeText в PmProfilePostsScreen.
    private String contentText = "";
    private String minutesText = "15";
    private String questionText = "";
    private String answerText = "";

    public PmEnvelopeComposeScreen(Screen parent, String target) {
        super(Text.translatable("pmchat.envelope.compose"));
        this.parent = parent;
        this.target = target;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    private final java.util.List<Object[]> fieldLabels = new java.util.ArrayList<>();
    private int[] skinRect;

    @Override
    protected void init() {
        applyTheme();
        // Снимок значений полей ДО их пересборки (toggle «вопрос» вызывает init() заново).
        if (contentField != null) contentText = contentField.getText();
        if (minutesField != null) minutesText = minutesField.getText();
        if (questionField != null) questionText = questionField.getText();
        if (answerField != null) answerText = answerField.getText();
        clearChildren();
        fieldLabels.clear();
        PANEL_W = Math.max(200, Math.min(260, width - 24));
        PANEL_H = Math.min(withQuestion ? 250 : 210, height - 16);
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int fx = px + 14;
        int fw = PANEL_W - 28;
        int y = py + 24;

        // Скин — иконка + название, клик циклит по PmWire.ENVELOPE_SKINS
        skinRect = new int[]{fx, y, fw, 16};
        y += 22;

        fieldLabels.add(new Object[]{"pmchat.envelope.content", fx, y - 10});
        contentField = new TextFieldWidget(textRenderer, fx, y, fw, 16, Text.translatable("pmchat.envelope.content"));
        contentField.setMaxLength(300);
        contentField.setText(contentText);
        addDrawableChild(contentField);
        y += 26;

        fieldLabels.add(new Object[]{"pmchat.envelope.minutes", fx, y - 10});
        minutesField = new TextFieldWidget(textRenderer, fx, y, 60, 16, Text.translatable("pmchat.envelope.minutes"));
        minutesField.setMaxLength(6);
        minutesField.setText(minutesText);
        addDrawableChild(minutesField);

        // Ширина пресетов — по числу кнопок; FlatButton текст не обрезает сам (см. render()
        // без scissor), поэтому подписи вроде «15м» на совсем узкой кнопке лезли бы за её
        // границы и наплывали на соседние. trimToButton() режет подпись под реальную ширину.
        int pw = (fw - 64 - 6) / PRESETS_MIN.length;
        int px2 = fx + 64 + 6;
        for (int m : PRESETS_MIN) {
            String label = trimToButton(presetLabel(m), pw - 4);
            addDrawableChild(FlatButton.centered(textRenderer, px2, y, pw - 2, 16,
                    Text.literal(label), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                    btn -> minutesField.setText(String.valueOf(m))));
            px2 += pw;
        }
        y += 24;

        addDrawableChild(FlatButton.centered(textRenderer, fx, y, fw, 16,
                Text.translatable(withQuestion ? "pmchat.envelope.question.remove" : "pmchat.envelope.question.add"),
                BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> {
                    withQuestion = !withQuestion;
                    init();
                }));
        y += 22;

        if (withQuestion) {
            fieldLabels.add(new Object[]{"pmchat.envelope.question", fx, y - 10});
            questionField = new TextFieldWidget(textRenderer, fx, y, fw, 16, Text.translatable("pmchat.envelope.question"));
            questionField.setMaxLength(120);
            questionField.setText(questionText);
            addDrawableChild(questionField);
            y += 26;

            fieldLabels.add(new Object[]{"pmchat.envelope.answer", fx, y - 10});
            answerField = new TextFieldWidget(textRenderer, fx, y, fw, 16, Text.translatable("pmchat.envelope.answer"));
            answerField.setMaxLength(60);
            answerField.setText(answerText);
            addDrawableChild(answerField);
            y += 26;
        } else {
            questionField = null;
            answerField = null;
        }

        addDrawableChild(FlatButton.centered(textRenderer, fx, y, (fw - 6) / 2, 18,
                Text.translatable("pmchat.envelope.send"), 0xFF244A33, 0xFF2E5C40, 0xFF4C8A66, 0xFFCFEEDA,
                btn -> send()));
        addDrawableChild(FlatButton.centered(textRenderer, fx + (fw - 6) / 2 + 6, y, (fw - 6) / 2, 18,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    private String trimToButton(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private String presetLabel(int minutes) {
        if (minutes < 60) return minutes + "м";
        if (minutes < 1440) return (minutes / 60) + "ч";
        return (minutes / 1440) + "д";
    }

    private void send() {
        String skin = PmWire.ENVELOPE_SKINS[skinIndex];
        String content = contentField.getText().trim();
        if (content.isEmpty()) {
            setStatus(Text.translatable("pmchat.envelope.needcontent"), 0xFFE07A6A);
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(minutesField.getText().trim());
        } catch (NumberFormatException e) {
            minutes = -1;
        }
        if (minutes <= 0) {
            setStatus(Text.translatable("pmchat.envelope.needtimer"), 0xFFE07A6A);
            return;
        }
        String question = withQuestion && questionField != null ? questionField.getText().trim() : "";
        String answer = withQuestion && answerField != null ? answerField.getText().trim() : "";
        if (withQuestion && (question.isEmpty() || answer.isEmpty())) {
            setStatus(Text.translatable("pmchat.envelope.needqa"), 0xFFE07A6A);
            return;
        }
        long unlockAt = System.currentTimeMillis() / 1000L + minutes * 60L;
        String wire = PmWire.envelope(skin, unlockAt, withQuestion ? question : null, withQuestion ? answer : null, content);
        PmChatClient.sendMessage(target, wire);
        close();
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

        for (Object[] entry : fieldLabels) {
            context.drawText(textRenderer, Text.translatable((String) entry[0]), (int) entry[1], (int) entry[2], LABEL, false);
        }

        if (skinRect != null) {
            String skin = PmWire.ENVELOPE_SKINS[skinIndex];
            boolean hov = inRect(mouseX, mouseY, skinRect);
            context.fill(skinRect[0], skinRect[1], skinRect[0] + skinRect[2], skinRect[1] + skinRect[3],
                    hov ? BTN_HOVER : BTN_BG);
            context.drawStrokedRectangle(skinRect[0], skinRect[1], skinRect[2], skinRect[3], BTN_BORDER);
            String label = PmWire.envelopeIcon(skin) + "  " + Text.translatable(PmWire.envelopeLabelKey(skin)).getString()
                    + "  (" + Text.translatable("pmchat.envelope.skin.next").getString() + ")";
            label = trimToButton(label, skinRect[2] - 6);
            context.drawText(textRenderer, label,
                    skinRect[0] + (skinRect[2] - textRenderer.getWidth(label)) / 2, skinRect[1] + 4,
                    PmWire.envelopeColor(skin), false);
        }

        if (!status.getString().isEmpty()) {
            context.drawText(textRenderer, status, px + (PANEL_W - textRenderer.getWidth(status)) / 2, py + PANEL_H - 12, statusColor, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private boolean inRect(int mx, int my, int[] r) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (inRect(mx, my, skinRect)) {
            skinIndex = (skinIndex + 1) % PmWire.ENVELOPE_SKINS.length;
            return true;
        }
        return super.mouseClicked(click, doubled);
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
