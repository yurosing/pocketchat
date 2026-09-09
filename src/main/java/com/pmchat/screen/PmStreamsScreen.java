package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmServerMedia;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * «Стримы» — список игроков, которые сейчас стримят (внешний сервис,
 * Twitch/YouTube — мод только показывает статус+ссылку, видео сам не тянет),
 * кнопка «начать/закончить стрим» для себя. Кнопки видны и без серверного
 * плагина (иначе нечего было бы нажать) — но реально объявить стрим другим
 * игрокам можно только когда плагин установлен: без него при попытке
 * показывается пояснение вместо молчаливого ничегонеделания.
 */
@Environment(EnvType.CLIENT)
public class PmStreamsScreen extends Screen {

    private static final int ROW_H = 34;

    /** Не static final — подгоняется под размер экрана в init() (GUI Scale 4 и т.п.). */
    private int PANEL_W = 300;

    private int BG, BORDER, LABEL, TITLE, SUBTLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();
    private final PmServerMedia sm = PmServerMedia.get();

    private int px, py, panelH, headerH = 26;
    private int lastSeenVersion = -1;
    private boolean requested = false;

    // Диалог «начать стрим»
    private boolean startMode = false;
    private EditBox titleField, urlField;

    // Локальное сообщение (напр. «нужен плагин»)
    private String localMsg = null;
    private long localMsgAt = 0L;

    public PmStreamsScreen(Screen parent) {
        super(Component.translatable("pmchat.streams.title"));
        this.parent = parent;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        SUBTLE = t.value;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    private boolean pluginPresent() {
        return sm.isAvailable();
    }

    @Override
    protected void init() {
        applyTheme();
        clearWidgets();
        lastSeenVersion = sm.streamVersion();

        if (pluginPresent() && !requested) {
            requested = true;
            sm.requestStreams();
        }

        int listRows = Math.max(1, sm.liveStreams().size());
        int listH = Math.min(listRows, 6) * ROW_H + 6;
        headerH = pluginPresent() ? 26 : 34;
        PANEL_W = Math.max(160, Math.min(300, width - 24));
        panelH = Math.min(headerH + 24 + listH + 36, height - 24);
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        if (!startMode) {
            addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 70, py + headerH, 140, 16,
                    Component.translatable(sm.isSelfStreaming() ? "pmchat.streams.stop" : "pmchat.streams.start"),
                    sm.isSelfStreaming() ? 0xFF5A2A22 : 0xFF2E5F46,
                    sm.isSelfStreaming() ? 0xFF6E332A : 0xFF376F52,
                    sm.isSelfStreaming() ? 0xFFA0463A : 0xFF4C8A66,
                    0xFFEDF3F0, btn -> {
                        if (!pluginPresent()) {
                            showLocalMsg(Component.translatable("pmchat.streams.needplugin").getString());
                            return;
                        }
                        if (sm.isSelfStreaming()) {
                            sm.stopStream();
                            reinit();
                        } else {
                            startMode = true;
                            reinit();
                        }
                    }).withIcon(PmIcons.STREAM));
        }

        if (startMode) {
            int fy = py + headerH + 22;
            titleField = new EditBox(font, px + 16, fy, PANEL_W - 32, 16,
                    Component.translatable("pmchat.streams.titlehint"));
            titleField.setMaxLength(64);
            titleField.setSuggestion(Component.translatable("pmchat.streams.titlehint").getString());
            addRenderableWidget(titleField);

            urlField = new EditBox(font, px + 16, fy + 22, PANEL_W - 32, 16,
                    Component.translatable("pmchat.streams.urlhint"));
            urlField.setMaxLength(96);
            urlField.setSuggestion(Component.translatable("pmchat.streams.urlhint").getString());
            addRenderableWidget(urlField);

            addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 90, fy + 44, 84, 18,
                    Component.translatable("pmchat.streams.cancel"),
                    BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> {
                        startMode = false;
                        reinit();
                    }));
            addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 + 6, fy + 44, 84, 18,
                    Component.translatable("pmchat.streams.go"),
                    0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> {
                        if (!pluginPresent()) {
                            showLocalMsg(Component.translatable("pmchat.streams.needplugin").getString());
                            startMode = false;
                            reinit();
                            return;
                        }
                        sm.startStream(titleField.getValue().trim(), urlField.getValue().trim());
                        startMode = false;
                        reinit();
                    }));
        }

        addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 40, py + panelH - 24, 80, 18,
                Component.translatable("pmchat.settings.done"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> onClose()));
    }

    private void showLocalMsg(String msg) {
        localMsg = msg;
        localMsgAt = System.currentTimeMillis();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (sm.streamVersion() != lastSeenVersion && !startMode) {
            reinit();
        }

        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.outline(px, py, PANEL_W, panelH, BORDER);

        Component title = Component.translatable("pmchat.streams.title");
        context.text(font, title,
                px + (PANEL_W - font.width(title)) / 2, py + 9, TITLE, false);

        if (!pluginPresent() && !startMode) {
            String note = Component.translatable("pmchat.streams.noplugin_note").getString();
            context.text(font, trimTo(note, PANEL_W - 24),
                    px + (PANEL_W - font.width(trimTo(note, PANEL_W - 24))) / 2, py + 19, SUBTLE, false);
        }

        if (!startMode) {
            drawList(context, mouseX, mouseY);
        }

        if (localMsg != null && System.currentTimeMillis() - localMsgAt < 4000) {
            context.text(font, trimTo(localMsg, PANEL_W - 24),
                    px + 12, py + panelH - 34, 0xFFE0574C, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        List<PmServerMedia.LiveStream> streams = sm.liveStreams();
        int listTop = py + headerH + 22;
        if (streams.isEmpty()) {
            context.text(font, Component.translatable("pmchat.streams.empty"),
                    px + 14, listTop + 4, SUBTLE, false);
            return;
        }
        int y = listTop;
        for (PmServerMedia.LiveStream s : streams) {
            context.fill(px + 8, y, px + PANEL_W - 8, y + ROW_H - 4, BTN_BG);
            context.outline(px + 8, y, PANEL_W - 16, ROW_H - 4, BORDER);
            context.text(font, "● " + config.aliasOf(s.player()), px + 14, y + 4, 0xFFE07A6A, false);
            String t = s.title() == null || s.title().isBlank()
                    ? Component.translatable("pmchat.streams.notitle").getString() : s.title();
            context.text(font, trimTo(t, PANEL_W - 24), px + 14, y + 16, LABEL, false);
            y += ROW_H;
        }
    }

    private String trimTo(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private void reinit() {
        init();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
