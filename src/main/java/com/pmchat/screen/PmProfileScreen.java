package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Профиль игрока (4.2 / 4.5). Свой профиль: день рождения, описание, роль.
 * Чужой: роль (назначается вручную), кнопка чёрного списка (5.5). Меню
 * профиля есть всегда — даже без бэкенда.
 */
@Environment(EnvType.CLIENT)
public class PmProfileScreen extends Screen {

    /** Не static final — подгоняется под размер экрана в init() (GUI Scale 4 и т.п.). */
    private int PANEL_W = 250;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();
    private final String player;   // ник просматриваемого игрока
    private final boolean self;

    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, SUBTLE;

    private int px, py, panelH;
    private EditBox birthdayField;
    private EditBox descField;
    private EditBox aliasField;
    private EditBox noteField;

    public PmProfileScreen(Screen parent, String player) {
        super(Component.translatable("pmchat.profile.title"));
        this.parent = parent;
        String me = PmChatClient.selfName();
        // Служебные ключи вкладок (§global, §bc:…, §grp:… и т.п.) — не ники:
        // такой «профиль» показывать нечего, и раньше он молча подменялся своим.
        String nick = player == null ? "" : player.trim();
        if (nick.startsWith("§")) nick = "";
        this.self = nick.isBlank() || nick.equalsIgnoreCase(me);
        this.player = self ? me : nick;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
        SUBTLE = PmTheme.isLight(config.theme) ? 0xFF6A737A : 0xFF808A90;
    }

    @Override
    protected void init() {
        applyTheme();
        clearWidgets();
        PANEL_W = Math.max(160, Math.min(250, width - 24));
        // +34 под строку «Публикации → Открыть» у самого низа панели (см. renderPostsLink).
        panelH = Math.min((self ? 214 : 236) + 34, height - 24);
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        int contentY = py + 84;

        // Роль-должность определяется автоматически из ника (префикс/суффикс),
        // вручную не выставляется — строка роли рисуется в render() только для
        // чтения. Резервируем её высоту.
        contentY += 21;

        if (self) {
            // День рождения — редактируемое поле
            birthdayField = new EditBox(font, px + PANEL_W - 108, contentY, 100, 15,
                    Component.translatable("pmchat.profile.birthday"));
            birthdayField.setMaxLength(24);
            birthdayField.setValue(config.profileBirthday == null ? "" : config.profileBirthday);
            String bh = Component.translatable("pmchat.profile.birthday.hint").getString();
            birthdayField.setSuggestion(birthdayField.getValue().isEmpty() ? bh : "");
            birthdayField.setResponder(s -> birthdayField.setSuggestion(s.isEmpty() ? bh : ""));
            addRenderableWidget(birthdayField);
            contentY += 21;

            // О себе — редактируемое поле на всю ширину (следующая строка под подписью)
            descField = new EditBox(font, px + 12, contentY + 12, PANEL_W - 24, 15,
                    Component.translatable("pmchat.profile.desc"));
            descField.setMaxLength(120);
            descField.setValue(config.profileDescription == null ? "" : config.profileDescription);
            String dh = Component.translatable("pmchat.profile.desc.hint").getString();
            descField.setSuggestion(descField.getValue().isEmpty() ? dh : "");
            descField.setResponder(s -> descField.setSuggestion(s.isEmpty() ? dh : ""));
            addRenderableWidget(descField);
            contentY += 33;
        } else {
            // Переименование игрока (алиас) — задаётся здесь, добавляет в контакты
            aliasField = new EditBox(font, px + PANEL_W - 108, contentY, 100, 15,
                    Component.translatable("pmchat.profile.rename"));
            aliasField.setMaxLength(24);
            aliasField.setValue(config.hasAlias(player) ? config.aliasOf(player) : "");
            String rh = Component.translatable("pmchat.profile.rename.hint").getString();
            aliasField.setSuggestion(aliasField.getValue().isEmpty() ? rh : "");
            aliasField.setResponder(s -> aliasField.setSuggestion(s.isEmpty() ? rh : ""));
            addRenderableWidget(aliasField);
            contentY += 21;

            // Кнопка ЧС (5.5) + «Пожаловаться» рядом — официальный аккаунт PocketChat
            // заблокировать нельзя (иначе теряются рассылки/уведомления администрации).
            com.pmchat.client.PmBackend.AccountInfo blockAcc = com.pmchat.client.PmBackend.isConfigured()
                    ? com.pmchat.client.PmBackend.cachedAccountInfo(player) : null;
            boolean officialAccount = blockAcc != null && blockAcc.official;
            boolean blocked = config.isBlocked(player);
            int halfW = (PANEL_W - 24 - 6) / 2;
            if (!officialAccount) {
                addRenderableWidget(FlatButton.centered(font, px + 12, contentY, halfW, 16,
                        Component.translatable(blocked ? "pmchat.profile.unblock" : "pmchat.profile.block"),
                        blocked ? 0xFF5A2A22 : BTN_BG, blocked ? 0xFF6E332A : BTN_HOVER,
                        blocked ? 0xFFA0463A : BTN_BORDER, 0xFFE07A6A, btn -> {
                            PmChatClient.toggleBlocked(player);
                            reinit();
                        }));
            }
            addRenderableWidget(FlatButton.centered(font, officialAccount ? px + 12 : px + 12 + halfW + 6,
                    contentY, officialAccount ? PANEL_W - 24 : halfW, 16,
                    Component.translatable("pmchat.report.open"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFFE0B040,
                    btn -> Minecraft.getInstance().gui.setScreen(new PmReportScreen(this, player))));
            contentY += 22;

            // Личная заметка (4.2+) — как в Discord: видна только тебе, хранится
            // только в pmchat.json, никогда не отправляется собеседнику.
            noteField = new EditBox(font, px + 12, contentY + 12, PANEL_W - 24, 15,
                    Component.translatable("pmchat.profile.note"));
            noteField.setMaxLength(200);
            noteField.setValue(config.noteOf(player));
            String nh = Component.translatable("pmchat.profile.note.hint").getString();
            noteField.setSuggestion(noteField.getValue().isEmpty() ? nh : "");
            noteField.setResponder(s -> noteField.setSuggestion(s.isEmpty() ? nh : ""));
            addRenderableWidget(noteField);
            contentY += 33;
        }

        // Кнопка «Готово»
        addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 40, py + panelH - 24, 80, 18,
                Component.translatable("pmchat.profile.done"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> onClose()));
    }

    private void reinit() {
        // сохраняем правки полей перед пересборкой
        persistFields();
        init();
    }

    private void persistFields() {
        if (birthdayField != null) config.profileBirthday = birthdayField.getValue().trim();
        if (descField != null) config.profileDescription = descField.getValue().trim();
        if (aliasField != null) config.setAlias(player, aliasField.getValue());
        if (noteField != null) config.setNote(player, noteField.getValue());
        config.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.outline(px, py, PANEL_W, panelH, BORDER);

        // Заголовок однозначно показывает, чей это профиль (свой или ник игрока)
        String titleStr = self
                ? Component.translatable("pmchat.profile.self").getString()
                : trimTo(player, PANEL_W - 40);
        Component title = Component.literal(titleStr);
        context.text(font, title,
                px + (PANEL_W - font.width(title)) / 2, py + 8, TITLE, false);

        // ---- Шапка: аватар + ник + роль + статус ----
        int avX = px + 14, avY = py + 26, avS = 44;
        drawAvatar(context, avX, avY, avS);

        int tx = avX + avS + 12;
        // Должность: назначенная админом перекрывает встроенную (по серверному нику);
        // отображаем локальный псевдоним, если задан
        PmRoles.Effective roleEff = PmRoles.effective(config, player);
        net.minecraft.network.chat.Component fullName = config.hasAlias(player)
                ? Component.literal(config.aliasOf(player))
                : PmNames.displayText(player);
        int nameX = tx;
        String icon = roleEff.icon;
        // Значок роли рисуем ОТДЕЛЬНО только когда показываем псевдоним (у него нет
        // префикса). У серверного ника роль уже есть в самом префиксе — иначе дубль.
        if (config.hasAlias(player) && !icon.isEmpty()) {
            context.text(font, icon, nameX, py + 30, roleEff.color, false);
            nameX += font.width(icon) + 4;
        }
        int nameMax = px + PANEL_W - 10 - nameX;
        String nameDrawn;
        if (font.width(fullName) <= nameMax) {
            context.text(font, fullName, nameX, py + 30, TITLE, false);
            nameDrawn = fullName.getString();
        } else {
            nameDrawn = trimTo(fullName.getString(), nameMax);
            context.text(font, nameDrawn, nameX, py + 30, TITLE, false);
        }
        // Галочка верификации / официальный аккаунт (см. PmBackend, server-pocketchat)
        if (com.pmchat.client.PmBackend.isConfigured()) {
            com.pmchat.client.PmBackend.AccountInfo acc = com.pmchat.client.PmBackend.cachedAccountInfo(player);
            if (acc != null && (acc.verified || acc.official)) {
                PmScreen.drawVerifiedBadge(context, font, nameX + font.width(nameDrawn) + 3, py + 29);
            }
        }

        // Статус «был(а) в сети»: если есть бэкенд — кросс-серверный (по последнему
        // пингу присутствия), иначе — по таб-листу текущего Minecraft-сервера.
        com.pmchat.client.PmBackend.AccountInfo backendAcc = com.pmchat.client.PmBackend.isConfigured()
                ? com.pmchat.client.PmBackend.cachedAccountInfo(player) : null;
        if (!self && backendAcc != null && backendAcc.official) {
            context.text(font, Component.translatable("pmchat.official.notice"), tx, py + 44, SUBTLE, false);
        } else if (self || backendAcc == null || backendAcc.lastSeenAt <= 0) {
            boolean online = self || onlineEntry() != null;
            context.text(font, Component.translatable(online ? "pmchat.profile.online" : "pmchat.profile.offline"),
                    tx, py + 44, online ? 0xFF6FBF8B : SUBTLE, false);
        } else {
            boolean precise = config.preciseLastSeen && backendAcc.sharePrecise;
            net.minecraft.network.chat.Component status = com.pmchat.client.PmBackend.humanizeLastSeen(backendAcc.lastSeenAt, precise);
            boolean isOnline = System.currentTimeMillis() - backendAcc.lastSeenAt < 90_000L;
            context.text(font, status, tx, py + 44, isOnline ? 0xFF6FBF8B : SUBTLE, false);
        }

        // ---- Подписи полей ----
        int contentY = py + 84;
        context.text(font, Component.translatable("pmchat.profile.role"), px + 12, contentY + 4, LABEL, false);
        // Значение роли — только для чтения здесь (назначается в админ-панели или
        // определяется из ника автоматически)
        Component roleVal = Component.literal((icon.isEmpty() ? "" : icon + " ") + roleEff.label);
        context.text(font, roleVal,
                px + PANEL_W - 12 - font.width(roleVal), contentY + 4,
                roleEff.none ? SUBTLE : roleEff.color, false);
        contentY += 21;
        if (self) {
            context.text(font, Component.translatable("pmchat.profile.birthday"),
                    px + 12, contentY + 4, LABEL, false);
            contentY += 21;
            context.text(font, Component.translatable("pmchat.profile.desc"),
                    px + 12, contentY, LABEL, false);
            contentY += 33;
        } else {
            // Подпись поля переименования + место под кнопку ЧС
            context.text(font, Component.translatable("pmchat.profile.rename"),
                    px + 12, contentY + 4, LABEL, false);
            contentY += 21;
            contentY += 22;
            context.text(font, Component.translatable("pmchat.profile.note"),
                    px + 12, contentY, LABEL, false);
            contentY += 33;
        }

        // ---- Публикации на страничке (стена) — открываются отдельным окном,
        // список может расти и не влезает в компактный профиль.
        renderPostsLink(context, mouseX, mouseY);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private int[] openPostsRect = null;

    private void renderPostsLink(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Готово-кнопка занимает py+panelH-24..py+panelH-6 — оставляем зазор над ней.
        int top = py + panelH - 42;
        context.fill(px + 8, top, px + PANEL_W - 8, top + 1, BORDER);
        Component postsTitle = Component.translatable("pmchat.posts.section");
        boolean hover = mouseX >= px + 12 && mouseX < px + 12 + font.width(postsTitle) + 60
                && mouseY >= top + 4 && mouseY < top + 15;
        context.text(font, postsTitle, px + 12, top + 5, hover ? VALUE : TITLE, false);
        Component openHint = Component.translatable("pmchat.posts.openhint");
        context.text(font, openHint, px + 12 + font.width(postsTitle) + 6, top + 5, SUBTLE, false);
        openPostsRect = new int[]{px + 8, top + 4, font.width(postsTitle) + font.width(openHint) + 20, 11};
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (openPostsRect != null) {
            int rx = openPostsRect[0], ry = openPostsRect[1], rw = openPostsRect[2], rh = openPostsRect[3];
            if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                Minecraft.getInstance().gui.setScreen(new PmProfilePostsScreen(this, player));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private String trimTo(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private PlayerInfo onlineEntry() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null ? mc.getConnection().getPlayerInfo(player) : null;
    }

    private void drawAvatar(GuiGraphicsExtractor context, int x, int y, int size) {
        // У заблокированного (5.5) скрываем аватарку даже онлайн
        if (!self && config.isBlocked(player)) {
            fillCircle(context, x + size / 2, y + size / 2, (size + 1) / 2, 0xFF3A3F44);
            context.text(font, "⊘",
                    x + size / 2 - font.width("⊘") / 2, y + size / 2 - 4, 0xFF8A9096, false);
            return;
        }
        if (com.pmchat.client.PmBackend.isConfigured()) {
            com.pmchat.client.PmBackend.AccountInfo acc = com.pmchat.client.PmBackend.cachedAccountInfo(player);
            if (acc != null && acc.official && com.pmchat.client.PmOfficialIcon.isReady()) {
                com.pmchat.client.PmOfficialIcon.draw(context, x, y, size);
                return;
            }
        }
        PlayerInfo entry = onlineEntry();
        if (entry != null && entry.getSkin() != null) {
            try {
                PlayerFaceExtractor.extractRenderState(context, entry.getSkin(), x, y, size);
                return;
            } catch (Throwable ignored) {
            }
        }
        int bg = 0xFF000000 | (player.hashCode() & 0xFFFFFF);
        fillCircle(context, x + size / 2, y + size / 2, (size + 1) / 2, bg);
        String letter = player.isEmpty() ? "?" : player.substring(0, 1).toUpperCase(Locale.ROOT);
        context.text(font, letter,
                x + size / 2 - font.width(letter) / 2, y + size / 2 - 4, 0xFFFFFFFF, false);
    }

    private static void fillCircle(GuiGraphicsExtractor ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) r * r - dy * dy);
            ctx.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
        }
    }

    @Override
    public void onClose() {
        persistFields();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
