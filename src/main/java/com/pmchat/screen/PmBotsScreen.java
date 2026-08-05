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
 * тем же почтовым ящиком, что и офлайн-ЛС.
 */
@Environment(EnvType.CLIENT)
public class PmBotsScreen extends Screen {

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;
    private int px, py, pw, ph;

    private TextFieldWidget nameField;
    private TextFieldWidget userField;
    private TextFieldWidget openField;
    private String nameText = "", userText = "", openText = "";

    /** null — ещё грузим список; иначе актуальный список ботов. */
    private List<PmBackend.BotInfo> bots = null;
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
        ph = Math.min(240, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        if (bots == null) loadBots();
        layout();
    }

    private void loadBots() {
        PmBackend.listBots((ok, list, err) -> {
            bots = ok && list != null ? list : new ArrayList<>();
            if (client != null) layout();
        });
    }

    /** Пересобирает виджеты (поля + кнопки) — вызывается заново после загрузки/изменения списка. */
    private void layout() {
        if (nameField != null) nameText = nameField.getText();
        if (userField != null) userText = userField.getText();
        if (openField != null) openText = openField.getText();
        clearChildren();

        int fx = px + 12;
        int fw = pw - 24;
        int y = py + 24;

        // Создание бота: имя + @username + кнопка
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

        addDrawableChild(FlatButton.centered(textRenderer, fx, y, fw, 15,
                Text.translatable("pmchat.bots.create"), 0xFF244A33, 0xFF2E5C40, 0xFF4C8A66, 0xFFCFEEDA,
                btn -> createBot()));
        y += 24;

        // Список моих ботов
        if (bots == null) {
            y += 14; // «загрузка…» рисуется в render()
        } else {
            int rowH = 30;
            int shown = 0;
            for (PmBackend.BotInfo b : bots) {
                if (y + rowH > py + ph - 44) break; // не влезает — прячем остаток
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

        // Открыть чат с чужим ботом по @username
        int openY = py + ph - 40;
        openField = new TextFieldWidget(textRenderer, fx, openY, fw - 66, 15, Text.translatable("pmchat.bots.openhint"));
        openField.setMaxLength(32);
        openField.setText(openText);
        String openHint = Text.translatable("pmchat.bots.openhint").getString();
        openField.setSuggestion(openText.isEmpty() ? openHint : null);
        openField.setChangedListener(s -> openField.setSuggestion(s.isEmpty() ? openHint : null));
        addDrawableChild(openField);
        addDrawableChild(FlatButton.centered(textRenderer, fx + fw - 62, openY, 62, 15,
                Text.translatable("pmchat.bots.opengo"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> {
                    String u = openField.getText().trim().replaceFirst("^@", "");
                    if (!u.isEmpty()) MinecraftClient.getInstance().setScreen(new PmScreen(u));
                }));

        // Закрыть
        addDrawableChild(FlatButton.centered(textRenderer, px + pw / 2 - 40, py + ph - 20, 80, 15,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));

        closeRect = new int[]{px + pw - 18, py + 5, 14, 14};
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
        int y = py + 24 + 19 + 24; // после полей создания + кнопки

        // Список ботов (текстовая часть; кнопки рисует super.render)
        if (bots == null) {
            ctx.drawText(textRenderer, Text.translatable("pmchat.bots.loading"), fx, y, LABEL, false);
        } else if (bots.isEmpty()) {
            ctx.drawText(textRenderer, Text.translatable("pmchat.bots.none"), fx, y, LABEL, false);
        } else {
            int rowH = 30;
            for (PmBackend.BotInfo b : bots) {
                if (y + rowH > py + ph - 44) break;
                String head = "@" + b.username + (b.name.isBlank() ? "" : "  " + b.name);
                ctx.drawText(textRenderer, trim(head, fw), fx, y, VALUE, false);
                y += rowH;
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
        if (closeRect != null) {
            int mx = (int) click.x(), my = (int) click.y();
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
