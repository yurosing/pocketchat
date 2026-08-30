package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmImages;
import com.pmchat.client.PmPalettes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Экран настроек мода: открывается кнопкой ⚙ в чате и из Mod Menu.
 * Разбит на вкладки-категории (см. {@link #TAB_KEYS}), внутри вкладки —
 * строки-параметры: подпись + кнопка-значение, клик перебирает варианты,
 * всё применяется и сохраняется сразу.
 */
@Environment(EnvType.CLIENT)
public class PmSettingsScreen extends Screen {

    private static final int ROW_H = 17;
    private static final int TAB_H = 16;

    /** Не static final — подгоняется под размер экрана в init() (GUI Scale 4 и т.п.). */
    private int PANEL_W = 280;

    private static final String[] TAB_KEYS = {
            "pmchat.settings.tab.appearance",
            "pmchat.settings.tab.chat",
            "pmchat.settings.tab.sound",
            "pmchat.settings.tab.account",
            "pmchat.settings.tab.privacy",
    };

    // Тема применяется в init() до построения строк (см. applyTheme)
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    private static final int[] SCALES = {80, 90, 100, 110, 125};
    private static final int[] VOLUMES = {25, 50, 75, 100};

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py, panelH;
    private static int lastTab = 0;
    private int tab = lastTab;

    public PmSettingsScreen(Screen parent) {
        super(Component.translatable("pmchat.settings.title"));
        this.parent = parent;
    }

    private boolean isAdminAccount() {
        return PmChatClient.canOpenAdminPanel();
    }

    private boolean backendConfigured() {
        return config.backendUrl != null && !config.backendUrl.isBlank();
    }

    /** Сколько строк займёт текущая вкладка — панель подстраивается под неё, а не под самую длинную. */
    private int rowsForTab(int t) {
        return switch (t) {
            case 1 -> 10;
            case 2 -> 4;
            case 3 -> backendConfigured() && !editBackendUrl
                    ? (4 + (isAdminAccount() ? 1 : 0)) : 5;
            case 4 -> 1 + (backendConfigured() && com.pmchat.client.PmBackend.hasAccount()
                    ? (com.pmchat.client.PmBackend.hasActiveFeature("paid_dm") ? 2 : 3) : 0);
            default -> 11;
        };
    }

    private EditBox backendUrlField;
    private boolean editBackendUrl = false;

    @Override
    protected void init() {
        applyTheme();
        optionLabels.clear();
        clearWidgets();
        lastTab = tab;

        int rows = Math.max(1, rowsForTab(tab));
        PANEL_W = Math.max(200, Math.min(280, width - 24));
        panelH = Math.min(26 + TAB_H + rows * ROW_H + 28, height - 24);
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        // Вкладки
        int tabW = PANEL_W / TAB_KEYS.length;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int ti = i;
            boolean active = ti == tab;
            addRenderableWidget(FlatButton.centered(font, px + i * tabW + 1, py + 22, tabW - 2, TAB_H - 2,
                    Component.translatable(TAB_KEYS[i]), active ? BTN_HOVER : BTN_BG, BTN_HOVER, BTN_BORDER,
                    active ? VALUE : LABEL, btn -> { tab = ti; init(); }));
        }

        int y = py + 22 + TAB_H + 4;

        switch (tab) {
            case 0 -> y = buildAppearanceTab(y);
            case 1 -> y = buildChatTab(y);
            case 2 -> y = buildSoundTab(y);
            case 3 -> y = buildAccountTab(y);
            case 4 -> y = buildPrivacyTab(y);
            default -> { }
        }

        addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 40, py + panelH - 24, 80, 18,
                Component.translatable("pmchat.settings.done"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> onClose()));

        // кнопка-ссылка на сайт документации (открывает RU/EN по языку клиента)
        FlatButton docsBtn = FlatButton.centered(font, px + PANEL_W - 24, py + 3, 18, 14,
                Component.translatable("pmchat.tip.docs"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFF9CC4DC,
                btn -> PmChatClient.openDocs()).withIcon(PmIcons.DOCS);
        docsBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("pmchat.tip.docs")));
        addRenderableWidget(docsBtn);
    }

    private int buildAppearanceTab(int y) {
        y = addOption(y, "pmchat.set.theme",
                () -> Component.translatable(PmTheme.nameKey(config.theme)),
                VALUE, () -> config.theme = (config.theme + 1) % PmTheme.COUNT);

        y = addOption(y, "pmchat.set.outcolor",
                () -> Component.literal("■ " + (config.outColor % PmPalettes.OUT.length + 1)),
                () -> PmPalettes.OUT[Math.floorMod(config.outColor, PmPalettes.OUT.length)],
                () -> config.outColor = (config.outColor + 1) % PmPalettes.OUT.length);

        y = addOption(y, "pmchat.set.incolor",
                () -> Component.literal("■ " + (config.inColor % PmPalettes.IN.length + 1)),
                () -> PmPalettes.IN[Math.floorMod(config.inColor, PmPalettes.IN.length)],
                () -> config.inColor = (config.inColor + 1) % PmPalettes.IN.length);

        y = addOption(y, "pmchat.set.names",
                () -> Component.translatable(config.uniformNames ? "pmchat.set.names.uniform" : "pmchat.set.names.rainbow"),
                VALUE, () -> config.uniformNames = !config.uniformNames);

        y = addOption(y, "pmchat.set.namecolor",
                () -> Component.literal("■ " + (config.nameColor % PmPalettes.NAMES.length + 1)),
                () -> PmPalettes.NAMES[Math.floorMod(config.nameColor, PmPalettes.NAMES.length)],
                () -> config.nameColor = (config.nameColor + 1) % PmPalettes.NAMES.length);

        y = addOption(y, "pmchat.set.msgtextcolor",
                () -> Component.literal(config.msgTextColor == 0
                        ? Component.translatable("pmchat.set.wallpaper.none").getString()
                        : "■ " + config.msgTextColor),
                () -> {
                    int c = PmPalettes.MSG_TEXT[Math.floorMod(config.msgTextColor, PmPalettes.MSG_TEXT.length)];
                    return c == 0 ? VALUE : c;
                },
                () -> config.msgTextColor = (config.msgTextColor + 1) % PmPalettes.MSG_TEXT.length);

        y = addOption(y, "pmchat.set.textscale",
                () -> Component.literal(config.textScalePct + "%"),
                VALUE, () -> config.textScalePct = SCALES[(indexOf(SCALES, config.textScalePct) + 1) % SCALES.length]);

        y = addOption(y, "pmchat.set.uiscale",
                () -> Component.literal(switch (Math.floorMod(config.uiScale, 3)) {
                    case 1 -> "M";
                    case 2 -> "L";
                    default -> "S";
                }),
                VALUE, () -> config.uiScale = (config.uiScale + 1) % 3);

        y = addOption(y, "pmchat.set.wallpaper",
                () -> Component.literal(config.wallpaper == null || config.wallpaper.isBlank()
                        ? Component.translatable("pmchat.set.wallpaper.none").getString()
                        : config.wallpaper.length() > 12 ? config.wallpaper.substring(0, 11) + "…" : config.wallpaper),
                VALUE, this::cycleWallpaper);

        y = addOption(y, "pmchat.set.badge",
                () -> Component.literal("■ " + (config.badgeColor % PmPalettes.BADGE.length + 1)),
                () -> PmPalettes.BADGE[Math.floorMod(config.badgeColor, PmPalettes.BADGE.length)],
                () -> config.badgeColor = (config.badgeColor + 1) % PmPalettes.BADGE.length);

        y = addOption(y, "pmchat.set.contactstar",
                () -> Component.literal("★ " + (config.contactStarColor % PmPalettes.CONTACT_STAR.length + 1)),
                () -> PmPalettes.CONTACT_STAR[Math.floorMod(config.contactStarColor, PmPalettes.CONTACT_STAR.length)],
                () -> config.contactStarColor = (config.contactStarColor + 1) % PmPalettes.CONTACT_STAR.length);

        return y;
    }

    private int buildChatTab(int y) {
        y = addOption(y, "pmchat.set.mention",
                () -> Component.translatable(config.mentionEnabled ? "pmchat.set.mention.on" : "pmchat.set.mention.off"),
                () -> config.mentionEnabled ? 0xFFF0C34E : VALUE,
                () -> config.mentionEnabled = !config.mentionEnabled);

        y = addOption(y, "pmchat.set.dnd",
                () -> Component.translatable(config.dnd ? "pmchat.set.dnd.on" : "pmchat.set.dnd.off"),
                () -> config.dnd ? 0xFFE07A6A : 0xFF8FD8A8,
                () -> config.dnd = !config.dnd);

        y = addOption(y, "pmchat.set.globalprefix",
                () -> Component.literal(config.globalPrefix == null || config.globalPrefix.isBlank()
                        ? Component.translatable("pmchat.set.globalprefix.none").getString()
                        : config.globalPrefix),
                VALUE, () -> {
                    String[] cycle = {"!", "@", "."};
                    String cur = config.globalPrefix == null ? "" : config.globalPrefix;
                    int idx = -1;
                    for (int i = 0; i < cycle.length; i++) if (cycle[i].equals(cur)) idx = i;
                    if (idx < 0) config.globalPrefix = cycle[0];
                    else if (idx == cycle.length - 1) config.globalPrefix = "";
                    else config.globalPrefix = cycle[idx + 1];
                });

        y = addOption(y, "pmchat.set.closedmg",
                () -> Component.translatable(config.closeOnDamage ? "pmchat.set.on" : "pmchat.set.off"),
                () -> config.closeOnDamage ? 0xFFE07A6A : VALUE,
                () -> config.closeOnDamage = !config.closeOnDamage);

        y = addOption(y, "pmchat.set.copynick",
                () -> Component.translatable(config.mentionOnCopy ? "pmchat.set.on" : "pmchat.set.off"),
                () -> config.mentionOnCopy ? 0xFF8FD8A8 : VALUE,
                () -> config.mentionOnCopy = !config.mentionOnCopy);

        // При заданном переименовании — слать /m на псевдоним, а не на реальный ник
        y = addOption(y, "pmchat.set.aliastarget",
                () -> Component.translatable(config.aliasAsTarget ? "pmchat.set.on" : "pmchat.set.off"),
                () -> config.aliasAsTarget ? 0xFF8FD8A8 : VALUE,
                () -> config.aliasAsTarget = !config.aliasAsTarget);

        y = addOption(y, "pmchat.set.staff",
                () -> Component.translatable(config.staffFeatures ? "pmchat.set.on" : "pmchat.set.off"),
                () -> config.staffFeatures ? 0xFFE07A6A : VALUE,
                () -> config.staffFeatures = !config.staffFeatures);

        y = addOption(y, "pmchat.set.coreprotect",
                () -> Component.translatable(config.coreProtectEnabled ? "pmchat.set.on" : "pmchat.set.off"),
                () -> config.coreProtectEnabled ? 0xFF8FD8A8 : VALUE,
                () -> config.coreProtectEnabled = !config.coreProtectEnabled);

        y = addOption(y, "pmchat.set.mediabar",
                () -> Component.translatable(config.mediaBarWhileTyping ? "pmchat.set.on" : "pmchat.set.off"),
                () -> config.mediaBarWhileTyping ? 0xFF8FD8A8 : VALUE,
                () -> config.mediaBarWhileTyping = !config.mediaBarWhileTyping);

        // Отдельный экран фильтров чата («No Global Chat»)
        y = addOption(y, "pmchat.filters.open",
                () -> Component.literal("⚙"),
                () -> 0xFF8FD8A8,
                () -> Minecraft.getInstance().gui.setScreen(new PmFiltersScreen(this)));

        return y;
    }

    private int buildSoundTab(int y) {
        y = addOption(y, "pmchat.set.sound",
                () -> Component.translatable("pmchat.set.sound." + Math.floorMod(config.notifySound, 4)),
                VALUE, () -> {
                    config.notifySound = (config.notifySound + 1) % 4;
                    PmChatClient.playNotifySound(Minecraft.getInstance()); // предпрослушка
                });

        y = addOption(y, "pmchat.set.volume",
                () -> Component.literal(config.notifyVolume + "%"),
                VALUE, () -> {
                    config.notifyVolume = VOLUMES[(indexOf(VOLUMES, config.notifyVolume) + 1) % VOLUMES.length];
                    PmChatClient.playNotifySound(Minecraft.getInstance());
                });

        y = addOption(y, "pmchat.set.tts",
                () -> Component.translatable(config.ttsGlobal ? "pmchat.set.tts.on" : "pmchat.set.tts.off"),
                () -> config.ttsGlobal ? 0xFF8FD8A8 : VALUE,
                () -> {
                    config.ttsGlobal = !config.ttsGlobal;
                    if (config.ttsGlobal) {
                        PmChatClient.speak(Component.translatable("pmchat.set.tts.preview").getString());
                    }
                });

        y = addOption(y, "pmchat.set.sttlang",
                () -> Component.translatable(config.sttLang == 1 ? "pmchat.set.sttlang.en" : "pmchat.set.sttlang.ru"),
                VALUE, () -> {
                    config.sttLang = config.sttLang == 1 ? 0 : 1;
                    com.pmchat.client.PmStt.onLanguageChanged();
                });

        return y;
    }

    private int buildAccountTab(int y) {
        y = addOption(y, "pmchat.rules.view",
                () -> Component.literal("⚙"),
                () -> LABEL,
                () -> Minecraft.getInstance().gui.setScreen(new PmRulesScreen(this)));

        if (!backendConfigured() || editBackendUrl) {
            int fx = px + 16;
            int fw = PANEL_W - 32;
            optionLabels.add(new Object[]{"pmchat.settings.tab.account.none", y});
            y += 12;

            String hint = Component.translatable("pmchat.settings.backendurl.hint").getString();
            backendUrlField = new EditBox(font, fx, y, fw, 16, Component.translatable("pmchat.settings.backendurl.hint"));
            backendUrlField.setMaxLength(200);
            if (backendConfigured()) {
                backendUrlField.setValue(config.backendUrl);
            } else {
                backendUrlField.setSuggestion(hint);
                backendUrlField.setResponder(s -> backendUrlField.setSuggestion(s.isEmpty() ? hint : null));
            }
            addRenderableWidget(backendUrlField);
            y += 20;

            addRenderableWidget(FlatButton.centered(font, fx, y, fw, 16,
                    Component.translatable("pmchat.settings.backendurl.connect"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                    btn -> {
                        String url = backendUrlField.getValue().trim();
                        if (!url.isEmpty()) {
                            if (!url.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
                                url = "http://" + url;
                            }
                            config.backendUrl = url;
                            config.save();
                            editBackendUrl = false;
                            reinit();
                        }
                    }));
            y += 20;
            return y;
        }

        y = addOption(y, "pmchat.login.open",
                () -> Component.literal("⚙"),
                () -> 0xFF8FD8A8,
                () -> Minecraft.getInstance().gui.setScreen(new PmLoginScreen(this)));

        if (isAdminAccount()) {
            y = addOption(y, "pmchat.admin.open",
                    () -> Component.literal("⚙"),
                    () -> 0xFFF0C34E,
                    () -> Minecraft.getInstance().gui.setScreen(new PmAdminScreen(this)));
        }

        y = addOption(y, "pmchat.support.open",
                () -> Component.literal("✉"),
                () -> 0xFF5AA0E0,
                () -> Minecraft.getInstance().gui.setScreen(new PmSupportScreen(this)));

        y = addOption(y, "pmchat.settings.backendurl.change",
                () -> Component.literal("⚙"),
                () -> LABEL,
                () -> editBackendUrl = true);

        return y;
    }

    private EditBox dmPriceField;
    private Component dmPriceStatus = Component.empty();
    private int dmPriceStatusColor = 0xFFAAAAAA;

    private int buildPrivacyTab(int y) {
        y = addOption(y, "pmchat.set.preciseseen",
                () -> Component.translatable(config.preciseLastSeen ? "pmchat.set.on" : "pmchat.set.off"),
                () -> config.preciseLastSeen ? 0xFF8FD8A8 : VALUE,
                () -> {
                    config.preciseLastSeen = !config.preciseLastSeen;
                    if (backendConfigured()) {
                        com.pmchat.client.PmBackend.setPrecisePresence(config.preciseLastSeen, null);
                    }
                });

        if (backendConfigured() && com.pmchat.client.PmBackend.hasAccount()) {
            y += 4;
            optionLabels.add(new Object[]{"pmchat.privacy.dmprice.section", y});
            y += 12;
            int fx = px + 12, fw = PANEL_W - 24;
            if (com.pmchat.client.PmBackend.hasActiveFeature("paid_dm")) {
                dmPriceField = new EditBox(font, fx, y, fw - 62, 16, Component.translatable("pmchat.shop.dmprice.hint"));
                dmPriceField.setMaxLength(8);
                com.pmchat.client.PmBackend.AccountInfo self =
                        com.pmchat.client.PmBackend.cachedAccountInfo(PmChatClient.selfName());
                dmPriceField.setValue(self != null ? String.valueOf(self.dmPrice) : "");
                String hint = Component.translatable("pmchat.shop.dmprice.hint").getString();
                dmPriceField.setSuggestion(dmPriceField.getValue().isEmpty() ? hint : "");
                dmPriceField.setResponder(s -> dmPriceField.setSuggestion(s.isEmpty() ? hint : ""));
                addRenderableWidget(dmPriceField);
                addRenderableWidget(FlatButton.centered(font, fx + fw - 58, y, 58, 16,
                        Component.translatable("pmchat.shop.dmprice.save"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                        btn -> saveDmPrice()));
                y += 20;
            } else {
                optionLabels.add(new Object[]{"pmchat.privacy.dmprice.needshop", y});
                y += 12;
                addRenderableWidget(FlatButton.centered(font, fx, y, fw, 16,
                        Component.translatable("pmchat.tip.shop"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFFF0C34E,
                        btn -> Minecraft.getInstance().gui.setScreen(new PmShopScreen(this))));
                y += 20;
            }
        }
        return y;
    }

    private void saveDmPrice() {
        long price;
        try {
            price = Long.parseLong(dmPriceField.getValue().trim());
        } catch (NumberFormatException e) {
            dmPriceStatus = Component.translatable("pmchat.admin.badamount");
            dmPriceStatusColor = 0xFFE07A6A;
            return;
        }
        if (price < 0) return;
        com.pmchat.client.PmBackend.setDmPrice(price, (ok, v, err) -> {
            dmPriceStatus = ok ? Component.translatable("pmchat.admin.ok") : Component.translatable("pmchat.admin.fail", String.valueOf(err));
            dmPriceStatusColor = ok ? 0xFF8FD8A8 : 0xFFE07A6A;
        });
    }

    private interface ValueSupplier {
        Component get();
    }

    private interface ColorSupplier {
        int get();
    }

    private int addOption(int y, String labelKey, ValueSupplier value, int valueColor, Runnable cycle) {
        return addOption(y, labelKey, value, () -> valueColor, cycle);
    }

    /** Строка настройки: подпись + кнопка-значение, клик перебирает варианты. */
    private int addOption(int y, String labelKey, ValueSupplier value, ColorSupplier color, Runnable cycle) {
        FlatButton button = FlatButton.centered(font, px + PANEL_W - 92, y, 84, 14,
                value.get(), BTN_BG, BTN_HOVER, BTN_BORDER, color.get(), btn -> {
                    cycle.run();
                    config.save();
                    // Пересоздаём экран, чтобы обновить подписи и цвета кнопок
                    reinit();
                });
        addRenderableWidget(button);
        optionLabels.add(new Object[]{labelKey, y});
        return y + ROW_H;
    }

    private final java.util.List<Object[]> optionLabels = new java.util.ArrayList<>();

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.outline(px, py, PANEL_W, panelH, BORDER);

        Component title = Component.translatable("pmchat.settings.title");
        context.text(font, title,
                px + (PANEL_W - font.width(title)) / 2, py + 8, TITLE, false);

        for (Object[] entry : optionLabels) {
            context.text(font, Component.translatable((String) entry[0]),
                    px + 10, (int) entry[1] + 3, LABEL, false);
        }

        if (tab == 4 && dmPriceField != null && !dmPriceStatus.getString().isEmpty()) {
            context.text(font, dmPriceStatus, px + 12, py + panelH - 40, dmPriceStatusColor, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void reinit() {
        init();
    }

    /** Перебирает обои: none → файлы из config/pmchat-wallpapers/. */
    private void cycleWallpaper() {
        java.util.List<String> files = new java.util.ArrayList<>();
        files.add(""); // "нет"
        try (var stream = java.nio.file.Files.list(PmScreen.wallpapersDir())) {
            stream.filter(java.nio.file.Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> {
                        String l = n.toLowerCase(java.util.Locale.ROOT);
                        return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".gif");
                    })
                    .sorted()
                    .forEach(files::add);
        } catch (Exception ignored) {
        }
        String cur = config.wallpaper == null ? "" : config.wallpaper;
        int idx = files.indexOf(cur);
        config.wallpaper = files.get((idx + 1 + files.size()) % files.size());
        PmImages.forgetLocal(config.wallpaper);
    }

    private static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) return i;
        }
        return 0;
    }

    @Override
    public void onClose() {
        config.save();
        Minecraft client = Minecraft.getInstance();
        // Возвращаемся в чат с уже применёнными настройками
        client.gui.setScreen(parent instanceof PmScreen ? new PmScreen() : parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
