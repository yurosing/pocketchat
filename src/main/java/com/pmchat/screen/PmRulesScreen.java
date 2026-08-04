package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Правила мода — показывается перед первым открытием мессенджера, пока
 * принятая версия правил ({@link PmConfig#rulesAcceptedVersion}) отстаёт от
 * актуальной. Без принятия окно мессенджера не откроется: см.
 * {@link PmChatClient#openMessenger}, единственную точку входа, которой
 * положено пользоваться вместо {@code new PmScreen()} напрямую.
 *
 * <p>Текст берётся с бэкенда ({@link PmBackend#cachedRules}) — админ может
 * менять его без релиза мода. Пока бэкенд не настроен или ответ ещё не
 * пришёл, показывается встроенный запасной текст (тот же, что раньше был
 * зашит намертво) — экран обязан работать даже совсем без сети.
 */
@Environment(EnvType.CLIENT)
public class PmRulesScreen extends Screen {

    /**
     * Не static final — подгоняется под размер экрана в init() (GUI Scale 4 и
     * т.п.). Этот экран — обязательный шаг перед первым открытием мессенджера,
     * так что он ОБЯЗАН оставаться пригодным для использования на любом экране.
     */
    private int PANEL_W = 260;

    private final Runnable onAccept;
    private final PmConfig config = PmChatClient.getConfig();
    private final int acceptVersion;

    private String eula, freedom, header, footer;
    private List<String> rules;

    private int px, py, panelH;
    private int BG, BORDER, LABEL, TITLE, SUBTLE, BTN_BG, BTN_HOVER, BTN_BORDER;

    /** Рамки-блоки: {y, h} — рисуются под текстом внутри них. */
    private int eulaBoxY, eulaBoxH;
    private int freedomBoxY, freedomBoxH;
    private int rulesBoxY, rulesBoxH;

    public PmRulesScreen(Runnable onAccept) {
        super(Text.translatable("pmchat.rules.title"));
        this.onAccept = onAccept;

        PmBackend.RulesContent fetched = PmBackend.cachedRules();
        PmBackend.RuleLocale active = fetched != null ? fetched.active() : null;
        if (active != null && !active.rules.isEmpty()) {
            eula = active.eula;
            freedom = active.freedom;
            header = active.header;
            rules = active.rules;
            footer = active.footer;
            acceptVersion = fetched.version;
        } else {
            eula = Text.translatable("pmchat.rules.eula").getString();
            freedom = Text.translatable("pmchat.rules.freedom").getString();
            header = Text.translatable("pmchat.rules.header").getString();
            rules = List.of(
                    Text.translatable("pmchat.rules.rule1").getString(),
                    Text.translatable("pmchat.rules.rule2").getString(),
                    Text.translatable("pmchat.rules.rule3").getString());
            footer = Text.translatable("pmchat.rules.footer").getString();
            acceptVersion = 1;
        }
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        SUBTLE = t.value;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder;
    }

    @Override
    protected void init() {
        applyTheme();
        clearChildren();

        PANEL_W = Math.max(180, Math.min(260, width - 24));
        int textW = PANEL_W - 32;
        int y = 40;

        eulaBoxY = y;
        y += lineCount(eula, textW) * 10 + 10;
        eulaBoxH = y - eulaBoxY;
        y += 8;

        freedomBoxY = y;
        y += lineCount(freedom, textW) * 10 + 10;
        freedomBoxH = y - freedomBoxY;
        y += 8;

        rulesBoxY = y;
        y += 12; // заголовок «но есть базовые правила»
        for (String rule : rules) y += lineCount(rule, textW - 18) * 10;
        y += 6;
        rulesBoxH = y - rulesBoxY;
        y += 10;

        y += lineCount(footer, textW) * 10;
        y += 26;

        // Кнопки «Принимаю»/«Не сейчас» обязаны остаться на экране даже если
        // содержимое не влезло по высоте (иначе мод нечем принять) — жмём
        // panelH к height и держим py неотрицательным, а не наоборот.
        panelH = Math.min(y, height - 8);
        px = (width - PANEL_W) / 2;
        py = Math.max(4, (height - panelH) / 2);

        addDrawableChild(FlatButton.centered(textRenderer, px + 12, py + panelH - 22, (PANEL_W - 32) / 2, 16,
                Text.translatable("pmchat.rules.decline"), BTN_BG, BTN_HOVER, BTN_BORDER, SUBTLE,
                btn -> close()));
        addDrawableChild(FlatButton.centered(textRenderer, px + 20 + (PANEL_W - 32) / 2, py + panelH - 22, (PANEL_W - 32) / 2, 16,
                Text.translatable("pmchat.rules.accept"), 0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA,
                btn -> accept()));
    }

    private int lineCount(String text, int maxW) {
        return Math.max(1, textRenderer.wrapLines(Text.literal(text), maxW).size());
    }

    private void accept() {
        config.rulesAccepted = true;
        config.rulesAcceptedVersion = acceptVersion;
        config.save();
        onAccept.run();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, panelH, BORDER);

        Text title = getTitle();
        context.drawText(textRenderer, title, px + (PANEL_W - textRenderer.getWidth(title)) / 2, py + 10, TITLE, false);
        context.fill(px + 12, py + 24, px + PANEL_W - 12, py + 25, BORDER);

        int textW = PANEL_W - 32;
        int tx = px + 16;

        drawBox(context, eulaBoxY, eulaBoxH);
        drawWrapped(context, eula, tx + 4, py + eulaBoxY + 5, textW - 8, LABEL);

        drawBox(context, freedomBoxY, freedomBoxH);
        drawWrapped(context, freedom, tx + 4, py + freedomBoxY + 5, textW - 8, LABEL);

        drawBox(context, rulesBoxY, rulesBoxH);
        int ry = py + rulesBoxY + 5;
        context.drawText(textRenderer, header, tx + 4, ry, SUBTLE, false);
        ry += 12;
        for (String rule : rules) {
            ry = drawBullet(context, rule, tx + 4, ry, textW - 18);
        }

        int fy = py + rulesBoxY + rulesBoxH + 10;
        drawWrapped(context, footer, tx, fy, textW, SUBTLE);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawBox(DrawContext context, int boxY, int boxH) {
        context.drawStrokedRectangle(px + 10, py + boxY, PANEL_W - 20, boxH, BORDER);
    }

    private void drawWrapped(DrawContext context, String text, int x, int y, int maxW, int color) {
        for (var line : textRenderer.wrapLines(Text.literal(text), maxW)) {
            context.drawText(textRenderer, line, x, y, color, false);
            y += 10;
        }
    }

    private int drawBullet(DrawContext context, String text, int x, int y, int maxW) {
        context.drawText(textRenderer, "•", x, y, 0xFFE07A6A, false);
        for (var line : textRenderer.wrapLines(Text.literal(text), maxW)) {
            context.drawText(textRenderer, line, x + 10, y, LABEL, false);
            y += 10;
        }
        return y;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
