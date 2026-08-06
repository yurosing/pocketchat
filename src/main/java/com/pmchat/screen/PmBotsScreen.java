package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * «Боты» — как BotFather в Telegram. Игрок создаёт бота своим аккаунтом, получает
 * bot-токен и сам где угодно хостит программу, которая ходит в Bot API бэкенда
 * ({@code /v1/bot/getUpdates}, {@code /v1/bot/sendMessage}). ЛС боту от игроков
 * идут через бэкенд (не через /m — бот не игрок), ответы бота приходят игроку
 * тем же почтовым ящиком, что и офлайн-ЛС. Раньше жил в Настройках — теперь
 * отдельный первоклассный пункт (значок робота у списка чатов).
 */
@Environment(EnvType.CLIENT)
public class PmBotsScreen extends Screen {

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;
    private int px, py, pw, ph;

    private TextFieldWidget nameField;
    private TextFieldWidget userField;
    private TextFieldWidget searchField;
    private String nameText = "", userText = "", searchText = "";

    /** null — ещё грузим список; иначе актуальный список ботов. */
    private List<PmBackend.BotInfo> bots = null;
    /** Цена создания бота (задаёт админ), -1 — ещё не загружена. */
    private long createPrice = -1;
    /** Результаты поиска по @username, вживую по мере ввода в searchField. */
    private List<PmBackend.BotInfo> searchResults = new ArrayList<>();
    private final List<Object[]> searchRowRects = new ArrayList<>(); // x,y,w,h,username
    private int searchSeq = 0;

    private Text status = Text.empty();
    private int statusColor = 0xFFAAAAAA;
    private int[] closeRect;

    public PmBotsScreen(Screen parent) {
        super(Text.translatable("pmchat.bots.title"));
        this.parent = parent;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    @Override
    protected void init() {
        applyTheme();
        pw = Math.min(320, width - 24);
        ph = Math.min(280, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        if (bots == null) loadBots();
        if (createPrice < 0) {
            PmBackend.getBotCreatePrice((ok, price, err) -> {
                createPrice = ok && price != null ? price : 0;
                if (client != null) layout();
            });
        }
        layout();
    }

    /** Package-visible: {@link PmBotEditScreen} calls this on the way back to refresh the list. */
    void loadBots() {
        PmBackend.listBots((ok, list, err) -> {
            bots = ok && list != null ? list : new ArrayList<>();
            if (client != null) layout();
        });
    }

    /** Пересобирает виджеты (поля + кнопки) — вызывается заново после загрузки/изменения списка. */
    private void layout() {
        if (nameField != null) nameText = nameField.getText();
        if (userField != null) userText = userField.getText();
        if (searchField != null) searchText = searchField.getText();
        clearChildren();

        int fx = px + 12;
        int fw = pw - 24;
        int y = py + 24;

        // Создание бота: имя + @username
        nameField = new TextFieldWidget(textRenderer, fx, y, fw / 2 - 3, 15, Text.translatable("pmchat.bots.name"));
        nameField.setMaxLength(64);
        nameField.setText(nameText);
        String nameHint = Text.translatable("pmchat.bots.name").getString();
        nameField.setSuggestion(nameText.isEmpty() ? nameHint : null);
        nameField.setChangedListener(s -> nameField.setSuggestion(s.isEmpty() ? nameHint : null));
        addDrawableChild(nameField);

        userField = new TextFieldWidget(textRenderer, fx + fw / 2 + 3, y, fw / 2 - 3, 15, Text.translatable("pmchat.bots.username"));
        userField.setMaxLength(32);
        userField.setText(userText);
        String userHint = Text.translatable("pmchat.bots.username").getString();
        userField.setSuggestion(userText.isEmpty() ? userHint : null);
        userField.setChangedListener(s -> userField.setSuggestion(s.isEmpty() ? userHint : null));
        addDrawableChild(userField);
        y += 19;
        // Цена создания (если админ её включил) рисуется в render() тут же, без виджета.
        y += (createPrice > 0) ? 10 : 0;

        int createW = fw - 96;
        addDrawableChild(FlatButton.centered(textRenderer, fx, y, createW, 15,
                Text.translatable("pmchat.bots.create"), 0xFF244A33, 0xFF2E5C40, 0xFF4C8A66, 0xFFCFEEDA,
                btn -> createBot()));
        addDrawableChild(FlatButton.centered(textRenderer, fx + createW + 4, y, fw - createW - 4, 15,
                Text.translatable("pmchat.bots.store"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFFF0C34E,
                btn -> MinecraftClient.getInstance().setScreen(new PmBotStoreScreen(this))));
        y += 22;

        // Список моих ботов
        int listBottom = py + ph - 78;
        if (bots == null) {
            y += 14; // «загрузка…» рисуется в render()
        } else {
            int rowH = 30;
            int shown = 0;
            for (PmBackend.BotInfo b : bots) {
                if (y + rowH > listBottom) break; // не влезает — прячем остаток
                int bw = fw;
                int actY = y + 13;
                int actW = (bw - 8) / 3;
                addDrawableChild(FlatButton.centered(textRenderer, fx, actY, actW, 13,
                        Text.translatable("pmchat.bots.copytoken"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFF9CC4DC,
                        btn -> {
                            MinecraftClient.getInstance().keyboard.setClipboard(b.token);
                            status = Text.translatable("pmchat.bots.copied");
                            statusColor = 0xFF8FD8A8;
                        }));
                addDrawableChild(FlatButton.centered(textRenderer, fx + actW + 4, actY, actW, 13,
                        Text.translatable("pmchat.bots.openchat"), BTN_BG, BTN_HOVER, BTN_BORDER, 0xFF8FD8A8,
                        btn -> MinecraftClient.getInstance().setScreen(new PmScreen(b.username))));
                addDrawableChild(FlatButton.centered(textRenderer, fx + 2 * (actW + 4), actY, actW, 13,
                        Text.translatable("pmchat.bots.delete"), 0xFF5A2A22, 0xFF6E332A, 0xFFA0463A, 0xFFE07A6A,
                        btn -> deleteBot(b.username)));
                y += rowH;
                shown++;
            }
            if (shown == 0) y += 14; // «пока нет ботов» рисуется в render()
        }

        // Поиск ботов по @username — вживую, список результатов ниже поля
        int searchY = py + ph - 40;
        searchField = new TextFieldWidget(textRenderer, fx, searchY, fw, 15, Text.translatable("pmchat.bots.searchhint"));
        searchField.setMaxLength(32);
        searchField.setText(searchText);
        String searchHint = Text.translatable("pmchat.bots.searchhint").getString();
        searchField.setSuggestion(searchText.isEmpty() ? searchHint : null);
        searchField.setChangedListener(s -> {
            searchField.setSuggestion(s.isEmpty() ? searchHint : null);
            runSearch(s.trim().replaceFirst("^@", ""));
        });
        addDrawableChild(searchField);

        // Закрыть
        addDrawableChild(FlatButton.centered(textRenderer, px + pw / 2 - 40, py + ph - 20, 80, 15,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));

        closeRect = new int[]{px + pw - 18, py + 5, 14, 14};
    }

    private void runSearch(String query) {
        int seq = ++searchSeq;
        if (query.isEmpty()) {
            searchResults = new ArrayList<>();
            return;
        }
        PmBackend.searchBots(query, (ok, list, err) -> {
            if (seq != searchSeq) return; // устарело — пользователь уже печатает дальше
            searchResults = ok && list != null ? list : new ArrayList<>();
        });
    }

    private void createBot() {
        String name = nameField.getText().trim();
        String user = userField.getText().trim().replaceFirst("^@", "");
        if (user.isEmpty()) {
            status = Text.translatable("pmchat.bots.needusername");
            statusColor = 0xFFE07A6A;
            return;
        }
        PmBackend.createBot(user, name, (ok, bot, err) -> {
            if (ok && bot != null) {
                status = Text.translatable("pmchat.bots.created", bot.username);
                statusColor = 0xFF8FD8A8;
                nameField.setText("");
                userField.setText("");
                nameText = ""; userText = "";
                MinecraftClient.getInstance().keyboard.setClipboard(bot.token);
                loadBots();
            } else if (err != null && err.contains("insufficient balance")) {
                status = Text.translatable("pmchat.bots.needcoins", createPrice);
                statusColor = 0xFFE07A6A;
            } else {
                status = Text.translatable("pmchat.bots.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
            }
        });
    }

    private void deleteBot(String username) {
        PmBackend.deleteBot(username, (ok, v, err) -> {
            if (ok) {
                status = Text.translatable("pmchat.bots.deleted", username);
                statusColor = 0xFFAAB0B6;
                loadBots();
            } else {
                status = Text.translatable("pmchat.bots.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
            }
        });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xC0000000);
        ctx.fill(px, py, px + pw, py + ph, BG);
        ctx.drawStrokedRectangle(px, py, pw, ph, BORDER);

        Text title = getTitle();
        ctx.drawText(textRenderer, title, px + 12, py + 8, TITLE, false);
        String x = "✕";
        boolean hovX = closeRect != null && mouseX >= closeRect[0] && mouseX < closeRect[0] + closeRect[2]
                && mouseY >= closeRect[1] && mouseY < closeRect[1] + closeRect[3];
        ctx.drawText(textRenderer, x, px + pw - 15, py + 8, hovX ? TITLE : LABEL, false);

        int fx = px + 12;
        int fw = pw - 24;
        int y = py + 24 + 19;
        if (createPrice > 0) {
            ctx.drawText(textRenderer, Text.translatable("pmchat.bots.priceline", createPrice), fx, y, LABEL, false);
            y += 10;
        }
        y += 22; // строка кнопок «Создать»/«Магазин»

        // Список ботов (текстовая часть; кнопки рисует super.render)
        if (bots == null) {
            ctx.drawText(textRenderer, Text.translatable("pmchat.bots.loading"), fx, y, LABEL, false);
        } else if (bots.isEmpty()) {
            ctx.drawText(textRenderer, Text.translatable("pmchat.bots.none"), fx, y, LABEL, false);
        } else {
            int rowH = 30;
            int listBottom = py + ph - 78;
            for (PmBackend.BotInfo b : bots) {
                if (y + rowH > listBottom) break;
                String head = "@" + b.username + (b.name.isBlank() ? "" : "  " + b.name);
                ctx.drawText(textRenderer, trim(head, fw), fx, y, VALUE, false);
                y += rowH;
            }
        }

        // Результаты поиска — маленький выпадающий список над полем поиска
        searchRowRects.clear();
        if (!searchResults.isEmpty()) {
            int searchY = py + ph - 40;
            int rowH = 14;
            int listTop = Math.max(y + 2, searchY - Math.min(searchResults.size(), 4) * rowH - 2);
            int ry = listTop;
            for (PmBackend.BotInfo b : searchResults) {
                if (ry + rowH > searchY - 2) break;
                boolean hov = mouseX >= fx && mouseX < fx + fw && mouseY >= ry && mouseY < ry + rowH;
                ctx.fill(fx, ry, fx + fw, ry + rowH - 1, hov ? BTN_HOVER : BTN_BG);
                String label = "@" + b.username + (b.name.isBlank() ? "" : "  " + b.name);
                ctx.drawText(textRenderer, trim(label, fw - 6), fx + 3, ry + 3, VALUE, false);
                searchRowRects.add(new Object[]{fx, ry, fw, rowH - 1, b.username});
                ry += rowH;
            }
        }

        // Статус
        if (!status.getString().isEmpty()) {
            ctx.drawText(textRenderer, status, fx, py + ph - 54, statusColor, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        for (Object[] r : searchRowRects) {
            if (mx >= (int) r[0] && mx < (int) r[0] + (int) r[2]
                    && my >= (int) r[1] && my < (int) r[1] + (int) r[3]) {
                MinecraftClient.getInstance().setScreen(new PmScreen((String) r[4]));
                return true;
            }
        }
        if (closeRect != null) {
            if (mx >= closeRect[0] && mx < closeRect[0] + closeRect[2]
                    && my >= closeRect[1] && my < closeRect[1] + closeRect[3]) {
                close();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private String trim(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
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
