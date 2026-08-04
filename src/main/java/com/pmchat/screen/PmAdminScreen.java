package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.List;

/**
 * Админ-панель PocketChat — полноэкранная, в киберпанк-стиле (всегда красная,
 * независимо от темы мессенджера). Вкладки: сводка по бэкенду, рассылка +
 * личное сообщение от официального аккаунта, управление игроком (валюта/
 * галочка/официальный), жалобы и обращения в поддержку. Работает только если
 * сервер узнаёт в токене аккаунт {@code ADMIN_USERNAME} и совпадает
 * {@link PmConfig#backendAdminSecret} — без этого все вызовы вернут 403.
 */
@Environment(EnvType.CLIENT)
public class PmAdminScreen extends Screen {

    private static final int HEADER_H = 30;
    private static final int TAB_H = 22;
    private static final int ROW_H = 22;

    // Палитра киберпанк/красная — фиксированная, не зависит от темы мессенджера.
    private static final int BG = 0xFF0A0304;
    private static final int PANEL = 0xFF170708;
    private static final int PANEL_LIGHT = 0xFF230A0C;
    private static final int NEON = 0xFFFF2A44;
    private static final int NEON_DIM = 0xFF7A1420;
    private static final int TITLE = 0xFFFF4D63;
    private static final int TEXT_MAIN = 0xFFE9C7CB;
    private static final int SUBTLE = 0xFF8A5A60;
    private static final int BTN_BG = 0xFF200A0C;
    private static final int BTN_HOVER = 0xFF3A1418;
    private static final int OK = 0xFF4CC26A;
    private static final int BAD = 0xFFFF4D63;
    private static final int WARN = 0xFFF0C34E;

    private static final String[] TAB_KEYS = {
            "pmchat.admin.tab.dashboard",
            "pmchat.admin.tab.broadcast",
            "pmchat.admin.tab.players",
            "pmchat.admin.tab.reports",
            "pmchat.admin.tab.support",
            "pmchat.admin.tab.rules",
            "pmchat.admin.tab.shop",
    };

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();
    private static int lastTab = 0;
    private int tab = lastTab;

    private Text status = Text.empty();
    private int statusColor = SUBTLE;

    // ---- Вкладка 0: сводка ----
    private PmBackend.AdminStatus dashStatus;
    private boolean dashError;
    private long lastDashLoadAt = 0;

    private TextFieldWidget featureMinutesField;

    // ---- Вкладка 1: рассылка ----
    private TextFieldWidget broadcastField;
    private TextFieldWidget dmTargetField;
    private TextFieldWidget dmMessageField;

    // ---- Вкладка 2: игрок ----
    private TextFieldWidget targetField;
    private TextFieldWidget amountField;
    private TextFieldWidget muteMinutesField;

    // ---- Вкладка 3/4: жалобы и поддержка ----
    private List<PmBackend.ReportEntry> reports = Collections.emptyList();
    private List<PmBackend.SupportEntry> tickets = Collections.emptyList();
    private int listScroll = 0;
    private int listTop, listBottom;

    // ---- Вкладка 5: правила (правится только RU — EN пересылается как есть) ----
    private TextFieldWidget ruleEulaField, ruleFreedomField, ruleFooterField;
    private TextFieldWidget[] ruleLineFields;
    private PmBackend.RuleLocale rulesEnCurrent;
    private String rulesHeaderCurrent = "";
    private static final int RULE_LINES = 5;

    // ---- Вкладка 6: магазин возможностей ----
    private List<PmBackend.ShopItem> shopItems = Collections.emptyList();
    private int shopScroll = 0;
    private int shopListTop, shopListBottom;
    private long shopEditingId = 0;
    private TextFieldWidget shopNameField, shopDescField, shopFeatureKeyField, shopPriceField, shopDurationField;
    private final java.util.List<Object[]> shopRowRects = new java.util.ArrayList<>();
    private final java.util.List<Object[]> shopDeleteRects = new java.util.ArrayList<>();

    public PmAdminScreen(Screen parent) {
        super(Text.translatable("pmchat.admin.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        lastTab = tab;
        status = Text.empty();

        int tabW = width / TAB_KEYS.length;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int ti = i;
            addDrawableChild(FlatButton.centered(textRenderer, ti * tabW, HEADER_H, tabW, TAB_H,
                    Text.translatable(TAB_KEYS[i]),
                    ti == tab ? PANEL_LIGHT : PANEL, BTN_HOVER, NEON_DIM, ti == tab ? TITLE : TEXT_MAIN,
                    btn -> {
                        tab = ti;
                        init();
                    }));
        }

        int contentTop = HEADER_H + TAB_H + 10;
        switch (tab) {
            case 0 -> buildDashboard(contentTop);
            case 1 -> buildBroadcast(contentTop);
            case 2 -> buildPlayers(contentTop);
            case 3 -> buildList(contentTop, true);
            case 4 -> buildList(contentTop, false);
            case 5 -> buildRules(contentTop);
            case 6 -> buildShop(contentTop);
            default -> { }
        }

        addDrawableChild(FlatButton.centered(textRenderer, width - 90, height - 24, 80, 18,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN, btn -> close()));
    }

    // ---------- вкладка 0: сводка по бэкенду ----------

    private static final String[] FEATURES = {"gifts", "reports", "support"};

    private void buildDashboard(int y) {
        loadDashboard();

        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int fx = cx - cardW / 2 + 12;
        int fw = cardW - 24;
        int fy = height - 118;

        featureMinutesField = new TextFieldWidget(textRenderer, fx, fy, fw, 16, Text.translatable("pmchat.admin.feature.minutes"));
        featureMinutesField.setMaxLength(6);
        placeholder(featureMinutesField, "pmchat.admin.feature.minutes");
        addDrawableChild(featureMinutesField);
        fy += 20;

        int colW = (fw - 8) / 3;
        for (int i = 0; i < FEATURES.length; i++) {
            String name = FEATURES[i];
            String humanName = Text.translatable("pmchat.admin.feature." + name).getString();
            int col = fx + i * (colW + 4);
            addDrawableChild(FlatButton.centered(textRenderer, col, fy, colW, 16,
                    Text.translatable("pmchat.admin.feature.on", humanName), BTN_BG, BTN_HOVER, OK, OK,
                    btn -> toggleFeature(name, false)));
            addDrawableChild(FlatButton.centered(textRenderer, col, fy + 20, colW, 16,
                    Text.translatable("pmchat.admin.feature.off", humanName), BTN_BG, BTN_HOVER, BAD, BAD,
                    btn -> toggleFeature(name, true)));
        }
    }

    private void toggleFeature(String name, boolean disable) {
        int minutes = 0;
        try {
            minutes = Integer.parseInt(featureMinutesField.getText().trim());
        } catch (NumberFormatException ignored) {
        }
        PmBackend.adminSetFeature(name, !disable, minutes, (ok, v, err) ->
                setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void loadDashboard() {
        lastDashLoadAt = System.currentTimeMillis();
        PmBackend.adminStatus((ok, st, err) -> {
            dashError = !ok;
            dashStatus = ok ? st : null;
        });
    }

    private void drawDashboard(DrawContext context, int mouseX, int mouseY) {
        int top = HEADER_H + TAB_H + 14;
        int cx = width / 2;

        if (System.currentTimeMillis() - lastDashLoadAt > 5000) loadDashboard();

        boolean online = dashStatus != null && !dashError;
        int pillW = 160;
        drawPanel(context, cx - pillW / 2, top, pillW, 20);
        String pillText = online ? "● ONLINE" : dashStatus == null ? "…" : "● OFFLINE";
        int pillColor = online ? OK : dashStatus == null ? SUBTLE : BAD;
        context.drawText(textRenderer, pillText,
                cx - textRenderer.getWidth(pillText) / 2, top + 6, pillColor, false);

        int tilesY = top + 32;
        int tileW = Math.min(150, (width - 80) / 4);
        int gap = 12;
        int totalW = tileW * 4 + gap * 3;
        int tx = cx - totalW / 2;
        long[] values = {
                dashStatus != null ? dashStatus.accounts : 0,
                dashStatus != null ? dashStatus.onlineNow : 0,
                dashStatus != null ? dashStatus.openReports : 0,
                dashStatus != null ? dashStatus.openTickets : 0,
        };
        String[] labels = {
                Text.translatable("pmchat.admin.dash.accounts").getString(),
                Text.translatable("pmchat.admin.dash.online").getString(),
                Text.translatable("pmchat.admin.dash.reports").getString(),
                Text.translatable("pmchat.admin.dash.tickets").getString(),
        };
        int[] accent = {NEON, OK, WARN, WARN};
        for (int i = 0; i < 4; i++) {
            int x = tx + i * (tileW + gap);
            drawPanel(context, x, tilesY, tileW, 56);
            context.fill(x, tilesY, x + tileW, tilesY + 2, accent[i]);
            String num = dashStatus != null ? String.valueOf(values[i]) : "—";
            context.drawText(textRenderer, num, x + (tileW - textRenderer.getWidth(num)) / 2, tilesY + 14, TEXT_MAIN, false);
            context.drawText(textRenderer, labels[i], x + (tileW - textRenderer.getWidth(labels[i])) / 2, tilesY + 38, SUBTLE, false);
        }

        if (dashStatus != null) {
            String uptime = Text.translatable("pmchat.admin.dash.uptime", formatUptime(dashStatus.uptimeSec)).getString();
            context.drawText(textRenderer, uptime, cx - textRenderer.getWidth(uptime) / 2, tilesY + 70, SUBTLE, false);
        } else if (dashError) {
            Text err = Text.translatable("pmchat.admin.dash.fail");
            context.drawText(textRenderer, err, cx - textRenderer.getWidth(err) / 2, tilesY + 70, BAD, false);
        }

        // Заголовок и состояние блока переключателей фич (кнопки уже добавлены в buildDashboard)
        int cardW = Math.min(420, width - 40);
        int fx = cx - cardW / 2 + 12;
        Text featTitle = Text.translatable("pmchat.admin.feature.title");
        context.drawText(textRenderer, featTitle, fx, height - 140, SUBTLE, false);
        int colW = (cardW - 24 - 8) / 3;
        for (int i = 0; i < FEATURES.length; i++) {
            boolean enabled = PmBackend.isFeatureEnabled(FEATURES[i]);
            int col = fx + i * (colW + 4);
            String state = enabled ? "●" : "○";
            context.drawText(textRenderer, state, col, height - 100, enabled ? OK : BAD, false);
        }

        if (!status.getString().isEmpty()) {
            context.drawText(textRenderer, status, width / 2 - textRenderer.getWidth(status) / 2, height - 46, statusColor, false);
        }
    }

    private static String formatUptime(long sec) {
        long d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    // ---------- вкладка 1: рассылка + личное сообщение ----------

    private void buildBroadcast(int y) {
        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int fx = cx - cardW / 2 + 12;
        int fw = cardW - 24;

        broadcastField = new TextFieldWidget(textRenderer, fx, y + 24, fw, 16, Text.translatable("pmchat.admin.broadcast.hint"));
        broadcastField.setMaxLength(500);
        placeholder(broadcastField, "pmchat.admin.broadcast.hint");
        addDrawableChild(broadcastField);
        addDrawableChild(FlatButton.centered(textRenderer, fx, y + 46, fw, 16,
                Text.translatable("pmchat.admin.broadcast.send"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doBroadcast()));

        int dmY = y + 84;
        int dmTargetW = fw - 22;
        dmTargetField = new TextFieldWidget(textRenderer, fx, dmY, dmTargetW, 16, Text.translatable("pmchat.admin.target.hint"));
        dmTargetField.setMaxLength(32);
        placeholder(dmTargetField, "pmchat.admin.target.hint");
        addDrawableChild(dmTargetField);
        FlatButton browseBtn = FlatButton.centered(textRenderer, fx + dmTargetW + 4, dmY, 18, 16,
                Text.literal("☰"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> MinecraftClient.getInstance().setScreen(
                        new PmAdminAccountsScreen(this, name -> dmTargetField.setText(name))));
        addDrawableChild(browseBtn);

        dmMessageField = new TextFieldWidget(textRenderer, fx, dmY + 22, fw, 16, Text.translatable("pmchat.admin.dm.hint"));
        dmMessageField.setMaxLength(500);
        placeholder(dmMessageField, "pmchat.admin.dm.hint");
        addDrawableChild(dmMessageField);
        addDrawableChild(FlatButton.centered(textRenderer, fx, dmY + 44, fw, 16,
                Text.translatable("pmchat.admin.dm.send"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doDirectMessage()));
    }

    private void doBroadcast() {
        String msg = broadcastField.getText().trim();
        if (msg.isEmpty()) return;
        PmBackend.adminBroadcast(msg, (ok, v, err) -> {
            if (ok) {
                broadcastField.setText("");
                setStatus(Text.translatable("pmchat.admin.ok"), OK);
            } else {
                setStatus(Text.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
            }
        });
    }

    private void doDirectMessage() {
        String target = dmTargetField.getText().trim();
        String msg = dmMessageField.getText().trim();
        if (target.isEmpty() || msg.isEmpty()) return;
        PmBackend.adminMessage(target, msg, (ok, v, err) -> {
            if (ok) {
                dmMessageField.setText("");
                setStatus(Text.translatable("pmchat.admin.ok"), OK);
            } else {
                setStatus(Text.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
            }
        });
    }

    // ---------- вкладка 2: управление игроком ----------

    private void buildPlayers(int y) {
        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int fx = cx - cardW / 2 + 12;
        int fw = cardW - 24;

        int targetFieldW = fw - 22;
        targetField = new TextFieldWidget(textRenderer, fx, y + 20, targetFieldW, 16, Text.translatable("pmchat.admin.target.hint"));
        targetField.setMaxLength(32);
        placeholder(targetField, "pmchat.admin.target.hint");
        addDrawableChild(targetField);
        addDrawableChild(FlatButton.centered(textRenderer, fx + targetFieldW + 4, y + 20, 18, 16,
                Text.literal("☰"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> MinecraftClient.getInstance().setScreen(
                        new PmAdminAccountsScreen(this, name -> targetField.setText(name)))));

        int ay = y + 44;
        amountField = new TextFieldWidget(textRenderer, fx, ay, (fw - 6) / 2, 16, Text.translatable("pmchat.admin.amount.hint"));
        amountField.setMaxLength(10);
        placeholder(amountField, "pmchat.admin.amount.hint");
        addDrawableChild(amountField);
        addDrawableChild(FlatButton.centered(textRenderer, fx + (fw - 6) / 2 + 6, ay, (fw - 6) / 2, 16,
                Text.translatable("pmchat.admin.grant"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN, btn -> doGrant()));

        int vy = ay + 24;
        addDrawableChild(FlatButton.centered(textRenderer, fx, vy, (fw - 6) / 2, 16,
                Text.translatable("pmchat.admin.verify.on"), BTN_BG, BTN_HOVER, OK, OK, btn -> doVerify(true)));
        addDrawableChild(FlatButton.centered(textRenderer, fx + (fw - 6) / 2 + 6, vy, (fw - 6) / 2, 16,
                Text.translatable("pmchat.admin.verify.off"), BTN_BG, BTN_HOVER, BAD, BAD, btn -> doVerify(false)));

        addDrawableChild(FlatButton.centered(textRenderer, fx, vy + 24, fw, 16,
                Text.translatable("pmchat.admin.official"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN, btn -> doOfficial()));

        // Модерация: временный мут (в минутах) и постоянный бан — единственный способ
        // заблокировать отправку ЛС/голосовых/фото у игрока (клиент сам проверяет
        // свой статус, бэкенд не видит /m напрямую, см. PmScreen.blockIfMuted).
        int my = vy + 48;
        muteMinutesField = new TextFieldWidget(textRenderer, fx, my, (fw - 6) / 2, 16, Text.translatable("pmchat.admin.mute.minutes"));
        muteMinutesField.setMaxLength(6);
        placeholder(muteMinutesField, "pmchat.admin.mute.minutes");
        addDrawableChild(muteMinutesField);
        addDrawableChild(FlatButton.centered(textRenderer, fx + (fw - 6) / 2 + 6, my, (fw - 6) / 2, 16,
                Text.translatable("pmchat.admin.mute.apply"), BTN_BG, BTN_HOVER, WARN, WARN, btn -> doMute()));

        int by = my + 24;
        addDrawableChild(FlatButton.centered(textRenderer, fx, by, (fw - 6) / 2, 16,
                Text.translatable("pmchat.admin.ban.on"), BTN_BG, BTN_HOVER, BAD, BAD, btn -> doBan(true)));
        addDrawableChild(FlatButton.centered(textRenderer, fx + (fw - 6) / 2 + 6, by, (fw - 6) / 2, 16,
                Text.translatable("pmchat.admin.ban.off"), BTN_BG, BTN_HOVER, OK, OK, btn -> doBan(false)));
    }

    private void doMute() {
        String target = targetField.getText().trim();
        if (target.isEmpty()) return;
        int minutes;
        try {
            minutes = Integer.parseInt(muteMinutesField.getText().trim());
        } catch (NumberFormatException e) {
            setStatus(Text.translatable("pmchat.admin.badamount"), BAD);
            return;
        }
        PmBackend.adminMute(target, minutes, (ok, v, err) ->
                setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void doBan(boolean banned) {
        String target = targetField.getText().trim();
        if (target.isEmpty()) return;
        PmBackend.adminBan(target, banned, (ok, v, err) ->
                setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void doGrant() {
        String target = targetField.getText().trim();
        long amount;
        try {
            amount = Long.parseLong(amountField.getText().trim());
        } catch (NumberFormatException e) {
            setStatus(Text.translatable("pmchat.admin.badamount"), BAD);
            return;
        }
        if (target.isEmpty() || amount == 0) return;
        PmBackend.adminGrantCurrency(target, amount, (ok, v, err) ->
                setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void doVerify(boolean verified) {
        String target = targetField.getText().trim();
        if (target.isEmpty()) return;
        PmBackend.adminVerify(target, verified, (ok, v, err) ->
                setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void doOfficial() {
        String target = targetField.getText().trim();
        if (target.isEmpty()) return;
        PmBackend.adminSetOfficial(target, true, null, (ok, v, err) ->
                setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    // ---------- вкладка 5: правила мода (без релиза мода) ----------

    /**
     * Правится только русский текст — английский пересылается как есть (см.
     * {@link PmBackend#adminSetRules}, оба языка обязательны на бэкенде).
     * Пустые строки-правила пропускаются при сохранении.
     */
    private void buildRules(int y) {
        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int fx = cx - cardW / 2 + 12;
        int fw = cardW - 24;

        ruleEulaField = new TextFieldWidget(textRenderer, fx, y, fw, 16, Text.translatable("pmchat.admin.rules.eula"));
        ruleEulaField.setMaxLength(500);
        placeholder(ruleEulaField, "pmchat.admin.rules.eula");
        addDrawableChild(ruleEulaField);

        ruleFreedomField = new TextFieldWidget(textRenderer, fx, y + 20, fw, 16, Text.translatable("pmchat.admin.rules.freedom"));
        ruleFreedomField.setMaxLength(500);
        placeholder(ruleFreedomField, "pmchat.admin.rules.freedom");
        addDrawableChild(ruleFreedomField);

        ruleLineFields = new TextFieldWidget[RULE_LINES];
        for (int i = 0; i < RULE_LINES; i++) {
            TextFieldWidget f = new TextFieldWidget(textRenderer, fx, y + 44 + i * 20, fw, 16,
                    Text.translatable("pmchat.admin.rules.line", i + 1));
            f.setMaxLength(200);
            placeholder(f, "pmchat.admin.rules.line.hint");
            addDrawableChild(f);
            ruleLineFields[i] = f;
        }

        int footerY = y + 44 + RULE_LINES * 20 + 4;
        ruleFooterField = new TextFieldWidget(textRenderer, fx, footerY, fw, 16, Text.translatable("pmchat.admin.rules.footer"));
        ruleFooterField.setMaxLength(500);
        placeholder(ruleFooterField, "pmchat.admin.rules.footer");
        addDrawableChild(ruleFooterField);

        addDrawableChild(FlatButton.centered(textRenderer, fx, footerY + 22, fw, 16,
                Text.translatable("pmchat.admin.rules.save"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doSaveRules()));

        loadRulesForEdit();
    }

    private void loadRulesForEdit() {
        status = Text.translatable("pmchat.admin.loading");
        statusColor = SUBTLE;
        PmBackend.fetchRulesForEdit((ok, content, err) -> {
            if (!ok || content == null) {
                status = Text.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = BAD;
                return;
            }
            status = Text.empty();
            PmBackend.RuleLocale ru = content.ru;
            rulesEnCurrent = content.en;
            rulesHeaderCurrent = ru.header;
            ruleEulaField.setText(ru.eula);
            ruleFreedomField.setText(ru.freedom);
            ruleFooterField.setText(ru.footer);
            for (int i = 0; i < RULE_LINES; i++) {
                ruleLineFields[i].setText(i < ru.rules.size() ? ru.rules.get(i) : "");
            }
        });
    }

    private void doSaveRules() {
        if (rulesEnCurrent == null) return; // текущие правила ещё не подгрузились
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (TextFieldWidget f : ruleLineFields) {
            String t = f.getText().trim();
            if (!t.isEmpty()) lines.add(t);
        }
        if (lines.isEmpty()) {
            setStatus(Text.translatable("pmchat.admin.rules.needline"), BAD);
            return;
        }
        PmBackend.RuleLocale ru = new PmBackend.RuleLocale(
                ruleEulaField.getText().trim(), ruleFreedomField.getText().trim(),
                rulesHeaderCurrent, lines, ruleFooterField.getText().trim());
        PmBackend.adminSetRules(ru, rulesEnCurrent, (ok, v, err) ->
                setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    // ---------- вкладка 6: магазин возможностей ----------

    private void buildShop(int y) {
        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int fx = cx - cardW / 2 + 12;
        int fw = cardW - 24;

        shopListTop = y;
        shopListBottom = y + 5 * ROW_H;

        int formY = shopListBottom + 8;
        shopNameField = new TextFieldWidget(textRenderer, fx, formY, fw, 16, Text.translatable("pmchat.admin.shop.name"));
        shopNameField.setMaxLength(64);
        placeholder(shopNameField, "pmchat.admin.shop.name");
        addDrawableChild(shopNameField);

        shopDescField = new TextFieldWidget(textRenderer, fx, formY + 20, fw, 16, Text.translatable("pmchat.admin.shop.desc"));
        shopDescField.setMaxLength(200);
        placeholder(shopDescField, "pmchat.admin.shop.desc");
        addDrawableChild(shopDescField);

        shopFeatureKeyField = new TextFieldWidget(textRenderer, fx, formY + 40, fw, 16, Text.translatable("pmchat.admin.shop.featurekey"));
        shopFeatureKeyField.setMaxLength(64);
        placeholder(shopFeatureKeyField, "pmchat.admin.shop.featurekey");
        addDrawableChild(shopFeatureKeyField);

        int halfW = (fw - 8) / 2;
        shopPriceField = new TextFieldWidget(textRenderer, fx, formY + 60, halfW, 16, Text.translatable("pmchat.admin.shop.price"));
        shopPriceField.setMaxLength(10);
        placeholder(shopPriceField, "pmchat.admin.shop.price");
        addDrawableChild(shopPriceField);

        shopDurationField = new TextFieldWidget(textRenderer, fx + halfW + 8, formY + 60, halfW, 16, Text.translatable("pmchat.admin.shop.duration"));
        shopDurationField.setMaxLength(5);
        placeholder(shopDurationField, "pmchat.admin.shop.duration");
        addDrawableChild(shopDurationField);

        int btnY = formY + 80;
        int btnW = (fw - 8) / 2;
        addDrawableChild(FlatButton.centered(textRenderer, fx, btnY, btnW, 16,
                Text.translatable("pmchat.admin.shop.save"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doSaveShopItem()));
        addDrawableChild(FlatButton.centered(textRenderer, fx + btnW + 8, btnY, btnW, 16,
                Text.translatable("pmchat.admin.shop.new"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> resetShopForm()));

        loadShopItems();
    }

    private void loadShopItems() {
        status = Text.translatable("pmchat.admin.loading");
        statusColor = SUBTLE;
        PmBackend.adminListShop((ok, list, err) -> {
            if (ok) {
                shopItems = list;
                shopScroll = 0;
                status = shopItems.isEmpty() ? Text.translatable("pmchat.admin.shop.empty") : Text.empty();
            } else {
                shopItems = Collections.emptyList();
                status = Text.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = BAD;
            }
        });
    }

    private void resetShopForm() {
        shopEditingId = 0;
        shopNameField.setText("");
        shopDescField.setText("");
        shopFeatureKeyField.setText("");
        shopPriceField.setText("");
        shopDurationField.setText("");
    }

    private void editShopItem(PmBackend.ShopItem item) {
        shopEditingId = item.id;
        shopNameField.setText(item.name);
        shopDescField.setText(item.description);
        shopFeatureKeyField.setText(item.featureKey != null ? item.featureKey : "");
        shopPriceField.setText(String.valueOf(item.price));
        shopDurationField.setText(String.valueOf(item.durationDays));
    }

    private void doSaveShopItem() {
        String name = shopNameField.getText().trim();
        if (name.isEmpty()) {
            setStatus(Text.translatable("pmchat.admin.shop.needname"), BAD);
            return;
        }
        long price;
        int duration;
        try {
            price = Long.parseLong(shopPriceField.getText().trim());
            duration = Integer.parseInt(shopDurationField.getText().trim());
        } catch (NumberFormatException e) {
            setStatus(Text.translatable("pmchat.admin.badamount"), BAD);
            return;
        }
        if (price < 0 || duration <= 0) {
            setStatus(Text.translatable("pmchat.admin.badamount"), BAD);
            return;
        }
        PmBackend.adminUpsertShopItem(shopEditingId, name, shopDescField.getText().trim(),
                shopFeatureKeyField.getText().trim(), price, duration, (ok, v, err) -> {
                    if (ok) {
                        setStatus(Text.translatable("pmchat.admin.ok"), OK);
                        resetShopForm();
                        loadShopItems();
                    } else {
                        setStatus(Text.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
                    }
                });
    }

    private void deleteShopItem(long id) {
        PmBackend.adminDeleteShopItem(id, (ok, v, err) -> {
            if (ok) {
                if (shopEditingId == id) resetShopForm();
                loadShopItems();
            } else {
                setStatus(Text.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
            }
        });
    }

    private void drawShop(DrawContext context, int mouseX, int mouseY) {
        shopRowRects.clear();
        shopDeleteRects.clear();
        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int left = cx - cardW / 2;
        int right = cx + cardW / 2;

        int y = shopListTop;
        for (int i = shopScroll; i < shopItems.size() && y + ROW_H <= shopListBottom; i++) {
            PmBackend.ShopItem item = shopItems.get(i);
            boolean editing = item.id == shopEditingId;
            boolean hovered = mouseY >= y && mouseY < y + ROW_H - 2 && mouseX >= left && mouseX < right;
            context.fill(left, y, right, y + ROW_H - 2, editing ? PANEL_LIGHT : (hovered ? BTN_HOVER : PANEL));
            context.fill(left, y, left + 2, y + ROW_H - 2, NEON_DIM);

            String line = item.name + " — " + item.price + "/" + item.durationDays + "d";
            String trimmed = trim(line, right - left - 60);
            context.drawText(textRenderer, trimmed, left + 8, y + 6, TEXT_MAIN, false);

            int btnX = right - 46, btnY = y + 2, btnW = 40, btnH = ROW_H - 6;
            boolean btnHover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? BTN_HOVER : BTN_BG);
            context.drawStrokedRectangle(btnX, btnY, btnW, btnH, NEON_DIM);
            Text del = Text.translatable("pmchat.admin.shop.delete");
            context.drawText(textRenderer, del, btnX + (btnW - textRenderer.getWidth(del)) / 2, btnY + 3, BAD, false);
            shopDeleteRects.add(new Object[]{btnX, btnY, btnW, btnH, item.id});
            shopRowRects.add(new Object[]{left, y, right - left - 46, ROW_H - 2, item.id});

            y += ROW_H;
        }

        if (!status.getString().isEmpty()) {
            context.drawText(textRenderer, status, left + 8, shopListTop - 10, statusColor, false);
        }
    }

    // ---------- вкладки 3/4: жалобы и поддержка (общий скроллящийся список) ----------

    private void buildList(int y, boolean reportsTab) {
        listTop = y + 4;
        listBottom = height - 32;
        if (reportsTab) loadReports(); else loadSupport();
    }

    private void loadReports() {
        status = Text.translatable("pmchat.admin.loading");
        statusColor = SUBTLE;
        PmBackend.adminListReports(true, (ok, list, err) -> {
            if (ok) {
                reports = list;
                listScroll = 0;
                status = reports.isEmpty() ? Text.translatable("pmchat.admin.reports.empty") : Text.empty();
            } else {
                reports = Collections.emptyList();
                status = Text.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = BAD;
            }
        });
    }

    private void loadSupport() {
        status = Text.translatable("pmchat.admin.loading");
        statusColor = SUBTLE;
        PmBackend.adminListSupport(true, (ok, list, err) -> {
            if (ok) {
                tickets = list;
                listScroll = 0;
                status = tickets.isEmpty() ? Text.translatable("pmchat.admin.support.empty") : Text.empty();
            } else {
                tickets = Collections.emptyList();
                status = Text.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = BAD;
            }
        });
    }

    private final java.util.List<Object[]> resolveBtnRects = new java.util.ArrayList<>();

    private void drawList(DrawContext context, int mouseX, int mouseY, boolean reportsTab) {
        resolveBtnRects.clear();
        int cardW = Math.min(680, width - 40);
        int cx = width / 2;
        int left = cx - cardW / 2;
        int right = cx + cardW / 2;

        int count = reportsTab ? reports.size() : tickets.size();
        int y = listTop;
        for (int i = listScroll; i < count && y + ROW_H <= listBottom; i++) {
            boolean hovered = mouseY >= y && mouseY < y + ROW_H - 2 && mouseX >= left && mouseX < right;
            context.fill(left, y, right, y + ROW_H - 2, hovered ? PANEL_LIGHT : PANEL);
            context.fill(left, y, left + 2, y + ROW_H - 2, NEON_DIM);

            String line;
            if (reportsTab) {
                PmBackend.ReportEntry r = reports.get(i);
                line = r.reporter + " → " + r.target + ": " + r.reason;
            } else {
                PmBackend.SupportEntry t = tickets.get(i);
                line = t.username + ": " + t.message;
            }
            String trimmed = trim(line, right - left - 90);
            context.drawText(textRenderer, trimmed, left + 8, y + 6, TEXT_MAIN, false);

            int btnX = right - 62, btnY = y + 2, btnW = 54, btnH = ROW_H - 6;
            boolean btnHover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? BTN_HOVER : BTN_BG);
            context.drawStrokedRectangle(btnX, btnY, btnW, btnH, NEON_DIM);
            Text resolve = Text.translatable("pmchat.admin.resolve");
            context.drawText(textRenderer, resolve, btnX + (btnW - textRenderer.getWidth(resolve)) / 2, btnY + 3, OK, false);
            resolveBtnRects.add(new Object[]{btnX, btnY, btnW, btnH, reportsTab
                    ? reports.get(i).id : tickets.get(i).id});

            y += ROW_H;
        }

        if (!status.getString().isEmpty()) {
            context.drawText(textRenderer, status, left + 8, listTop + 2, statusColor, false);
        }
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (textRenderer.getWidth(text) <= maxWidth) return text;
        return textRenderer.trimToWidth(text, Math.max(0, maxWidth - textRenderer.getWidth("…"))) + "…";
    }

    // ---------- общее ----------

    private static void placeholder(TextFieldWidget field, String labelKey) {
        String hint = Text.translatable(labelKey).getString();
        field.setSuggestion(hint);
        field.setChangedListener(s -> field.setSuggestion(s.isEmpty() ? hint : null));
    }

    private void setStatus(Text text, int color) {
        status = text;
        statusColor = color;
    }

    private void drawPanel(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, PANEL);
        context.drawStrokedRectangle(x, y, w, h, NEON_DIM);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BG);
        // Тонкая неоновая линия под шапкой — акцент в стиле киберпанк.
        context.fill(0, 0, width, HEADER_H, PANEL);
        context.fill(0, HEADER_H - 2, width, HEADER_H, NEON);

        Text title = Text.translatable("pmchat.admin.title");
        context.drawText(textRenderer, title, 12, 10, TITLE, false);

        String backend = config.backendUrl == null ? "" : config.backendUrl;
        context.drawText(textRenderer, backend, width - 12 - textRenderer.getWidth(backend), 10, SUBTLE, false);

        super.render(context, mouseX, mouseY, delta);

        switch (tab) {
            case 0 -> drawDashboard(context, mouseX, mouseY);
            case 3 -> drawList(context, mouseX, mouseY, true);
            case 4 -> drawList(context, mouseX, mouseY, false);
            case 6 -> drawShop(context, mouseX, mouseY);
            default -> {
                if (!status.getString().isEmpty()) {
                    context.drawText(textRenderer, status, width / 2 - textRenderer.getWidth(status) / 2,
                            height - 46, statusColor, false);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if ((tab == 3 || tab == 4) && !resolveBtnRects.isEmpty()) {
            double mx = click.x(), my = click.y();
            for (Object[] rect : resolveBtnRects) {
                int x = (int) rect[0], y = (int) rect[1], w = (int) rect[2], h = (int) rect[3];
                long id = (long) rect[4];
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    boolean reportsTab = tab == 3;
                    if (reportsTab) {
                        PmBackend.adminResolveReport(id, (ok, v, err) -> {
                            if (ok) loadReports();
                            else setStatus(Text.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
                        });
                    } else {
                        PmBackend.adminResolveSupport(id, (ok, v, err) -> {
                            if (ok) loadSupport();
                            else setStatus(Text.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
                        });
                    }
                    return true;
                }
            }
        }
        if (tab == 6) {
            double mx = click.x(), my = click.y();
            for (Object[] rect : shopDeleteRects) {
                int x = (int) rect[0], y = (int) rect[1], w = (int) rect[2], h = (int) rect[3];
                long id = (long) rect[4];
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    deleteShopItem(id);
                    return true;
                }
            }
            for (Object[] rect : shopRowRects) {
                int x = (int) rect[0], y = (int) rect[1], w = (int) rect[2], h = (int) rect[3];
                long id = (long) rect[4];
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    for (PmBackend.ShopItem item : shopItems) {
                        if (item.id == id) {
                            editShopItem(item);
                            break;
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == 3 || tab == 4) {
            int count = tab == 3 ? reports.size() : tickets.size();
            int visible = Math.max(1, (listBottom - listTop) / ROW_H);
            int maxScroll = Math.max(0, count - visible);
            listScroll = Math.max(0, Math.min(maxScroll, listScroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        if (tab == 6) {
            int visible = Math.max(1, (shopListBottom - shopListTop) / ROW_H);
            int maxScroll = Math.max(0, shopItems.size() - visible);
            shopScroll = Math.max(0, Math.min(maxScroll, shopScroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
