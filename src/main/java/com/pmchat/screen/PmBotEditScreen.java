package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

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

    private EditBox nameField;
    private EditBox userField;
    private String nameText, userText;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;
    private String currentUsername;

    public PmBotEditScreen(PmBotsScreen parent, PmBackend.BotInfo bot) {
        super(Component.translatable("pmchat.bots.edit.title", bot.username));
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
        if (nameField != null) nameText = nameField.getValue();
        if (userField != null) userText = userField.getValue();
        clearWidgets();

        int fx = px + 12;
        int fw = pw - 24;
        int y = py + 26;

        nameField = new EditBox(font, fx, y, fw, 15, Component.translatable("pmchat.bots.name"));
        nameField.setMaxLength(64);
        nameField.setValue(nameText);
        addRenderableWidget(nameField);
        y += 19;

        userField = new EditBox(font, fx, y, fw, 15, Component.translatable("pmchat.bots.username"));
        userField.setMaxLength(32);
        userField.setValue(userText);
        addRenderableWidget(userField);
        y += 22;

        addRenderableWidget(FlatButton.centered(font, fx, y, fw, 15,
                Component.translatable("pmchat.bots.save"), 0xFF244A33, 0xFF2E5C40, 0xFF4C8A66, 0xFFCFEEDA,
                btn -> save(false)));
        y += 19;

        addRenderableWidget(FlatButton.centered(font, fx, y, fw, 15,
                Component.translatable("pmchat.bots.regen"), 0xFF5A2A22, 0xFF6E332A, 0xFFA0463A, 0xFFE07A6A,
                btn -> save(true)));
        y += 22;

        addRenderableWidget(FlatButton.centered(font, px + pw / 2 - 40, y, 80, 15,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> back()));
    }

    private void save(boolean regenerateToken) {
        String name = nameField.getValue().trim();
        String newUser = userField.getValue().trim().replaceFirst("^@", "");
        if (newUser.isEmpty()) {
            status = Component.translatable("pmchat.bots.needusername");
            statusColor = 0xFFE07A6A;
            return;
        }
        String newUserArg = newUser.equalsIgnoreCase(currentUsername) ? null : newUser;
        PmBackend.editBot(currentUsername, newUserArg, name, regenerateToken, (ok, bot, err) -> {
            if (ok && bot != null) {
                currentUsername = bot.username;
                if (regenerateToken) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(bot.token);
                    status = Component.translatable("pmchat.bots.regenok");
                } else {
                    status = Component.translatable("pmchat.bots.saved");
                }
                statusColor = 0xFF8FD8A8;
            } else {
                status = Component.translatable("pmchat.bots.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
            }
        });
    }

    private void back() {
        parent.loadBots();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xC0000000);
        ctx.fill(px, py, px + pw, py + ph, BG);
        ctx.outline(px, py, pw, ph, BORDER);

        Component title = getTitle();
        ctx.text(font, trim(title.getString(), pw - 16), px + 8, py + 8, TITLE, false);

        if (!status.getString().isEmpty()) {
            ctx.text(font, status, px + 12, py + ph - 16, statusColor, false);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private String trim(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public void onClose() {
        back();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
