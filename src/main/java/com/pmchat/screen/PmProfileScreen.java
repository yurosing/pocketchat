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

    public PmProfileScreen(Screen parent, String player) {
        super(Text.translatable("pmchat.profile.title"));
        this.parent = parent;
        String me = PmChatClient.selfName();
        this.self = player == null || player.isBlank() || player.equalsIgnoreCase(me);
        this.player = self ? me : player.trim();
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

    @Override
    protected void init() {
        applyTheme();
        clearChildren();
        panelH = self ? 214 : 258;
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
        if (!icon.isEmpty()) {
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

        if (self) {
            // Баланс рядом — только свой (4.5). Значение доступно с плагином/Vault.
            String bal = PmChatClient.knownBalance();
            String balText = Text.translatable("pmchat.profile.balance").getString() + ": "
                    + (bal == null ? Text.translatable("pmchat.profile.balance.unknown").getString() : bal);
            context.drawText(textRenderer, balText, tx, py + 56, 0xFFE0B040, false);
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
        }

        // ---- Раздел подарков (4.2) ----
        renderGifts(context, mouseX, mouseY, contentY);

        super.render(context, mouseX, mouseY, delta);
    }

    private final java.util.List<Object[]> giftRects = new java.util.ArrayList<>(); // x,y,w,h,giftId

    private void renderGifts(DrawContext context, int mouseX, int mouseY, int top) {
        giftRects.clear();
        context.fill(px + 8, top, px + PANEL_W - 8, top + 1, BORDER);
        context.drawText(textRenderer, Text.translatable("pmchat.profile.gifts"),
                px + 12, top + 5, TITLE, false);
        // Подсказка «нажми, чтобы подарить» — только на чужом профиле с плагином
        if (!self && pluginPresent()) {
            String hint = Text.translatable("pmchat.profile.gifts.buyhint").getString();
            context.drawText(textRenderer, hint,
                    px + PANEL_W - 12 - textRenderer.getWidth(hint), top + 5, SUBTLE, false);
        }

        if (!pluginPresent()) {
            context.drawText(textRenderer, trimTo(
                            Text.translatable("pmchat.profile.gifts.needplugin").getString(), PANEL_W - 24),
                    px + 12, top + 17, SUBTLE, false);
            return;
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
