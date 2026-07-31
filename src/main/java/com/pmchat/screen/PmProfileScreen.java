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

    private static final int PANEL_W = 250;

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

    /** Новый путь подарков/кошелька — свой бэкенд на Railway, не требует плагина. */
    private boolean backendConfigured() {
        return com.pmchat.client.PmBackend.isConfigured();
    }

    private static int rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "rare" -> 0xFF4FA8E0;
            case "epic" -> 0xFFB064E0;
            case "legendary" -> 0xFFE0A030;
            default -> 0xFFA0A8B0; // common
        };
    }

    @Override
    protected void init() {
        applyTheme();
        clearChildren();
        // Каталог бэкенда сейчас в разы больше старого (16 подарков вместо ~6) —
        // на чужом профиле ему нужно больше рядов, отсюда добавочная высота.
        panelH = self ? 214 : (backendConfigured() ? 291 + 80 : 291);
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        // Подтягиваем каталог/баланс и полученные подарки текущего игрока
        if (backendConfigured()) {
            com.pmchat.client.PmBackend.cachedBalance();
            com.pmchat.client.PmBackend.cachedCatalog();
            com.pmchat.client.PmBackend.cachedInbox(player);
        } else if (pluginPresent()) {
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

            // Кнопка ЧС (5.5)
            boolean blocked = config.isBlocked(player);
            addDrawableChild(FlatButton.centered(textRenderer, px + 12, contentY, PANEL_W - 24, 16,
                    Text.translatable(blocked ? "pmchat.profile.unblock" : "pmchat.profile.block"),
                    blocked ? 0xFF5A2A22 : BTN_BG, blocked ? 0xFF6E332A : BTN_HOVER,
                    blocked ? 0xFFA0463A : BTN_BORDER, 0xFFE07A6A, btn -> {
                        PmChatClient.toggleBlocked(player);
                        reinit();
                    }));
            contentY += 22;

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
        // Роль определяется по серверному нику; отображаем локальный псевдоним, если задан
        String serverDisplay = PmNames.displayString(player);
        String role = PmRoles.detect(serverDisplay);
        net.minecraft.text.Text fullName = config.hasAlias(player)
                ? Text.literal(config.aliasOf(player))
                : PmNames.displayText(player);
        int nameX = tx;
        String icon = PmRoles.icon(role);
        // Значок роли рисуем ОТДЕЛЬНО только когда показываем псевдоним (у него нет
        // префикса). У серверного ника роль уже есть в самом префиксе — иначе дубль.
        if (config.hasAlias(player) && !icon.isEmpty()) {
            context.drawText(textRenderer, icon, nameX, py + 30, PmRoles.color(role), false);
            nameX += textRenderer.getWidth(icon) + 4;
        }
        int nameMax = px + PANEL_W - 10 - nameX;
        if (textRenderer.getWidth(fullName) <= nameMax) {
            context.drawText(textRenderer, fullName, nameX, py + 30, TITLE, false);
        } else {
            context.drawText(textRenderer, trimTo(fullName.getString(), nameMax), nameX, py + 30, TITLE, false);
        }

        boolean online = self || onlineEntry() != null;
        context.drawText(textRenderer, Text.translatable(online ? "pmchat.profile.online" : "pmchat.profile.offline"),
                tx, py + 44, online ? 0xFF6FBF8B : SUBTLE, false);

        bonusRect = null;
        if (self) {
            // Баланс рядом — только свой (4.5).
            String balText;
            if (backendConfigured()) {
                double bal = com.pmchat.client.PmBackend.cachedBalance();
                balText = Text.translatable("pmchat.profile.balance").getString() + ": "
                        + (bal < 0 ? "…" : fmt(bal));
            } else {
                String bal = PmChatClient.knownBalance();
                balText = Text.translatable("pmchat.profile.balance").getString() + ": "
                        + (bal == null ? Text.translatable("pmchat.profile.balance.unknown").getString() : bal);
            }
            context.drawText(textRenderer, balText, tx, py + 56, 0xFFE0B040, false);

            if (backendConfigured()) {
                String bonus = Text.translatable("pmchat.profile.bonus").getString();
                int bw = textRenderer.getWidth(bonus) + 8;
                int bx = px + PANEL_W - 12 - bw, by = py + 54;
                boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 12;
                PmScreen.fillRound(context, bx, by, bw, 12, 3, hover ? BTN_HOVER : BTN_BG);
                context.drawText(textRenderer, bonus, bx + 4, by + 2, 0xFF6FBF8B, false);
                bonusRect = new int[]{bx, by, bw, 12};
            }
        }

        // ---- Подписи полей ----
        int contentY = py + 84;
        context.drawText(textRenderer, Text.translatable("pmchat.profile.role"), px + 12, contentY + 4, LABEL, false);
        // Значение роли — только для чтения (определяется из ника автоматически)
        Text roleVal = Text.literal((icon.isEmpty() ? "" : icon + " ")
                + Text.translatable(PmRoles.nameKey(role)).getString());
        context.drawText(textRenderer, roleVal,
                px + PANEL_W - 12 - textRenderer.getWidth(roleVal), contentY + 4,
                role.isEmpty() ? SUBTLE : PmRoles.color(role), false);
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

        super.render(context, mouseX, mouseY, delta);
    }

    private final java.util.List<Object[]> giftRects = new java.util.ArrayList<>(); // x,y,w,h,giftId
    private int[] bonusRect; // x,y,w,h — «забрать ежедневный бонус» (только свой профиль, бэкенд)

    private void renderGifts(DrawContext context, int mouseX, int mouseY, int top) {
        giftRects.clear();
        context.fill(px + 8, top, px + PANEL_W - 8, top + 1, BORDER);
        context.drawText(textRenderer, Text.translatable("pmchat.profile.gifts"),
                px + 12, top + 5, TITLE, false);

        if (backendConfigured()) {
            renderGiftsBackend(context, mouseX, mouseY, top);
        } else if (pluginPresent()) {
            renderGiftsLegacy(context, mouseX, mouseY, top);
        } else {
            context.drawText(textRenderer, trimTo(
                            Text.translatable("pmchat.profile.gifts.needplugin").getString(), PANEL_W - 24),
                    px + 12, top + 17, SUBTLE, false);
        }
    }

    private void renderGiftsBackend(DrawContext context, int mouseX, int mouseY, int top) {
        // Подсказка «нажми, чтобы подарить» — только на чужом профиле
        if (!self) {
            String hint = Text.translatable("pmchat.profile.gifts.buyhint").getString();
            context.drawText(textRenderer, hint,
                    px + PANEL_W - 12 - textRenderer.getWidth(hint), top + 5, SUBTLE, false);
        }

        java.util.List<com.pmchat.client.PmBackend.ReceivedGift> got = com.pmchat.client.PmBackend.cachedInbox(player);
        int iy = top + 16;
        if (got.isEmpty()) {
            context.drawText(textRenderer, Text.translatable("pmchat.profile.gifts.empty"),
                    px + 12, iy, SUBTLE, false);
        } else {
            java.util.List<com.pmchat.client.PmBackend.Gift> catForIcons = com.pmchat.client.PmBackend.cachedCatalog();
            int gx = px + 12;
            int shown = 0;
            for (int i = got.size() - 1; i >= 0 && shown < 14; i--, shown++) {
                com.pmchat.client.PmBackend.ReceivedGift g = got.get(i);
                String ic = giftIcon(catForIcons, g.giftId());
                context.drawText(textRenderer, ic, gx, iy, 0xFFE0A0E0, false);
                gx += textRenderer.getWidth(ic) + 4;
            }
            if (got.size() > 14) {
                context.drawText(textRenderer, "+" + (got.size() - 14), gx, iy, SUBTLE, false);
            }
        }

        if (!self) {
            java.util.List<com.pmchat.client.PmBackend.Gift> cat = com.pmchat.client.PmBackend.cachedCatalog();
            double balance = com.pmchat.client.PmBackend.cachedBalance();
            int cy = top + 30;
            int cx = px + 12;
            int cellW = 55, cellH = 16, gap = 3;
            if (cat != null) {
                for (com.pmchat.client.PmBackend.Gift g : cat) {
                    if (cx + cellW > px + PANEL_W - 8) {
                        cx = px + 12;
                        cy += cellH + gap;
                    }
                    boolean hover = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + cellH;
                    boolean afford = balance >= g.price();
                    int rarity = rarityColor(g.rarity());
                    // Легендарки лёгким пульсом рамки — выделяются в каталоге
                    boolean pulse = "legendary".equals(g.rarity());
                    int borderColor = pulse
                            ? blend(rarity, 0xFFFFFFFF, (float) (0.15 + 0.15 * Math.sin(System.currentTimeMillis() / 260.0)))
                            : rarity;
                    PmScreen.fillRound(context, cx, cy, cellW, cellH, 3, hover ? BTN_HOVER : BTN_BG);
                    context.drawStrokedRectangle(cx, cy, cellW, cellH, borderColor);
                    String label = g.icon() + " " + fmt(g.price());
                    context.drawText(textRenderer, trimTo(label, cellW - 6), cx + 4, cy + 4,
                            afford ? 0xFFE0B040 : 0xFF9A6A6A, false);
                    giftRects.add(new Object[]{cx, cy, cellW, cellH, g.id()});
                    cx += cellW + gap;
                }
            } else {
                context.drawText(textRenderer, "…", px + 12, cy, SUBTLE, false);
            }
        }

        String rmsg = com.pmchat.client.PmBackend.lastResultMsg();
        if (rmsg != null && System.currentTimeMillis() - com.pmchat.client.PmBackend.lastResultAt() < 4000) {
            boolean ok = com.pmchat.client.PmBackend.lastResultOk();
            String shown = ok ? Text.translatable("pmchat.profile.gifts.sent").getString() : rmsg;
            context.drawText(textRenderer, trimTo(shown, PANEL_W - 24),
                    px + 12, py + panelH - 38, ok ? 0xFF6FBF8B : 0xFFE0574C, false);
        }
    }

    private static String giftIcon(java.util.List<com.pmchat.client.PmBackend.Gift> catalog, String giftId) {
        if (catalog != null) {
            for (com.pmchat.client.PmBackend.Gift g : catalog) {
                if (g.id().equals(giftId)) return g.icon();
            }
        }
        return "•";
    }

    /** Линейная интерполяция цвета (для лёгкого пульса рамки легендарных подарков). */
    private static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void renderGiftsLegacy(DrawContext context, int mouseX, int mouseY, int top) {
        // Подсказка «нажми, чтобы подарить» — только на чужом профиле с плагином
        if (!self) {
            String hint = Text.translatable("pmchat.profile.gifts.buyhint").getString();
            context.drawText(textRenderer, hint,
                    px + PANEL_W - 12 - textRenderer.getWidth(hint), top + 5, SUBTLE, false);
        }

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
                giftRects.add(new Object[]{cx, cy, cellW, cellH, g.id()});
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

    private static String fmt(double d) {
        long l = (long) d;
        return d == l ? Long.toString(l) : String.format(Locale.ROOT, "%.1f", d);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (bonusRect != null && mx >= bonusRect[0] && mx < bonusRect[0] + bonusRect[2]
                && my >= bonusRect[1] && my < bonusRect[1] + bonusRect[3]) {
            com.pmchat.client.PmBackend.claimDailyBonusAsync();
            return true;
        }
        for (Object[] r : giftRects) {
            int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
            if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                String giftId = (String) r[4];
                if (backendConfigured()) {
                    com.pmchat.client.PmBackend.buyGiftAsync(player, giftId);
                } else {
                    com.pmchat.client.PmServerMedia.get().buyGift(player, giftId);
                }
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
