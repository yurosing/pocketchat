package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Профиль игрока (4.2 / 4.5). Свой профиль: день рождения, описание, роль,
 * баланс и раздел подарков (как в Telegram). Чужой: роль (назначается вручную),
 * кнопка чёрного списка (5.5). Меню профиля есть всегда — даже без плагина;
 * подарки за монеты Vault активны только когда серверный плагин доступен.
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
    private TextFieldWidget birthdayField;
    private TextFieldWidget descField;
    private TextFieldWidget aliasField;
    private TextFieldWidget noteField;

    public PmProfileScreen(Screen parent, String player) {
        super(Text.translatable("pmchat.profile.title"));
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

    private boolean pluginPresent() {
        return com.pmchat.client.PmServerMedia.get().isAvailable();
    }

    /** Альтернативный путь подарков/баланса через server-pocketchat, без Paper-плагина. */
    private boolean backendGiftsAvailable() {
        return com.pmchat.client.PmBackend.isConfigured() && com.pmchat.client.PmBackend.hasAccount();
    }

    @Override
    protected void init() {
        applyTheme();
        clearChildren();
        // Высота панели для чужого профиля: каталог подарков через бэкенд теперь
        // открывается отдельным прокручиваемым окном (PmGiftsScreen), а не рисуется
        // тут же — только устаревший путь через плагин сервера всё ещё встроен
        // прямо в профиль и резервирует под себя место по числу строк.
        int catalogRows = 3;
        if (!self && pluginPresent()) {
            int catalogSize = com.pmchat.client.PmServerMedia.get().giftCatalog().size();
            if (catalogSize > 0) catalogRows = (catalogSize + 3) / 4;
        }
        PANEL_W = Math.max(160, Math.min(250, width - 24));
        int coinsRowH = !self && backendGiftsAvailable() ? 22 : 0;
        // +34 под строку «Публикации → Открыть» у самого низа панели (см. renderPostsLink) —
        // с запасом, чтобы не наехать на хвост раздела подарков выше.
        panelH = Math.min((self ? 214 : 260 + coinsRowH + catalogRows * 19) + 34, height - 24);
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        // Подтягиваем каталог/баланс и полученные подарки текущего игрока
        if (pluginPresent()) {
            com.pmchat.client.PmServerMedia sm = com.pmchat.client.PmServerMedia.get();
            sm.requestGifts();
            sm.requestGiftInventory(player);
        }

        int contentY = py + 84;

        // Роль-должность определяется автоматически из ника (префикс/суффикс),
        // вручную не выставляется — строка роли рисуется в render() только для
        // чтения. Резервируем её высоту.
        contentY += 21;

        if (self) {
            // День рождения — редактируемое поле
            birthdayField = new TextFieldWidget(textRenderer, px + PANEL_W - 108, contentY, 100, 15,
                    Text.translatable("pmchat.profile.birthday"));
            birthdayField.setMaxLength(24);
            birthdayField.setText(config.profileBirthday == null ? "" : config.profileBirthday);
            String bh = Text.translatable("pmchat.profile.birthday.hint").getString();
            birthdayField.setSuggestion(birthdayField.getText().isEmpty() ? bh : "");
            birthdayField.setChangedListener(s -> birthdayField.setSuggestion(s.isEmpty() ? bh : ""));
            addDrawableChild(birthdayField);
            contentY += 21;

            // О себе — редактируемое поле на всю ширину (следующая строка под подписью)
            descField = new TextFieldWidget(textRenderer, px + 12, contentY + 12, PANEL_W - 24, 15,
                    Text.translatable("pmchat.profile.desc"));
            descField.setMaxLength(120);
            descField.setText(config.profileDescription == null ? "" : config.profileDescription);
            String dh = Text.translatable("pmchat.profile.desc.hint").getString();
            descField.setSuggestion(descField.getText().isEmpty() ? dh : "");
            descField.setChangedListener(s -> descField.setSuggestion(s.isEmpty() ? dh : ""));
            addDrawableChild(descField);
            contentY += 33;
        } else {
            // Переименование игрока (алиас) — задаётся здесь, добавляет в контакты
            aliasField = new TextFieldWidget(textRenderer, px + PANEL_W - 108, contentY, 100, 15,
                    Text.translatable("pmchat.profile.rename"));
            aliasField.setMaxLength(24);
            aliasField.setText(config.hasAlias(player) ? config.aliasOf(player) : "");
            String rh = Text.translatable("pmchat.profile.rename.hint").getString();
            aliasField.setSuggestion(aliasField.getText().isEmpty() ? rh : "");
            aliasField.setChangedListener(s -> aliasField.setSuggestion(s.isEmpty() ? rh : ""));
            addDrawableChild(aliasField);
            contentY += 21;

            // Кнопка ЧС (5.5) + «Пожаловаться» рядом — официальный аккаунт PocketChat
            // заблокировать нельзя (иначе теряются рассылки/уведомления администрации).
            com.pmchat.client.PmBackend.AccountInfo blockAcc = com.pmchat.client.PmBackend.isConfigured()
                    ? com.pmchat.client.PmBackend.cachedAccountInfo(player) : null;
            boolean officialAccount = blockAcc != null && blockAcc.official;
            boolean blocked = config.isBlocked(player);
            int halfW = (PANEL_W - 24 - 6) / 2;
            if (!officialAccount) {
                addDrawableChild(FlatButton.centered(textRenderer, px + 12, contentY, halfW, 16,
                        Text.translatable(blocked ? "pmchat.profile.unblock" : "pmchat.profile.block"),
                        blocked ? 0xFF5A2A22 : BTN_BG, blocked ? 0xFF6E332A : BTN_HOVER,
                        blocked ? 0xFFA0463A : BTN_BORDER, 0xFFE07A6A, btn -> {
                            PmChatClient.toggleBlocked(player);
                            reinit();
                        }));
            }
            addDrawableChild(FlatButton.centered(textRenderer, officialAccount ? px + 12 : px + 12 + halfW + 6,
                    contentY, officialAccount ? PANEL_W - 24 : halfW, 16,
                    Text.translatable("pmchat.report.open"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFFE0B040,
                    btn -> MinecraftClient.getInstance().setScreen(new PmReportScreen(this, player))));
            contentY += 22;

            // Отправить монеты — только если есть свой аккаунт бэкенда
            if (backendGiftsAvailable()) {
                addDrawableChild(FlatButton.centered(textRenderer, px + 12, contentY, PANEL_W - 24, 16,
                        Text.translatable("pmchat.coins.open"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFFF0C34E,
                        btn -> MinecraftClient.getInstance().setScreen(new PmSendCoinsScreen(this, player))));
                contentY += 22;
            }

            // Личная заметка (4.2+) — как в Discord: видна только тебе, хранится
            // только в pmchat.json, никогда не отправляется собеседнику.
            noteField = new TextFieldWidget(textRenderer, px + 12, contentY + 12, PANEL_W - 24, 15,
                    Text.translatable("pmchat.profile.note"));
            noteField.setMaxLength(200);
            noteField.setText(config.noteOf(player));
            String nh = Text.translatable("pmchat.profile.note.hint").getString();
            noteField.setSuggestion(noteField.getText().isEmpty() ? nh : "");
            noteField.setChangedListener(s -> noteField.setSuggestion(s.isEmpty() ? nh : ""));
            addDrawableChild(noteField);
            contentY += 33;
        }

        // Кнопка «Готово»
        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + panelH - 24, 80, 18,
                Text.translatable("pmchat.profile.done"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> close()));
    }

    private void reinit() {
        // сохраняем правки полей перед пересборкой
        persistFields();
        init();
    }

    private void persistFields() {
        if (birthdayField != null) config.profileBirthday = birthdayField.getText().trim();
        if (descField != null) config.profileDescription = descField.getText().trim();
        if (aliasField != null) config.setAlias(player, aliasField.getText());
        if (noteField != null) config.setNote(player, noteField.getText());
        config.save();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, panelH, BORDER);

        // Заголовок однозначно показывает, чей это профиль (свой или ник игрока)
        String titleStr = self
                ? Text.translatable("pmchat.profile.self").getString()
                : trimTo(player, PANEL_W - 40);
        Text title = Text.literal(titleStr);
        context.drawText(textRenderer, title,
                px + (PANEL_W - textRenderer.getWidth(title)) / 2, py + 8, TITLE, false);

        // ---- Шапка: аватар + ник + роль + статус (+ баланс для себя) ----
        int avX = px + 14, avY = py + 26, avS = 44;
        drawAvatar(context, avX, avY, avS);

        int tx = avX + avS + 12;
        // Должность: назначенная админом перекрывает встроенную (по серверному нику);
        // отображаем локальный псевдоним, если задан
        PmRoles.Effective roleEff = PmRoles.effective(config, player);
        net.minecraft.text.Text fullName = config.hasAlias(player)
                ? Text.literal(config.aliasOf(player))
                : PmNames.displayText(player);
        int nameX = tx;
        String icon = roleEff.icon;
        // Значок роли рисуем ОТДЕЛЬНО только когда показываем псевдоним (у него нет
        // префикса). У серверного ника роль уже есть в самом префиксе — иначе дубль.
        if (config.hasAlias(player) && !icon.isEmpty()) {
            context.drawText(textRenderer, icon, nameX, py + 30, roleEff.color, false);
            nameX += textRenderer.getWidth(icon) + 4;
        }
        int nameMax = px + PANEL_W - 10 - nameX;
        String nameDrawn;
        if (textRenderer.getWidth(fullName) <= nameMax) {
            context.drawText(textRenderer, fullName, nameX, py + 30, TITLE, false);
            nameDrawn = fullName.getString();
        } else {
            nameDrawn = trimTo(fullName.getString(), nameMax);
            context.drawText(textRenderer, nameDrawn, nameX, py + 30, TITLE, false);
        }
        // Галочка верификации / официальный аккаунт (см. PmBackend, server-pocketchat)
        if (com.pmchat.client.PmBackend.isConfigured()) {
            com.pmchat.client.PmBackend.AccountInfo acc = com.pmchat.client.PmBackend.cachedAccountInfo(player);
            if (acc != null && (acc.verified || acc.official)) {
                PmScreen.drawVerifiedBadge(context, textRenderer, nameX + textRenderer.getWidth(nameDrawn) + 3, py + 29);
            }
        }

        // Статус «был(а) в сети»: если есть бэкенд — кросс-серверный (по последнему
        // пингу присутствия), иначе — по таб-листу текущего Minecraft-сервера.
        com.pmchat.client.PmBackend.AccountInfo backendAcc = com.pmchat.client.PmBackend.isConfigured()
                ? com.pmchat.client.PmBackend.cachedAccountInfo(player) : null;
        if (!self && backendAcc != null && backendAcc.official) {
            context.drawText(textRenderer, Text.translatable("pmchat.official.notice"), tx, py + 44, SUBTLE, false);
        } else if (self || backendAcc == null || backendAcc.lastSeenAt <= 0) {
            boolean online = self || onlineEntry() != null;
            context.drawText(textRenderer, Text.translatable(online ? "pmchat.profile.online" : "pmchat.profile.offline"),
                    tx, py + 44, online ? 0xFF6FBF8B : SUBTLE, false);
        } else {
            boolean precise = config.preciseLastSeen && backendAcc.sharePrecise;
            net.minecraft.text.Text status = com.pmchat.client.PmBackend.humanizeLastSeen(backendAcc.lastSeenAt, precise);
            boolean isOnline = System.currentTimeMillis() - backendAcc.lastSeenAt < 90_000L;
            context.drawText(textRenderer, status, tx, py + 44, isOnline ? 0xFF6FBF8B : SUBTLE, false);
        }

        if (self) {
            // Баланс рядом — только свой (4.5). Значение доступно с плагином/Vault.
            String bal = PmChatClient.knownBalance();
            String balText = Text.translatable("pmchat.profile.balance").getString() + ": "
                    + (bal == null ? Text.translatable("pmchat.profile.balance.unknown").getString() : bal);
            context.drawText(textRenderer, balText, tx, py + 56, 0xFFE0B040, false);

            if (com.pmchat.client.PmBackend.isConfigured() && com.pmchat.client.PmBackend.hasAccount()) {
                Long pcBal = com.pmchat.client.PmBackend.cachedSelfBalance();
                String pcBalText = Text.translatable("pmchat.profile.balance.pocketchat").getString() + ": "
                        + (pcBal == null ? "…" : com.pmchat.client.PmBackend.formatCoins(pcBal));
                context.drawText(textRenderer, pcBalText, tx, py + 66, com.pmchat.client.PmBackend.CURRENCY_COLOR, false);
            }
        }

        // ---- Подписи полей ----
        int contentY = py + 84;
        context.drawText(textRenderer, Text.translatable("pmchat.profile.role"), px + 12, contentY + 4, LABEL, false);
        // Значение роли — только для чтения здесь (назначается в админ-панели или
        // определяется из ника автоматически)
        Text roleVal = Text.literal((icon.isEmpty() ? "" : icon + " ") + roleEff.label);
        context.drawText(textRenderer, roleVal,
                px + PANEL_W - 12 - textRenderer.getWidth(roleVal), contentY + 4,
                roleEff.none ? SUBTLE : roleEff.color, false);
        contentY += 21;
        if (self) {
            context.drawText(textRenderer, Text.translatable("pmchat.profile.birthday"),
                    px + 12, contentY + 4, LABEL, false);
            contentY += 21;
            context.drawText(textRenderer, Text.translatable("pmchat.profile.desc"),
                    px + 12, contentY, LABEL, false);
            contentY += 33;
        } else {
            // Подпись поля переименования + место под кнопку ЧС
            context.drawText(textRenderer, Text.translatable("pmchat.profile.rename"),
                    px + 12, contentY + 4, LABEL, false);
            contentY += 21;
            contentY += 22;
            context.drawText(textRenderer, Text.translatable("pmchat.profile.note"),
                    px + 12, contentY, LABEL, false);
            contentY += 33;
        }

        // ---- Раздел подарков (4.2) ----
        renderGifts(context, mouseX, mouseY, contentY);

        // ---- Публикации на страничке (стена) — открываются отдельным окном,
        // список может расти и не влезает в компактный профиль (как PmGiftsScreen).
        renderPostsLink(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private int[] openPostsRect = null;

    private void renderPostsLink(DrawContext context, int mouseX, int mouseY) {
        // Готово-кнопка занимает py+panelH-24..py+panelH-6 — оставляем зазор над ней.
        int top = py + panelH - 42;
        context.fill(px + 8, top, px + PANEL_W - 8, top + 1, BORDER);
        Text postsTitle = Text.translatable("pmchat.posts.section");
        boolean hover = mouseX >= px + 12 && mouseX < px + 12 + textRenderer.getWidth(postsTitle) + 60
                && mouseY >= top + 4 && mouseY < top + 15;
        context.drawText(textRenderer, postsTitle, px + 12, top + 5, hover ? VALUE : TITLE, false);
        Text openHint = Text.translatable("pmchat.posts.openhint");
        context.drawText(textRenderer, openHint, px + 12 + textRenderer.getWidth(postsTitle) + 6, top + 5, SUBTLE, false);
        openPostsRect = new int[]{px + 8, top + 4, textRenderer.getWidth(postsTitle) + textRenderer.getWidth(openHint) + 20, 11};
    }

    private final java.util.List<Object[]> giftRects = new java.util.ArrayList<>(); // x,y,w,h,giftId
    private int[] openGalleryRect = null; // x,y,w,h — клик по заголовку «Подарки» открывает полную галерею
    private int[] openCatalogRect = null; // x,y,w,h — клик по «Подарить →» открывает каталог покупки

    private void renderGifts(DrawContext context, int mouseX, int mouseY, int top) {
        giftRects.clear();
        openCatalogRect = null;
        context.fill(px + 8, top, px + PANEL_W - 8, top + 1, BORDER);
        Text giftsTitle = Text.translatable("pmchat.profile.gifts");
        boolean titleHover = mouseX >= px + 12 && mouseX < px + 12 + textRenderer.getWidth(giftsTitle) + 12
                && mouseY >= top && mouseY < top + 11;
        context.drawText(textRenderer, giftsTitle, px + 12, top + 5, titleHover ? VALUE : TITLE, false);
        Text openHint = Text.translatable("pmchat.gifts.openall");
        context.drawText(textRenderer, openHint, px + 12 + textRenderer.getWidth(giftsTitle) + 6, top + 5, SUBTLE, false);
        openGalleryRect = new int[]{px + 8, top, textRenderer.getWidth(giftsTitle) + textRenderer.getWidth(openHint) + 20, 11};

        boolean plugin = pluginPresent();
        boolean backend = !plugin && backendGiftsAvailable();

        // «Подарить →» — только на чужом профиле, когда есть чем дарить; открывает
        // отдельный прокручиваемый каталог (PmGiftsScreen), а не рисуется тут же —
        // каталог может быть длинным и не влезает в компактный профиль.
        if (!self && backend) {
            Text buyLink = Text.translatable("pmchat.profile.gifts.buyhint");
            int bx = px + PANEL_W - 12 - textRenderer.getWidth(buyLink);
            context.drawText(textRenderer, buyLink, bx, top + 5, SUBTLE, false);
            openCatalogRect = new int[]{bx - 4, top, textRenderer.getWidth(buyLink) + 8, 11};
        } else if (!self && plugin) {
            String hint = Text.translatable("pmchat.profile.gifts.buyhint").getString();
            context.drawText(textRenderer, hint,
                    px + PANEL_W - 12 - textRenderer.getWidth(hint), top + 5, SUBTLE, false);
        }

        if (plugin) {
            renderGiftsViaPlugin(context, mouseX, mouseY, top);
        } else if (backend) {
            renderGiftsViaBackend(context, mouseX, mouseY, top);
        } else {
            context.drawText(textRenderer, trimTo(
                            Text.translatable("pmchat.profile.gifts.needplugin").getString(), PANEL_W - 24),
                    px + 12, top + 17, SUBTLE, false);
        }
    }

    private void renderGiftsViaPlugin(DrawContext context, int mouseX, int mouseY, int top) {
        com.pmchat.client.PmServerMedia sm = com.pmchat.client.PmServerMedia.get();

        // Полученные подарки текущего игрока — ряд иконок
        java.util.List<com.pmchat.client.PmServerMedia.ReceivedGift> got = sm.giftsFor(player);
        int iy = top + 16;
        if (got.isEmpty()) {
            context.drawText(textRenderer, Text.translatable("pmchat.profile.gifts.empty"),
                    px + 12, iy, SUBTLE, false);
        } else {
            int gx = px + 12;
            int shown = 0;
            for (int i = got.size() - 1; i >= 0 && shown < 14; i--, shown++) {
                com.pmchat.client.PmServerMedia.ReceivedGift g = got.get(i);
                String ic = g.icon() == null || g.icon().isEmpty() ? "•" : g.icon();
                context.drawText(textRenderer, ic, gx, iy, 0xFFE0A0E0, false);
                gx += textRenderer.getWidth(ic) + 4;
            }
            if (got.size() > 14) {
                context.drawText(textRenderer, "+" + (got.size() - 14), gx, iy, SUBTLE, false);
            }
        }

        // Отправить подарок (только чужой профиль) — каталог кнопками
        if (!self) {
            java.util.List<com.pmchat.client.PmServerMedia.Gift> cat = sm.giftCatalog();
            int cy = top + 30;
            int cx = px + 12;
            int cellW = 55, cellH = 16, gap = 3;
            for (com.pmchat.client.PmServerMedia.Gift g : cat) {
                if (cx + cellW > px + PANEL_W - 8) {
                    cx = px + 12;
                    cy += cellH + gap;
                }
                boolean hover = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + cellH;
                boolean afford = sm.selfBalance() >= g.price();
                context.fill(cx, cy, cx + cellW, cy + cellH, hover ? BTN_HOVER : BTN_BG);
                context.drawStrokedRectangle(cx, cy, cellW, cellH, BTN_BORDER);
                String label = g.icon() + " " + fmt(g.price());
                context.drawText(textRenderer, trimTo(label, cellW - 6), cx + 4, cy + 4,
                        afford ? 0xFFE0B040 : 0xFF9A6A6A, false);
                giftRects.add(new Object[]{cx, cy, cellW, cellH, g.id(), false});
                cx += cellW + gap;
            }
        }

        // Итог последней покупки (короткое сообщение)
        String rmsg = sm.lastResultMsg();
        if (rmsg != null && System.currentTimeMillis() - sm.lastResultAt() < 4000) {
            context.drawText(textRenderer, trimTo(rmsg, PANEL_W - 24),
                    px + 12, py + panelH - 38, sm.lastResultOk() ? 0xFF6FBF8B : 0xFFE0574C, false);
        }
    }

    private void renderGiftsViaBackend(DrawContext context, int mouseX, int mouseY, int top) {
        java.util.List<com.pmchat.client.PmBackend.ReceivedGift> got = com.pmchat.client.PmBackend.cachedGiftInbox(player);
        int iy = top + 15;
        if (got.isEmpty()) {
            context.drawText(textRenderer, Text.translatable("pmchat.profile.gifts.empty"),
                    px + 12, iy + 4, SUBTLE, false);
        } else {
            int cell = 18, gap = 3;
            int gx = px + 12;
            int shown = 0;
            long now = System.currentTimeMillis();
            for (int i = got.size() - 1; i >= 0 && shown < 10; i--, shown++) {
                com.pmchat.client.PmBackend.ReceivedGift g = got.get(i);
                com.pmchat.client.PmBackend.Gift def = com.pmchat.client.PmBackend.giftById(g.giftId);
                String ic = def != null ? def.icon : (g.giftId == null || g.giftId.isEmpty() ? "•" : g.giftId);
                int rarity = com.pmchat.client.PmBackend.rarityColor(def != null ? def.rarity : null);
                boolean hover = mouseX >= gx && mouseX < gx + cell && mouseY >= iy && mouseY < iy + cell;

                // Карточка-«чип»: фон + рамка цвета редкости, значок по центру.
                context.fill(gx, iy, gx + cell, iy + cell, hover ? 0x40FFFFFF : 0x22FFFFFF);
                context.drawStrokedRectangle(gx, iy, cell, cell, rarity);
                float bob = hover ? 0 : (float) Math.sin(now / 400.0 + shown * 0.9) * 1f;
                context.drawText(textRenderer, ic, gx + (cell - textRenderer.getWidth(ic)) / 2,
                        iy + 4 + (int) bob, 0xFFFFFFFF, false);

                // Цветной кружок-бейдж отправителя (первая буква ника) в углу — как в Telegram.
                String from = g.from == null || g.from.isEmpty() ? "?" : g.from;
                int badgeColor = 0xFF000000 | (from.toLowerCase(Locale.ROOT).hashCode() & 0xFFFFFF);
                fillCircle(context, gx + cell - 2, iy + 2, 4, badgeColor);

                gx += cell + gap;
            }
            if (got.size() > 10) {
                context.drawText(textRenderer, "+" + (got.size() - 10), gx, iy + 4, SUBTLE, false);
            }
        }
    }

    private static String fmt(double d) {
        long l = (long) d;
        return d == l ? Long.toString(l) : String.format(Locale.ROOT, "%.1f", d);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (openGalleryRect != null) {
            int rx = openGalleryRect[0], ry = openGalleryRect[1], rw = openGalleryRect[2], rh = openGalleryRect[3];
            if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                MinecraftClient.getInstance().setScreen(new PmGiftsScreen(this, player, false));
                return true;
            }
        }
        if (openCatalogRect != null) {
            int rx = openCatalogRect[0], ry = openCatalogRect[1], rw = openCatalogRect[2], rh = openCatalogRect[3];
            if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                MinecraftClient.getInstance().setScreen(new PmGiftsScreen(this, player, true));
                return true;
            }
        }
        if (openPostsRect != null) {
            int rx = openPostsRect[0], ry = openPostsRect[1], rw = openPostsRect[2], rh = openPostsRect[3];
            if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                MinecraftClient.getInstance().setScreen(new PmProfilePostsScreen(this, player));
                return true;
            }
        }
        // Дарение через плагин (устаревший путь — плагин сервера больше не поставляется,
        // код оставлен на случай стороннего сервера, отвечающего на pmchat:media)
        for (Object[] r : giftRects) {
            int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
            if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                com.pmchat.client.PmServerMedia.get().buyGift(player, (String) r[4]);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private String trimTo(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private PlayerListEntry onlineEntry() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerListEntry(player) : null;
    }

    private void drawAvatar(DrawContext context, int x, int y, int size) {
        // У заблокированного (5.5) скрываем аватарку даже онлайн
        if (!self && config.isBlocked(player)) {
            fillCircle(context, x + size / 2, y + size / 2, (size + 1) / 2, 0xFF3A3F44);
            context.drawText(textRenderer, "⊘",
                    x + size / 2 - textRenderer.getWidth("⊘") / 2, y + size / 2 - 4, 0xFF8A9096, false);
            return;
        }
        if (com.pmchat.client.PmBackend.isConfigured()) {
            com.pmchat.client.PmBackend.AccountInfo acc = com.pmchat.client.PmBackend.cachedAccountInfo(player);
            if (acc != null && acc.official && com.pmchat.client.PmOfficialIcon.isReady()) {
                com.pmchat.client.PmOfficialIcon.draw(context, x, y, size);
                return;
            }
        }
        PlayerListEntry entry = onlineEntry();
        if (entry != null && entry.getSkinTextures() != null) {
            try {
                PlayerSkinDrawer.draw(context, entry.getSkinTextures(), x, y, size);
                return;
            } catch (Throwable ignored) {
            }
        }
        int bg = 0xFF000000 | (player.hashCode() & 0xFFFFFF);
        fillCircle(context, x + size / 2, y + size / 2, (size + 1) / 2, bg);
        String letter = player.isEmpty() ? "?" : player.substring(0, 1).toUpperCase(Locale.ROOT);
        context.drawText(textRenderer, letter,
                x + size / 2 - textRenderer.getWidth(letter) / 2, y + size / 2 - 4, 0xFFFFFFFF, false);
    }

    private static void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) r * r - dy * dy);
            ctx.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
        }
    }

    @Override
    public void close() {
        persistFields();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
