package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmServerMedia;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * «Стримы» — список игроков, которые сейчас стримят (внешний сервис,
 * Twitch/YouTube — мод только показывает статус+ссылку, видео сам не тянет),
 * кнопка «начать/закончить стрим» для себя и донат Vault-монетами стримеру.
 * Кнопки видны и без серверного плагина (иначе нечего было бы нажать) — но
 * реально объявить стрим другим игрокам и задонатить можно только когда
 * плагин установлен: без него при попытке показывается пояснение вместо
 * молчаливого ничегонеделания.
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

    // Диалог доната
    private String donateTarget = null;
    private EditBox amountField;

    /** {x,y,w,h,player} — кнопки доната в списке. */
    private final List<Object[]> donateRects = new ArrayList<>();

    // Локальное сообщение (напр. «нужен плагин») — не путать с sm.lastDonateMsg
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
        donateRects.clear();
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

        if (!startMode && donateTarget == null) {
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
        } else if (donateTarget != null) {
            int fy = py + headerH + 22;
            amountField = new EditBox(font, px + 16, fy, PANEL_W - 32, 16,
                    Component.translatable("pmchat.streams.amounthint"));
            amountField.setMaxLength(12);
            addRenderableWidget(amountField);

            addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 90, fy + 24, 84, 18,
                    Component.translatable("pmchat.streams.cancel"),
                    BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> {
                        donateTarget = null;
                        reinit();
                    }));
            addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 + 6, fy + 24, 84, 18,
                    Component.translatable("pmchat.streams.donate"),
                    0xFF6B4A1E, 0xFF7E5824, 0xFFB98A3A, 0xFFF0D8A0, btn -> {
                        double amount = parseAmount(amountField.getValue());
                        if (amount > 0) sm.donate(donateTarget, amount);
                        donateTarget = null;
                        reinit();
                    }).withIcon(PmIcons.MONEY));
        }

        addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 40, py + panelH - 24, 80, 18,
                Component.translatable("pmchat.settings.done"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> onClose()));
    }

    private void showLocalMsg(String msg) {
        localMsg = msg;
        localMsgAt = System.currentTimeMillis();
    }

    private static double parseAmount(String s) {
        try {
            double d = Double.parseDouble(s.trim().replace(',', '.'));
            return Double.isFinite(d) ? d : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (sm.streamVersion() != lastSeenVersion && !startMode && donateTarget == null) {
            reinit();
        }

        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.outline(px, py, PANEL_W, panelH, BORDER);

        Component title = Component.translatable("pmchat.streams.title");
        context.text(font, title,
                px + (PANEL_W - font.width(title)) / 2, py + 9, TITLE, false);

        if (!pluginPresent() && !startMode && donateTarget == null) {
            String note = Component.translatable("pmchat.streams.noplugin_note").getString();
            context.text(font, trimTo(note, PANEL_W - 24),
                    px + (PANEL_W - font.width(trimTo(note, PANEL_W - 24))) / 2, py + 19, SUBTLE, false);
        }

        if (!startMode && donateTarget == null) {
            drawList(context, mouseX, mouseY);
        }

        String rmsg = sm.lastDonateMsg();
        if (rmsg != null && System.currentTimeMillis() - sm.lastDonateAt() < 4000) {
            context.text(font, trimTo(rmsg, PANEL_W - 24),
                    px + 12, py + panelH - 34, sm.lastDonateOk() ? 0xFF6FBF8B : 0xFFE0574C, false);
        } else if (localMsg != null && System.currentTimeMillis() - localMsgAt < 4000) {
            context.text(font, trimTo(localMsg, PANEL_W - 24),
                    px + 12, py + panelH - 34, 0xFFE0574C, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        donateRects.clear();
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
            context.text(font, trimTo(t, PANEL_W - 100), px + 14, y + 16, LABEL, false);

            boolean self = s.player().equalsIgnoreCase(PmChatClient.selfName());
            if (!self) {
                int bx = px + PANEL_W - 70, by = y + 6, bw = 56, bh = 18;
                boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
                context.fill(bx, by, bx + bw, by + bh, hov ? BTN_HOVER : BTN_BG);
                context.outline(bx, by, bw, bh, BTN_BORDER);
                String lbl = Component.translatable("pmchat.streams.donate").getString();
                context.text(font, lbl, bx + (bw - font.width(lbl)) / 2, by + 5,
                        0xFFF0C34E, false);
                donateRects.add(new Object[]{bx, by, bw, bh, s.player()});
            }
            y += ROW_H;
        }
    }

    private String trimTo(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (!startMode && donateTarget == null && pluginPresent()) {
            int mx = (int) click.x(), my = (int) click.y();
            for (Object[] r : donateRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                    donateTarget = (String) r[4];
                    reinit();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void reinit() {
        init();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
