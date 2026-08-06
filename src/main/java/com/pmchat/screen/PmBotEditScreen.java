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

/**
 * Редактирование одного бота: имя, @username (переезд очереди входящих на
 * бэкенде — см. server-pocketchat) и перевыпуск токена. Открывается из
 * {@link PmBotsScreen} и возвращается туда же (обновив список) по кнопке
 * «Готово» — так же, как остальные модальные экраны мода.
 */
@Environment(EnvType.CLIENT)
public class PmBotEditScreen extends Screen {

    private final PmBotsScreen parent;
    private final String originalUsername;
    private final PmConfig config = PmChatClient.getConfig();

    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;
    private int px, py, pw, ph;

    private TextFieldWidget nameField;
    private TextFieldWidget userField;
    private String nameText, userText;
    private Text status = Text.empty();
    private int statusColor = 0xFFAAAAAA;
    private String currentUsername;

    public PmBotEditScreen(PmBotsScreen parent, PmBackend.BotInfo bot) {
        super(Text.translatable("pmchat.bots.edit.title", bot.username));
        this.parent = parent;
        this.originalUsername = bot.username;
        this.currentUsername = bot.username;
        this.nameText = bot.name;
        this.userText = bot.username;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    @Override
    protected void init() {
        applyTheme();
        pw = Math.min(260, width - 24);
        ph = Math.min(150, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        layout();
    }

    private void layout() {
        if (nameField != null) nameText = nameField.getText();
        if (userField != null) userText = userField.getText();
        clearChildren();

        int fx = px + 12;
        int fw = pw - 24;
        int y = py + 26;

        nameField = new TextFieldWidget(textRenderer, fx, y, fw, 15, Text.translatable("pmchat.bots.name"));
        nameField.setMaxLength(64);
        nameField.setText(nameText);
        addDrawableChild(nameField);
        y += 19;

        userField = new TextFieldWidget(textRenderer, fx, y, fw, 15, Text.translatable("pmchat.bots.username"));
        userField.setMaxLength(32);
        userField.setText(userText);
        addDrawableChild(userField);
        y += 22;

        addDrawableChild(FlatButton.centered(textRenderer, fx, y, fw, 15,
                Text.translatable("pmchat.bots.save"), 0xFF244A33, 0xFF2E5C40, 0xFF4C8A66, 0xFFCFEEDA,
                btn -> save(false)));
        y += 19;

        addDrawableChild(FlatButton.centered(textRenderer, fx, y, fw, 15,
                Text.translatable("pmchat.bots.regen"), 0xFF5A2A22, 0xFF6E332A, 0xFFA0463A, 0xFFE07A6A,
                btn -> save(true)));
        y += 22;

        addDrawableChild(FlatButton.centered(textRenderer, px + pw / 2 - 40, y, 80, 15,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> back()));
    }

    private void save(boolean regenerateToken) {
        String name = nameField.getText().trim();
        String newUser = userField.getText().trim().replaceFirst("^@", "");
        if (newUser.isEmpty()) {
            status = Text.translatable("pmchat.bots.needusername");
            statusColor = 0xFFE07A6A;
            return;
        }
        String newUserArg = newUser.equalsIgnoreCase(currentUsername) ? null : newUser;
        PmBackend.editBot(currentUsername, newUserArg, name, regenerateToken, (ok, bot, err) -> {
            if (ok && bot != null) {
                currentUsername = bot.username;
                if (regenerateToken) {
                    MinecraftClient.getInstance().keyboard.setClipboard(bot.token);
                    status = Text.translatable("pmchat.bots.regenok");
                } else {
                    status = Text.translatable("pmchat.bots.saved");
                }
                statusColor = 0xFF8FD8A8;
            } else {
                status = Text.translatable("pmchat.bots.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
            }
        });
    }

    private void back() {
        parent.loadBots();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xC0000000);
        ctx.fill(px, py, px + pw, py + ph, BG);
        ctx.drawStrokedRectangle(px, py, pw, ph, BORDER);

        Text title = getTitle();
        ctx.drawText(textRenderer, trim(title.getString(), pw - 16), px + 8, py + 8, TITLE, false);

        if (!status.getString().isEmpty()) {
            ctx.drawText(textRenderer, status, px + 12, py + ph - 16, statusColor, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private String trim(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public void close() {
        back();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
