package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
    private final Screen returnTo;
    private final boolean viewOnly;
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
        super(Component.translatable("pmchat.rules.title"));
        this.onAccept = onAccept;
        this.returnTo = null;
        this.viewOnly = false;
        acceptVersion = loadContent();
    }

    /** Просмотр правил из настроек — без принятия/отклонения, просто «Закрыть». */
    public PmRulesScreen(Screen returnTo) {
        super(Component.translatable("pmchat.rules.title"));
        this.onAccept = null;
        this.returnTo = returnTo;
        this.viewOnly = true;
        acceptVersion = loadContent();
    }

    private int loadContent() {
        PmBackend.RulesContent fetched = PmBackend.cachedRules();
        PmBackend.RuleLocale active = fetched != null ? fetched.active() : null;
        if (active != null && !active.rules.isEmpty()) {
            eula = active.eula;
            freedom = active.freedom;
            header = active.header;
            rules = active.rules;
            footer = active.footer;
            return fetched.version;
        } else {
            eula = Component.translatable("pmchat.rules.eula").getString();
            freedom = Component.translatable("pmchat.rules.freedom").getString();
            header = Component.translatable("pmchat.rules.header").getString();
            rules = List.of(
                    Component.translatable("pmchat.rules.rule1").getString(),
                    Component.translatable("pmchat.rules.rule2").getString(),
                    Component.translatable("pmchat.rules.rule3").getString());
            footer = Component.translatable("pmchat.rules.footer").getString();
            return 1;
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
        clearWidgets();

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

        if (viewOnly) {
            addRenderableWidget(FlatButton.centered(font, px + (PANEL_W - 80) / 2, py + panelH - 22, 80, 16,
                    Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, LABEL,
                    btn -> onClose()));
        } else {
            addRenderableWidget(FlatButton.centered(font, px + 12, py + panelH - 22, (PANEL_W - 32) / 2, 16,
                    Component.translatable("pmchat.rules.decline"), BTN_BG, BTN_HOVER, BTN_BORDER, SUBTLE,
                    btn -> onClose()));
            addRenderableWidget(FlatButton.centered(font, px + 20 + (PANEL_W - 32) / 2, py + panelH - 22, (PANEL_W - 32) / 2, 16,
                    Component.translatable("pmchat.rules.accept"), 0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA,
                    btn -> accept()));
        }
    }

    private int lineCount(String text, int maxW) {
        return Math.max(1, font.split(Component.literal(text), maxW).size());
    }

    private void accept() {
        config.rulesAccepted = true;
        config.rulesAcceptedVersion = acceptVersion;
        config.save();
        onAccept.run();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.outline(px, py, PANEL_W, panelH, BORDER);

        Component title = getTitle();
        context.text(font, title, px + (PANEL_W - font.width(title)) / 2, py + 10, TITLE, false);
        context.fill(px + 12, py + 24, px + PANEL_W - 12, py + 25, BORDER);

        int textW = PANEL_W - 32;
        int tx = px + 16;

        drawBox(context, eulaBoxY, eulaBoxH);
        drawWrapped(context, eula, tx + 4, py + eulaBoxY + 5, textW - 8, LABEL);

        drawBox(context, freedomBoxY, freedomBoxH);
        drawWrapped(context, freedom, tx + 4, py + freedomBoxY + 5, textW - 8, LABEL);

        drawBox(context, rulesBoxY, rulesBoxH);
        int ry = py + rulesBoxY + 5;
        context.text(font, header, tx + 4, ry, SUBTLE, false);
        ry += 12;
        for (String rule : rules) {
            ry = drawBullet(context, rule, tx + 4, ry, textW - 18);
        }

        int fy = py + rulesBoxY + rulesBoxH + 10;
        drawWrapped(context, footer, tx, fy, textW, SUBTLE);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawBox(GuiGraphicsExtractor context, int boxY, int boxH) {
        context.outline(px + 10, py + boxY, PANEL_W - 20, boxH, BORDER);
    }

    private void drawWrapped(GuiGraphicsExtractor context, String text, int x, int y, int maxW, int color) {
        for (var line : font.split(Component.literal(text), maxW)) {
            context.text(font, line, x, y, color, false);
            y += 10;
        }
    }

    private int drawBullet(GuiGraphicsExtractor context, String text, int x, int y, int maxW) {
        context.text(font, "•", x, y, 0xFFE07A6A, false);
        for (var line : font.split(Component.literal(text), maxW)) {
            context.text(font, line, x + 10, y, LABEL, false);
            y += 10;
        }
        return y;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(viewOnly ? returnTo : null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
