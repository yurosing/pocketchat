package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
            "pmchat.admin.tab.roles",
            "pmchat.admin.tab.bots",
    };

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();
    private static int lastTab = 0;
    private int tab = lastTab;
    /** Число строк панели вкладок — считается в init() под ширину окна (9 вкладок часто не влезают в одну). */
    private int tabRows = 1;

    private Component status = Component.empty();
    private int statusColor = SUBTLE;

    // ---- Вкладка 0: сводка ----
    private PmBackend.AdminStatus dashStatus;
    private boolean dashError;
    private long lastDashLoadAt = 0;

    private EditBox featureMinutesField;

    // ---- Вкладка 1: рассылка ----
    private EditBox broadcastField;
    private EditBox dmTargetField;
    private EditBox dmMessageField;

    // ---- Вкладка 2: игрок ----
    private EditBox targetField;
    private EditBox amountField;
    private EditBox muteMinutesField;

    // ---- Вкладка 3/4: жалобы и поддержка ----
    private List<PmBackend.ReportEntry> reports = Collections.emptyList();
    private List<PmBackend.SupportEntry> tickets = Collections.emptyList();
    private int listScroll = 0;
    private int listTop, listBottom;

    // ---- Вкладка 5: правила (правится только RU — EN пересылается как есть) ----
    private EditBox ruleEulaField, ruleFreedomField, ruleFooterField;
    private EditBox[] ruleLineFields;
    private PmBackend.RuleLocale rulesEnCurrent;
    private String rulesHeaderCurrent = "";
    private static final int RULE_LINES = 5;

    // ---- Вкладка 6: магазин возможностей ----
    private List<PmBackend.ShopItem> shopItems = Collections.emptyList();
    private int shopScroll = 0;
    private int shopListTop, shopListBottom;
    private long shopEditingId = 0;
    private EditBox shopNameField, shopDescField, shopFeatureKeyField, shopPriceField, shopDurationField;
    private final java.util.List<Object[]> shopRowRects = new java.util.ArrayList<>();
    private final java.util.List<Object[]> shopDeleteRects = new java.util.ArrayList<>();

    // ---- Вкладка 7: должности (роли) игроков ----
    private List<PmBackend.RoleDef> roleDefs = Collections.emptyList();
    private int roleScroll = 0;
    private int roleListTop, roleListBottom;
    private String roleEditingKey = null;
    private EditBox roleKeyField, roleNameField, rolePrefixField, roleColorField;
    private EditBox roleAssignTargetField;
    private final java.util.List<Object[]> roleRowRects = new java.util.ArrayList<>();
    private final java.util.List<Object[]> roleDeleteRects = new java.util.ArrayList<>();

    // Должность «только префикс» по умолчанию (значок-бейдж без отдельного названия) —
    // заводится в списке сама при первом открытии вкладки, чтобы админу не пришлось
    // руками набирать значения, чтобы выдать её игрокам. Флаг — static на класс, чтобы
    // не долбить бэкенд повторной попыткой при каждом открытии вкладки за сессию клиента.
    private static final String DEFAULT_ROLE_KEY = "pocketchat";
    private static final String DEFAULT_ROLE_NAME = "PocketChat";
    private static final String DEFAULT_ROLE_PREFIX = "💬";
    private static final String DEFAULT_ROLE_COLOR = "#25D366";
    private static boolean defaultRoleSeedAttempted = false;

    // ---- Вкладка 8: боты — цены + заявки в магазин ботов ----
    private EditBox botCreatePriceField, botstoreSubmitPriceField;
    private List<PmBackend.BotListingPending> botPending = Collections.emptyList();
    private int botScroll = 0;
    private int botListTop, botListBottom;
    private final java.util.List<Object[]> botApproveRects = new java.util.ArrayList<>();
    private final java.util.List<Object[]> botRejectRects = new java.util.ArrayList<>();

    /** Подписи над полями форм (ключ локализации, x, y) — рисуются в render() поверх полей ниже них. */
    private final java.util.List<Object[]> formLabels = new java.util.ArrayList<>();
    /** Секции-заголовки форм (ключ локализации, x, y). */
    private final java.util.List<Object[]> formSections = new java.util.ArrayList<>();

    public PmAdminScreen(Screen parent) {
        super(Component.translatable("pmchat.admin.title"));
        this.parent = parent;
    }

    /** Текстовое поле с постоянной подписью сверху (не пропадает при вводе, в отличие от {@code placeholder}). */
    private EditBox labeledField(int x, int y, int w, String labelKey, int maxLen) {
        formLabels.add(new Object[]{labelKey, x, y});
        EditBox f = new EditBox(font, x, y + 11, w, 16, Component.translatable(labelKey));
        f.setMaxLength(maxLen);
        addRenderableWidget(f);
        return f;
    }

    private void section(String labelKey, int x, int y) {
        formSections.add(new Object[]{labelKey, x, y});
    }

    @Override
    protected void init() {
        clearWidgets();
        formLabels.clear();
        formSections.clear();
        lastTab = tab;
        status = Component.empty();

        // 9 вкладок в одну строку на узком окне (GUI Scale 4 и т.п.) не влезают — подписи
        // вылезали за края экрана (FlatButton текст не обрезает). Переносим лишние вкладки
        // на вторую (и далее) строку вместо этого.
        int minTabW = 0;
        for (String k : TAB_KEYS) minTabW = Math.max(minTabW, font.getWidth(Component.translatable(k)) + 16);
        int cols = Math.max(1, Math.min(TAB_KEYS.length, width / Math.max(1, minTabW)));
        tabRows = (int) Math.ceil(TAB_KEYS.length / (double) cols);
        int tabW = width / cols;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int ti = i;
            int row = i / cols, col = i % cols;
            addRenderableWidget(FlatButton.centered(font, col * tabW, HEADER_H + row * TAB_H, tabW, TAB_H,
                    Component.translatable(TAB_KEYS[i]),
                    ti == tab ? PANEL_LIGHT : PANEL, BTN_HOVER, NEON_DIM, ti == tab ? TITLE : TEXT_MAIN,
                    btn -> {
                        tab = ti;
                        init();
                    }));
        }

        int contentTop = HEADER_H + TAB_H * tabRows + 10;
        switch (tab) {
            case 0 -> buildDashboard(contentTop);
            case 1 -> buildBroadcast(contentTop);
            case 2 -> buildPlayers(contentTop);
            case 3 -> buildList(contentTop, true);
            case 4 -> buildList(contentTop, false);
            case 5 -> buildRules(contentTop);
            case 6 -> buildShop(contentTop);
            case 7 -> buildRoles(contentTop);
            case 8 -> buildBots(contentTop);
            default -> { }
        }

        addRenderableWidget(FlatButton.centered(font, width - 90, height - 24, 80, 18,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN, btn -> close()));
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

        featureMinutesField = new EditBox(font, fx, fy, fw, 16, Component.translatable("pmchat.admin.feature.minutes"));
        featureMinutesField.setMaxLength(6);
        placeholder(featureMinutesField, "pmchat.admin.feature.minutes");
        addRenderableWidget(featureMinutesField);
        fy += 20;

        int colW = (fw - 8) / 3;
        for (int i = 0; i < FEATURES.length; i++) {
            String name = FEATURES[i];
            String humanName = Component.translatable("pmchat.admin.feature." + name).getString();
            int col = fx + i * (colW + 4);
            addRenderableWidget(FlatButton.centered(font, col, fy, colW, 16,
                    Component.translatable("pmchat.admin.feature.on", humanName), BTN_BG, BTN_HOVER, OK, OK,
                    btn -> toggleFeature(name, false)));
            addRenderableWidget(FlatButton.centered(font, col, fy + 20, colW, 16,
                    Component.translatable("pmchat.admin.feature.off", humanName), BTN_BG, BTN_HOVER, BAD, BAD,
                    btn -> toggleFeature(name, true)));
        }
    }

    private void toggleFeature(String name, boolean disable) {
        int minutes = 0;
        try {
            minutes = Integer.parseInt(featureMinutesField.getValue().trim());
        } catch (NumberFormatException ignored) {
        }
        PmBackend.adminSetFeature(name, !disable, minutes, (ok, v, err) ->
                setStatus(ok ? Component.translatable("pmchat.admin.ok") : Component.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void loadDashboard() {
        lastDashLoadAt = System.currentTimeMillis();
        PmBackend.adminStatus((ok, st, err) -> {
            dashError = !ok;
            dashStatus = ok ? st : null;
        });
    }

    private void drawDashboard(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int top = HEADER_H + TAB_H * tabRows + 14;
        int cx = width / 2;

        if (System.currentTimeMillis() - lastDashLoadAt > 5000) loadDashboard();

        boolean online = dashStatus != null && !dashError;
        int pillW = 160;
        drawPanel(context, cx - pillW / 2, top, pillW, 20);
        String pillText = online ? "● ONLINE" : dashStatus == null ? "…" : "● OFFLINE";
        int pillColor = online ? OK : dashStatus == null ? SUBTLE : BAD;
        context.text(font, pillText,
                cx - font.getWidth(pillText) / 2, top + 6, pillColor, false);

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
                Component.translatable("pmchat.admin.dash.accounts").getString(),
                Component.translatable("pmchat.admin.dash.online").getString(),
                Component.translatable("pmchat.admin.dash.reports").getString(),
                Component.translatable("pmchat.admin.dash.tickets").getString(),
        };
        int[] accent = {NEON, OK, WARN, WARN};
        for (int i = 0; i < 4; i++) {
            int x = tx + i * (tileW + gap);
            drawPanel(context, x, tilesY, tileW, 56);
            context.fill(x, tilesY, x + tileW, tilesY + 2, accent[i]);
            String num = dashStatus != null ? String.valueOf(values[i]) : "—";
            context.text(font, num, x + (tileW - font.getWidth(num)) / 2, tilesY + 14, TEXT_MAIN, false);
            context.text(font, labels[i], x + (tileW - font.getWidth(labels[i])) / 2, tilesY + 38, SUBTLE, false);
        }

        if (dashStatus != null) {
            String uptime = Component.translatable("pmchat.admin.dash.uptime", formatUptime(dashStatus.uptimeSec)).getString();
            context.text(font, uptime, cx - font.getWidth(uptime) / 2, tilesY + 70, SUBTLE, false);
        } else if (dashError) {
            Component err = Component.translatable("pmchat.admin.dash.fail");
            context.text(font, err, cx - font.getWidth(err) / 2, tilesY + 70, BAD, false);
        }

        // Заголовок и состояние блока переключателей фич (кнопки уже добавлены в buildDashboard)
        int cardW = Math.min(420, width - 40);
        int fx = cx - cardW / 2 + 12;
        Component featTitle = Component.translatable("pmchat.admin.feature.title");
        context.text(font, featTitle, fx, height - 140, SUBTLE, false);
        int colW = (cardW - 24 - 8) / 3;
        for (int i = 0; i < FEATURES.length; i++) {
            boolean enabled = PmBackend.isFeatureEnabled(FEATURES[i]);
            int col = fx + i * (colW + 4);
            String state = enabled ? "●" : "○";
            context.text(font, state, col, height - 100, enabled ? OK : BAD, false);
        }

        if (!status.getString().isEmpty()) {
            context.text(font, status, width / 2 - font.getWidth(status) / 2, height - 46, statusColor, false);
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

        broadcastField = new EditBox(font, fx, y + 24, fw, 16, Component.translatable("pmchat.admin.broadcast.hint"));
        broadcastField.setMaxLength(500);
        placeholder(broadcastField, "pmchat.admin.broadcast.hint");
        addRenderableWidget(broadcastField);
        addRenderableWidget(FlatButton.centered(font, fx, y + 46, fw, 16,
                Component.translatable("pmchat.admin.broadcast.send"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doBroadcast()));

        int dmY = y + 84;
        int dmTargetW = fw - 22;
        dmTargetField = new EditBox(font, fx, dmY, dmTargetW, 16, Component.translatable("pmchat.admin.target.hint"));
        dmTargetField.setMaxLength(32);
        placeholder(dmTargetField, "pmchat.admin.target.hint");
        addRenderableWidget(dmTargetField);
        FlatButton browseBtn = FlatButton.centered(font, fx + dmTargetW + 4, dmY, 18, 16,
                Component.literal("☰"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> Minecraft.getInstance().setScreen(
                        new PmAdminAccountsScreen(this, name -> dmTargetField.setValue(name))));
        addRenderableWidget(browseBtn);

        dmMessageField = new EditBox(font, fx, dmY + 22, fw, 16, Component.translatable("pmchat.admin.dm.hint"));
        dmMessageField.setMaxLength(500);
        placeholder(dmMessageField, "pmchat.admin.dm.hint");
        addRenderableWidget(dmMessageField);
        addRenderableWidget(FlatButton.centered(font, fx, dmY + 44, fw, 16,
                Component.translatable("pmchat.admin.dm.send"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doDirectMessage()));
    }

    private void doBroadcast() {
        String msg = broadcastField.getValue().trim();
        if (msg.isEmpty()) return;
        PmBackend.adminBroadcast(msg, (ok, v, err) -> {
            if (ok) {
                broadcastField.setValue("");
                setStatus(Component.translatable("pmchat.admin.ok"), OK);
            } else {
                setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
            }
        });
    }

    private void doDirectMessage() {
        String target = dmTargetField.getValue().trim();
        String msg = dmMessageField.getValue().trim();
        if (target.isEmpty() || msg.isEmpty()) return;
        PmBackend.adminMessage(target, msg, (ok, v, err) -> {
            if (ok) {
                dmMessageField.setValue("");
                setStatus(Component.translatable("pmchat.admin.ok"), OK);
            } else {
                setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
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
        targetField = new EditBox(font, fx, y + 20, targetFieldW, 16, Component.translatable("pmchat.admin.target.hint"));
        targetField.setMaxLength(32);
        placeholder(targetField, "pmchat.admin.target.hint");
        addRenderableWidget(targetField);
        addRenderableWidget(FlatButton.centered(font, fx + targetFieldW + 4, y + 20, 18, 16,
                Component.literal("☰"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> Minecraft.getInstance().setScreen(
                        new PmAdminAccountsScreen(this, name -> targetField.setValue(name)))));

        int ay = y + 44;
        amountField = new EditBox(font, fx, ay, (fw - 6) / 2, 16, Component.translatable("pmchat.admin.amount.hint"));
        amountField.setMaxLength(10);
        placeholder(amountField, "pmchat.admin.amount.hint");
        addRenderableWidget(amountField);
        addRenderableWidget(FlatButton.centered(font, fx + (fw - 6) / 2 + 6, ay, (fw - 6) / 2, 16,
                Component.translatable("pmchat.admin.grant"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN, btn -> doGrant()));

        int vy = ay + 24;
        addRenderableWidget(FlatButton.centered(font, fx, vy, (fw - 6) / 2, 16,
                Component.translatable("pmchat.admin.verify.on"), BTN_BG, BTN_HOVER, OK, OK, btn -> doVerify(true)));
        addRenderableWidget(FlatButton.centered(font, fx + (fw - 6) / 2 + 6, vy, (fw - 6) / 2, 16,
                Component.translatable("pmchat.admin.verify.off"), BTN_BG, BTN_HOVER, BAD, BAD, btn -> doVerify(false)));

        addRenderableWidget(FlatButton.centered(font, fx, vy + 24, fw, 16,
                Component.translatable("pmchat.admin.official"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN, btn -> doOfficial()));

        // Модерация: временный мут (в минутах) и постоянный бан — единственный способ
        // заблокировать отправку ЛС/голосовых/фото у игрока (клиент сам проверяет
        // свой статус, бэкенд не видит /m напрямую, см. PmScreen.blockIfMuted).
        int my = vy + 48;
        muteMinutesField = new EditBox(font, fx, my, (fw - 6) / 2, 16, Component.translatable("pmchat.admin.mute.minutes"));
        muteMinutesField.setMaxLength(6);
        placeholder(muteMinutesField, "pmchat.admin.mute.minutes");
        addRenderableWidget(muteMinutesField);
        addRenderableWidget(FlatButton.centered(font, fx + (fw - 6) / 2 + 6, my, (fw - 6) / 2, 16,
                Component.translatable("pmchat.admin.mute.apply"), BTN_BG, BTN_HOVER, WARN, WARN, btn -> doMute()));

        int by = my + 24;
        addRenderableWidget(FlatButton.centered(font, fx, by, (fw - 6) / 2, 16,
                Component.translatable("pmchat.admin.ban.on"), BTN_BG, BTN_HOVER, BAD, BAD, btn -> doBan(true)));
        addRenderableWidget(FlatButton.centered(font, fx + (fw - 6) / 2 + 6, by, (fw - 6) / 2, 16,
                Component.translatable("pmchat.admin.ban.off"), BTN_BG, BTN_HOVER, OK, OK, btn -> doBan(false)));
    }

    private void doMute() {
        String target = targetField.getValue().trim();
        if (target.isEmpty()) return;
        int minutes;
        try {
            minutes = Integer.parseInt(muteMinutesField.getValue().trim());
        } catch (NumberFormatException e) {
            setStatus(Component.translatable("pmchat.admin.badamount"), BAD);
            return;
        }
        PmBackend.adminMute(target, minutes, (ok, v, err) ->
                setStatus(ok ? Component.translatable("pmchat.admin.ok") : Component.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void doBan(boolean banned) {
        String target = targetField.getValue().trim();
        if (target.isEmpty()) return;
        PmBackend.adminBan(target, banned, (ok, v, err) ->
                setStatus(ok ? Component.translatable("pmchat.admin.ok") : Component.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void doGrant() {
        String target = targetField.getValue().trim();
        long amount;
        try {
            amount = Long.parseLong(amountField.getValue().trim());
        } catch (NumberFormatException e) {
            setStatus(Component.translatable("pmchat.admin.badamount"), BAD);
            return;
        }
        if (target.isEmpty() || amount == 0) return;
        PmBackend.adminGrantCurrency(target, amount, (ok, v, err) ->
                setStatus(ok ? Component.translatable("pmchat.admin.ok") : Component.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void doVerify(boolean verified) {
        String target = targetField.getValue().trim();
        if (target.isEmpty()) return;
        PmBackend.adminVerify(target, verified, (ok, v, err) ->
                setStatus(ok ? Component.translatable("pmchat.admin.ok") : Component.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void doOfficial() {
        String target = targetField.getValue().trim();
        if (target.isEmpty()) return;
        PmBackend.adminSetOfficial(target, true, null, (ok, v, err) ->
                setStatus(ok ? Component.translatable("pmchat.admin.ok") : Component.translatable("pmchat.admin.fail", String.valueOf(err)),
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

        ruleEulaField = new EditBox(font, fx, y, fw, 16, Component.translatable("pmchat.admin.rules.eula"));
        ruleEulaField.setMaxLength(500);
        placeholder(ruleEulaField, "pmchat.admin.rules.eula");
        addRenderableWidget(ruleEulaField);

        ruleFreedomField = new EditBox(font, fx, y + 20, fw, 16, Component.translatable("pmchat.admin.rules.freedom"));
        ruleFreedomField.setMaxLength(500);
        placeholder(ruleFreedomField, "pmchat.admin.rules.freedom");
        addRenderableWidget(ruleFreedomField);

        ruleLineFields = new EditBox[RULE_LINES];
        for (int i = 0; i < RULE_LINES; i++) {
            EditBox f = new EditBox(font, fx, y + 44 + i * 20, fw, 16,
                    Component.translatable("pmchat.admin.rules.line", i + 1));
            f.setMaxLength(200);
            placeholder(f, "pmchat.admin.rules.line.hint");
            addRenderableWidget(f);
            ruleLineFields[i] = f;
        }

        int footerY = y + 44 + RULE_LINES * 20 + 4;
        ruleFooterField = new EditBox(font, fx, footerY, fw, 16, Component.translatable("pmchat.admin.rules.footer"));
        ruleFooterField.setMaxLength(500);
        placeholder(ruleFooterField, "pmchat.admin.rules.footer");
        addRenderableWidget(ruleFooterField);

        addRenderableWidget(FlatButton.centered(font, fx, footerY + 22, fw, 16,
                Component.translatable("pmchat.admin.rules.save"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doSaveRules()));

        loadRulesForEdit();
    }

    private void loadRulesForEdit() {
        status = Component.translatable("pmchat.admin.loading");
        statusColor = SUBTLE;
        PmBackend.fetchRulesForEdit((ok, content, err) -> {
            if (!ok || content == null) {
                status = Component.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = BAD;
                return;
            }
            status = Component.empty();
            PmBackend.RuleLocale ru = content.ru;
            rulesEnCurrent = content.en;
            rulesHeaderCurrent = ru.header;
            ruleEulaField.setValue(ru.eula);
            ruleFreedomField.setValue(ru.freedom);
            ruleFooterField.setValue(ru.footer);
            for (int i = 0; i < RULE_LINES; i++) {
                ruleLineFields[i].setValue(i < ru.rules.size() ? ru.rules.get(i) : "");
            }
        });
    }

    private void doSaveRules() {
        if (rulesEnCurrent == null) return; // текущие правила ещё не подгрузились
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (EditBox f : ruleLineFields) {
            String t = f.getValue().trim();
            if (!t.isEmpty()) lines.add(t);
        }
        if (lines.isEmpty()) {
            setStatus(Component.translatable("pmchat.admin.rules.needline"), BAD);
            return;
        }
        PmBackend.RuleLocale ru = new PmBackend.RuleLocale(
                ruleEulaField.getValue().trim(), ruleFreedomField.getValue().trim(),
                rulesHeaderCurrent, lines, ruleFooterField.getValue().trim());
        PmBackend.adminSetRules(ru, rulesEnCurrent, (ok, v, err) ->
                setStatus(ok ? Component.translatable("pmchat.admin.ok") : Component.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    // ---------- вкладка 6: магазин возможностей ----------

    private void buildShop(int y) {
        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int fx = cx - cardW / 2 + 12;
        int fw = cardW - 24;

        section("pmchat.admin.shop.section.list", fx, y - 2);
        shopListTop = y + 12;
        shopListBottom = shopListTop + 5 * ROW_H;

        int formY = shopListBottom + 10;
        section("pmchat.admin.shop.section.edit", fx, formY);
        formY += 14;

        shopNameField = labeledField(fx, formY, fw, "pmchat.admin.shop.name", 64);
        formY += 30;
        shopDescField = labeledField(fx, formY, fw, "pmchat.admin.shop.desc", 200);
        formY += 30;
        shopFeatureKeyField = labeledField(fx, formY, fw, "pmchat.admin.shop.featurekey", 64);
        formY += 30;

        int halfW = (fw - 8) / 2;
        shopPriceField = labeledField(fx, formY, halfW, "pmchat.admin.shop.price", 10);
        shopDurationField = labeledField(fx + halfW + 8, formY, halfW, "pmchat.admin.shop.duration", 5);
        formY += 30;

        int btnW = (fw - 8) / 2;
        addRenderableWidget(FlatButton.centered(font, fx, formY, btnW, 16,
                Component.translatable("pmchat.admin.shop.save"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doSaveShopItem()));
        addRenderableWidget(FlatButton.centered(font, fx + btnW + 8, formY, btnW, 16,
                Component.translatable("pmchat.admin.shop.new"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> resetShopForm()));

        loadShopItems();
    }

    private void loadShopItems() {
        status = Component.translatable("pmchat.admin.loading");
        statusColor = SUBTLE;
        PmBackend.adminListShop((ok, list, err) -> {
            if (ok) {
                shopItems = list;
                shopScroll = 0;
                status = shopItems.isEmpty() ? Component.translatable("pmchat.admin.shop.empty") : Component.empty();
            } else {
                shopItems = Collections.emptyList();
                status = Component.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = BAD;
            }
        });
    }

    private void resetShopForm() {
        shopEditingId = 0;
        shopNameField.setValue("");
        shopDescField.setValue("");
        shopFeatureKeyField.setValue("");
        shopPriceField.setValue("");
        shopDurationField.setValue("");
    }

    private void editShopItem(PmBackend.ShopItem item) {
        shopEditingId = item.id;
        shopNameField.setValue(item.name);
        shopDescField.setValue(item.description);
        shopFeatureKeyField.setValue(item.featureKey != null ? item.featureKey : "");
        shopPriceField.setValue(String.valueOf(item.price));
        shopDurationField.setValue(String.valueOf(item.durationDays));
    }

    private void doSaveShopItem() {
        String name = shopNameField.getValue().trim();
        if (name.isEmpty()) {
            setStatus(Component.translatable("pmchat.admin.shop.needname"), BAD);
            return;
        }
        long price;
        int duration;
        try {
            price = Long.parseLong(shopPriceField.getValue().trim());
            duration = Integer.parseInt(shopDurationField.getValue().trim());
        } catch (NumberFormatException e) {
            setStatus(Component.translatable("pmchat.admin.badamount"), BAD);
            return;
        }
        if (price < 0 || duration <= 0) {
            setStatus(Component.translatable("pmchat.admin.badamount"), BAD);
            return;
        }
        PmBackend.adminUpsertShopItem(shopEditingId, name, shopDescField.getValue().trim(),
                shopFeatureKeyField.getValue().trim(), price, duration, (ok, v, err) -> {
                    if (ok) {
                        setStatus(Component.translatable("pmchat.admin.ok"), OK);
                        resetShopForm();
                        loadShopItems();
                    } else {
                        setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
                    }
                });
    }

    private void deleteShopItem(long id) {
        PmBackend.adminDeleteShopItem(id, (ok, v, err) -> {
            if (ok) {
                if (shopEditingId == id) resetShopForm();
                loadShopItems();
            } else {
                setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
            }
        });
    }

    private void drawShop(GuiGraphicsExtractor context, int mouseX, int mouseY) {
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
            context.text(font, trimmed, left + 8, y + 6, TEXT_MAIN, false);

            int btnX = right - 46, btnY = y + 2, btnW = 40, btnH = ROW_H - 6;
            boolean btnHover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? BTN_HOVER : BTN_BG);
            context.outline(btnX, btnY, btnW, btnH, NEON_DIM);
            Component del = Component.translatable("pmchat.admin.shop.delete");
            context.text(font, del, btnX + (btnW - font.getWidth(del)) / 2, btnY + 3, BAD, false);
            shopDeleteRects.add(new Object[]{btnX, btnY, btnW, btnH, item.id});
            shopRowRects.add(new Object[]{left, y, right - left - 46, ROW_H - 2, item.id});

            y += ROW_H;
        }

        if (shopItems.isEmpty() && !status.getString().isEmpty()) {
            context.text(font, status, left + 8, shopListTop + 2, statusColor, false);
        } else if (!status.getString().isEmpty()) {
            context.text(font, status, width / 2 - font.getWidth(status) / 2, height - 46, statusColor, false);
        }
    }

    // ---------- вкладка 7: должности (роли) игроков ----------

    private void buildRoles(int y) {
        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int fx = cx - cardW / 2 + 12;
        int fw = cardW - 24;

        section("pmchat.admin.role.section.list", fx, y - 2);
        roleListTop = y + 12;
        roleListBottom = roleListTop + 4 * ROW_H;

        int formY = roleListBottom + 10;
        section("pmchat.admin.role.section.edit", fx, formY);
        formY += 14;

        roleKeyField = labeledField(fx, formY, fw, "pmchat.admin.role.key", 32);
        formY += 30;
        roleNameField = labeledField(fx, formY, fw, "pmchat.admin.role.name", 40);
        formY += 30;

        int halfW = (fw - 8) / 2 - 18;
        rolePrefixField = labeledField(fx, formY, halfW, "pmchat.admin.role.prefix", 8);
        roleColorField = labeledField(fx + halfW + 26, formY, (fw - 8) / 2 - 8, "pmchat.admin.role.color", 9);
        formY += 30;

        int btnW = (fw - 8) / 2;
        addRenderableWidget(FlatButton.centered(font, fx, formY, btnW, 16,
                Component.translatable("pmchat.admin.shop.save"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doSaveRole()));
        addRenderableWidget(FlatButton.centered(font, fx + btnW + 8, formY, btnW, 16,
                Component.translatable("pmchat.admin.shop.new"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> resetRoleForm()));
        formY += 26;

        section("pmchat.admin.role.section.assign", fx, formY);
        formY += 14;
        roleAssignTargetField = labeledField(fx, formY, fw, "pmchat.admin.target.hint", 32);
        formY += 30;

        addRenderableWidget(FlatButton.centered(font, fx, formY, btnW, 16,
                Component.translatable("pmchat.admin.role.assign"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> {
                    if (roleEditingKey == null) {
                        setStatus(Component.translatable("pmchat.admin.role.needselect"), BAD);
                        return;
                    }
                    doAssignRole(roleEditingKey);
                }));
        addRenderableWidget(FlatButton.centered(font, fx + btnW + 8, formY, btnW, 16,
                Component.translatable("pmchat.admin.role.unassign"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doAssignRole(null)));

        loadRoleDefs();
    }

    private void loadRoleDefs() {
        status = Component.translatable("pmchat.admin.loading");
        statusColor = SUBTLE;
        PmBackend.adminListRoles((ok, list, err) -> {
            if (ok) {
                roleDefs = list;
                roleScroll = 0;
                status = roleDefs.isEmpty() ? Component.translatable("pmchat.admin.role.empty") : Component.empty();
                maybeSeedDefaultRole();
            } else {
                roleDefs = Collections.emptyList();
                status = Component.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = BAD;
            }
        });
    }

    /** Заводит DEFAULT_ROLE_* один раз за сессию, если её ещё нет в списке должностей. */
    private void maybeSeedDefaultRole() {
        if (defaultRoleSeedAttempted) return;
        for (PmBackend.RoleDef r : roleDefs) {
            if (r.key.equalsIgnoreCase(DEFAULT_ROLE_KEY)) {
                defaultRoleSeedAttempted = true;
                return;
            }
        }
        defaultRoleSeedAttempted = true;
        PmBackend.adminUpsertRole(DEFAULT_ROLE_KEY, DEFAULT_ROLE_NAME, DEFAULT_ROLE_PREFIX, DEFAULT_ROLE_COLOR,
                (ok, v, err) -> {
                    if (ok) loadRoleDefs();
                });
    }

    private void resetRoleForm() {
        roleEditingKey = null;
        roleKeyField.setValue("");
        roleNameField.setValue("");
        rolePrefixField.setValue("");
        roleColorField.setValue("");
    }

    private void editRoleDef(PmBackend.RoleDef r) {
        roleEditingKey = r.key;
        roleKeyField.setValue(r.key);
        roleNameField.setValue(r.name);
        rolePrefixField.setValue(r.prefix);
        roleColorField.setValue(String.format("#%06X", r.color & 0xFFFFFF));
    }

    private void doSaveRole() {
        String key = roleKeyField.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        String name = roleNameField.getValue().trim();
        String prefix = rolePrefixField.getValue().trim();
        String color = roleColorField.getValue().trim();
        if (key.isEmpty() || !key.matches("[a-z0-9_-]+")) {
            setStatus(Component.translatable("pmchat.admin.role.needkey"), BAD);
            return;
        }
        // Должность «только префикс» (5.6): полное название необязательно, если задан
        // значок-префикс — на профиле в качестве подписи используется он же.
        if (name.isEmpty() && prefix.isEmpty()) {
            setStatus(Component.translatable("pmchat.admin.role.needprefix"), BAD);
            return;
        }
        if (name.isEmpty()) name = prefix;
        if (color.isEmpty()) color = "#FFFFFF";
        if (!color.matches("(?i)#[0-9a-f]{6}([0-9a-f]{2})?")) {
            setStatus(Component.translatable("pmchat.admin.role.badcolor"), BAD);
            return;
        }
        PmBackend.adminUpsertRole(key, name, prefix, color, (ok, v, err) -> {
            if (ok) {
                setStatus(Component.translatable("pmchat.admin.ok"), OK);
                resetRoleForm();
                loadRoleDefs();
            } else {
                setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
            }
        });
    }

    /** Best-effort разбор "#RRGGBB"/"#RRGGBBAA" для живого предпросмотра — серый, пока текст не похож на цвет. */
    private static int previewColor(String hex) {
        String h = hex == null ? "" : hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (!h.matches("(?i)[0-9a-f]{6}([0-9a-f]{2})?")) return 0xFF444444;
        try {
            return h.length() > 6 ? (int) Long.parseLong(h, 16) : 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return 0xFF444444;
        }
    }

    private void deleteRoleDef(String key) {
        PmBackend.adminDeleteRole(key, (ok, v, err) -> {
            if (ok) {
                if (key.equals(roleEditingKey)) resetRoleForm();
                loadRoleDefs();
            } else {
                setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
            }
        });
    }

    private void doAssignRole(String roleKey) {
        String target = roleAssignTargetField.getValue().trim();
        if (target.isEmpty()) {
            setStatus(Component.translatable("pmchat.admin.target.needed"), BAD);
            return;
        }
        PmBackend.adminAssignRole(target, roleKey, (ok, v, err) ->
                setStatus(ok ? Component.translatable("pmchat.admin.ok") : Component.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void drawRoles(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        roleRowRects.clear();
        roleDeleteRects.clear();
        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int left = cx - cardW / 2;
        int right = cx + cardW / 2;

        int y = roleListTop;
        for (int i = roleScroll; i < roleDefs.size() && y + ROW_H <= roleListBottom; i++) {
            PmBackend.RoleDef r = roleDefs.get(i);
            boolean editing = r.key.equals(roleEditingKey);
            boolean hovered = mouseY >= y && mouseY < y + ROW_H - 2 && mouseX >= left && mouseX < right;
            context.fill(left, y, right, y + ROW_H - 2, editing ? PANEL_LIGHT : (hovered ? BTN_HOVER : PANEL));
            context.fill(left, y, left + 2, y + ROW_H - 2, r.color);

            String line = (r.prefix.isEmpty() ? "" : r.prefix + " ") + r.name + " (" + r.key + ")";
            String trimmed = trim(line, right - left - 60);
            context.text(font, trimmed, left + 8, y + 6, r.color, false);

            int btnX = right - 46, btnY = y + 2, btnW = 40, btnH = ROW_H - 6;
            boolean btnHover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? BTN_HOVER : BTN_BG);
            context.outline(btnX, btnY, btnW, btnH, NEON_DIM);
            Component del = Component.translatable("pmchat.admin.shop.delete");
            context.text(font, del, btnX + (btnW - font.getWidth(del)) / 2, btnY + 3, BAD, false);
            roleDeleteRects.add(new Object[]{btnX, btnY, btnW, btnH, r.key});
            roleRowRects.add(new Object[]{left, y, right - left - 46, ROW_H - 2, r.key});

            y += ROW_H;
        }

        if (roleDefs.isEmpty() && !status.getString().isEmpty()) {
            context.text(font, status, left + 8, roleListTop + 2, statusColor, false);
        } else if (!status.getString().isEmpty()) {
            context.text(font, status, width / 2 - font.getWidth(status) / 2, height - 46, statusColor, false);
        }
    }

    // ---------- вкладка 8: боты — цены + заявки в магазин ----------

    private void buildBots(int y) {
        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int fx = cx - cardW / 2 + 12;
        int fw = cardW - 24;

        section("pmchat.admin.bots.section.prices", fx, y - 2);
        int halfW = (fw - 8) / 2;
        botCreatePriceField = labeledField(fx, y + 12, halfW, "pmchat.admin.bots.createprice", 9);
        botstoreSubmitPriceField = labeledField(fx + halfW + 8, y + 12, halfW, "pmchat.admin.bots.submitprice", 9);
        addRenderableWidget(FlatButton.centered(font, fx, y + 42, fw, 16,
                Component.translatable("pmchat.admin.shop.save"), BTN_BG, BTN_HOVER, NEON_DIM, TEXT_MAIN,
                btn -> doSavePrices()));

        int listY = y + 68;
        section("pmchat.admin.bots.section.pending", fx, listY - 2);
        botListTop = listY + 12;
        botListBottom = height - 32;

        loadPrices();
        loadBotPending();
    }

    private void loadPrices() {
        PmBackend.adminGetPrices((ok, prices, err) -> {
            if (ok && prices != null) {
                botCreatePriceField.setValue(String.valueOf(prices.botCreatePrice));
                botstoreSubmitPriceField.setValue(String.valueOf(prices.botstoreSubmitPrice));
            }
        });
    }

    private void doSavePrices() {
        long createPrice, submitPrice;
        try {
            createPrice = Long.parseLong(botCreatePriceField.getValue().trim());
            submitPrice = Long.parseLong(botstoreSubmitPriceField.getValue().trim());
        } catch (NumberFormatException e) {
            setStatus(Component.translatable("pmchat.admin.badamount"), BAD);
            return;
        }
        if (createPrice < 0 || submitPrice < 0) {
            setStatus(Component.translatable("pmchat.admin.badamount"), BAD);
            return;
        }
        PmBackend.adminSetPrices(createPrice, submitPrice, (ok, v, err) ->
                setStatus(ok ? Component.translatable("pmchat.admin.ok") : Component.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? OK : BAD));
    }

    private void loadBotPending() {
        PmBackend.adminBotstorePending((ok, list, err) -> {
            if (ok) {
                botPending = list;
                botScroll = 0;
            } else {
                botPending = Collections.emptyList();
                setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
            }
        });
    }

    private void reviewBotListing(long id, boolean approve) {
        PmBackend.adminReviewBotListing(id, approve, (ok, v, err) -> {
            if (ok) loadBotPending();
            else setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
        });
    }

    private void drawBots(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        botApproveRects.clear();
        botRejectRects.clear();
        int cardW = Math.min(420, width - 40);
        int cx = width / 2;
        int left = cx - cardW / 2;
        int right = cx + cardW / 2;

        if (botPending.isEmpty()) {
            context.text(font, Component.translatable("pmchat.admin.bots.pending.empty"),
                    left, botListTop, SUBTLE, false);
            return;
        }

        int y = botListTop;
        for (int i = botScroll; i < botPending.size() && y + ROW_H <= botListBottom; i++) {
            PmBackend.BotListingPending p = botPending.get(i);
            boolean hovered = mouseY >= y && mouseY < y + ROW_H - 2 && mouseX >= left && mouseX < right;
            context.fill(left, y, right, y + ROW_H - 2, hovered ? PANEL_LIGHT : PANEL);
            context.fill(left, y, left + 2, y + ROW_H - 2, NEON_DIM);

            String line = p.owner + ": " + p.name + " (" + p.price + ")";
            context.text(font, trim(line, right - left - 100), left + 8, y + 6, TEXT_MAIN, false);

            int btnW = 44, btnH = ROW_H - 6, btnY = y + 2;
            int rejX = right - btnW - 2, apprX = rejX - btnW - 4;
            boolean apprHover = mouseX >= apprX && mouseX < apprX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            boolean rejHover = mouseX >= rejX && mouseX < rejX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            context.fill(apprX, btnY, apprX + btnW, btnY + btnH, apprHover ? BTN_HOVER : BTN_BG);
            context.outline(apprX, btnY, btnW, btnH, OK);
            Component approve = Component.translatable("pmchat.admin.bots.approve");
            context.text(font, approve, apprX + (btnW - font.getWidth(approve)) / 2, btnY + 3, OK, false);
            context.fill(rejX, btnY, rejX + btnW, btnY + btnH, rejHover ? BTN_HOVER : BTN_BG);
            context.outline(rejX, btnY, btnW, btnH, BAD);
            Component reject = Component.translatable("pmchat.admin.bots.reject");
            context.text(font, reject, rejX + (btnW - font.getWidth(reject)) / 2, btnY + 3, BAD, false);

            botApproveRects.add(new Object[]{apprX, btnY, btnW, btnH, p.id});
            botRejectRects.add(new Object[]{rejX, btnY, btnW, btnH, p.id});

            y += ROW_H;
        }
    }

    // ---------- вкладки 3/4: жалобы и поддержка (общий скроллящийся список) ----------

    private void buildList(int y, boolean reportsTab) {
        listTop = y + 4;
        listBottom = height - 32;
        if (reportsTab) loadReports(); else loadSupport();
    }

    private void loadReports() {
        status = Component.translatable("pmchat.admin.loading");
        statusColor = SUBTLE;
        PmBackend.adminListReports(true, (ok, list, err) -> {
            if (ok) {
                reports = list;
                listScroll = 0;
                status = reports.isEmpty() ? Component.translatable("pmchat.admin.reports.empty") : Component.empty();
            } else {
                reports = Collections.emptyList();
                status = Component.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = BAD;
            }
        });
    }

    private void loadSupport() {
        status = Component.translatable("pmchat.admin.loading");
        statusColor = SUBTLE;
        PmBackend.adminListSupport(true, (ok, list, err) -> {
            if (ok) {
                tickets = list;
                listScroll = 0;
                status = tickets.isEmpty() ? Component.translatable("pmchat.admin.support.empty") : Component.empty();
            } else {
                tickets = Collections.emptyList();
                status = Component.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = BAD;
            }
        });
    }

    private final java.util.List<Object[]> resolveBtnRects = new java.util.ArrayList<>();

    private void drawList(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean reportsTab) {
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
            context.text(font, trimmed, left + 8, y + 6, TEXT_MAIN, false);

            int btnX = right - 62, btnY = y + 2, btnW = 54, btnH = ROW_H - 6;
            boolean btnHover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? BTN_HOVER : BTN_BG);
            context.outline(btnX, btnY, btnW, btnH, NEON_DIM);
            Component resolve = Component.translatable("pmchat.admin.resolve");
            context.text(font, resolve, btnX + (btnW - font.getWidth(resolve)) / 2, btnY + 3, OK, false);
            resolveBtnRects.add(new Object[]{btnX, btnY, btnW, btnH, reportsTab
                    ? reports.get(i).id : tickets.get(i).id});

            y += ROW_H;
        }

        if (!status.getString().isEmpty()) {
            context.text(font, status, left + 8, listTop + 2, statusColor, false);
        }
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.getWidth(text) <= maxWidth) return text;
        return font.trimToWidth(text, Math.max(0, maxWidth - font.getWidth("…"))) + "…";
    }

    // ---------- общее ----------

    private static void placeholder(EditBox field, String labelKey) {
        String hint = Component.translatable(labelKey).getString();
        field.setSuggestion(hint);
        field.setResponder(s -> field.setSuggestion(s.isEmpty() ? hint : null));
    }

    private void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
    }

    private void drawPanel(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, PANEL);
        context.outline(x, y, w, h, NEON_DIM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BG);
        // Тонкая неоновая линия под шапкой — акцент в стиле киберпанк.
        context.fill(0, 0, width, HEADER_H, PANEL);
        context.fill(0, HEADER_H - 2, width, HEADER_H, NEON);

        Component title = Component.translatable("pmchat.admin.title");
        context.text(font, title, 12, 10, TITLE, false);

        String backend = config.backendUrl == null ? "" : config.backendUrl;
        context.text(font, backend, width - 12 - font.getWidth(backend), 10, SUBTLE, false);

        super.extractRenderState(context, mouseX, mouseY, delta);

        for (Object[] entry : formSections) {
            context.text(font, Component.translatable((String) entry[0]), (int) entry[1], (int) entry[2], TITLE, false);
        }
        for (Object[] entry : formLabels) {
            context.text(font, Component.translatable((String) entry[0]), (int) entry[1], (int) entry[2], SUBTLE, false);
        }
        if (tab == 7 && roleColorField != null && rolePrefixField != null) {
            int sx = rolePrefixField.getX() + rolePrefixField.getWidth() + 5;
            int sy = roleColorField.getY();
            context.fill(sx, sy, sx + 16, sy + 16, previewColor(roleColorField.getValue()));
            context.outline(sx, sy, 16, 16, NEON_DIM);
        }

        switch (tab) {
            case 0 -> drawDashboard(context, mouseX, mouseY);
            case 3 -> drawList(context, mouseX, mouseY, true);
            case 4 -> drawList(context, mouseX, mouseY, false);
            case 6 -> drawShop(context, mouseX, mouseY);
            case 7 -> drawRoles(context, mouseX, mouseY);
            case 8 -> drawBots(context, mouseX, mouseY);
            default -> {
                if (!status.getString().isEmpty()) {
                    context.text(font, status, width / 2 - font.getWidth(status) / 2,
                            height - 46, statusColor, false);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
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
                            else setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
                        });
                    } else {
                        PmBackend.adminResolveSupport(id, (ok, v, err) -> {
                            if (ok) loadSupport();
                            else setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), BAD);
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
        if (tab == 7) {
            double mx = click.x(), my = click.y();
            for (Object[] rect : roleDeleteRects) {
                int x = (int) rect[0], y = (int) rect[1], w = (int) rect[2], h = (int) rect[3];
                String key = (String) rect[4];
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    deleteRoleDef(key);
                    return true;
                }
            }
            for (Object[] rect : roleRowRects) {
                int x = (int) rect[0], y = (int) rect[1], w = (int) rect[2], h = (int) rect[3];
                String key = (String) rect[4];
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    for (PmBackend.RoleDef r : roleDefs) {
                        if (r.key.equals(key)) {
                            editRoleDef(r);
                            break;
                        }
                    }
                    return true;
                }
            }
        }
        if (tab == 8) {
            double mx = click.x(), my = click.y();
            for (Object[] rect : botApproveRects) {
                int x = (int) rect[0], y = (int) rect[1], w = (int) rect[2], h = (int) rect[3];
                long id = (long) rect[4];
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    reviewBotListing(id, true);
                    return true;
                }
            }
            for (Object[] rect : botRejectRects) {
                int x = (int) rect[0], y = (int) rect[1], w = (int) rect[2], h = (int) rect[3];
                long id = (long) rect[4];
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    reviewBotListing(id, false);
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
        if (tab == 7) {
            int visible = Math.max(1, (roleListBottom - roleListTop) / ROW_H);
            int maxScroll = Math.max(0, roleDefs.size() - visible);
            roleScroll = Math.max(0, Math.min(maxScroll, roleScroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        if (tab == 8) {
            int visible = Math.max(1, (botListBottom - botListTop) / ROW_H);
            int maxScroll = Math.max(0, botPending.size() - visible);
            botScroll = Math.max(0, Math.min(maxScroll, botScroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
