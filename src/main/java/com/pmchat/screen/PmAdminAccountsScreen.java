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

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Список зарегистрированных аккаунтов бэкенда PocketChat (для админ-панели):
 * поиск по нику, клик по строке подставляет ник в вызвавший экран.
 */
@Environment(EnvType.CLIENT)
public class PmAdminAccountsScreen extends Screen {

    private static final int ROW_H = 24;

    /** Не static final — подгоняются под размер экрана в init() (GUI Scale 4 и т.п.). */
    private int PANEL_W = 260;
    private int PANEL_H = 220;

    private final Screen parent;
    private final Consumer<String> onPick;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private EditBox searchField;
    private List<PmBackend.AdminAccount> accounts = Collections.emptyList();
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;
    private int scroll = 0;
    private int listTop, listBottom;

    public PmAdminAccountsScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("pmchat.admin.accounts.title"));
        this.parent = parent;
        this.onPick = onPick;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    @Override
    protected void init() {
        applyTheme();
        clearChildren();
        PANEL_W = Math.max(180, Math.min(260, width - 24));
        PANEL_H = Math.min(220, height - 16);
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        int fx = px + 16;
        int fw = PANEL_W - 32;
        int y = py + 26;

        searchField = new EditBox(textRenderer, fx, y, fw, 16, Component.translatable("pmchat.admin.accounts.search"));
        String hint = Component.translatable("pmchat.admin.accounts.search").getString();
        searchField.setSuggestion(hint);
        searchField.setResponder(s -> {
            searchField.setSuggestion(s.isEmpty() ? hint : null);
            load();
        });
        addDrawableChild(searchField);

        listTop = y + 22;
        listBottom = py + PANEL_H - 44;

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));

        load();
    }

    private void load() {
        status = Component.translatable("pmchat.admin.accounts.loading");
        statusColor = 0xFFAAAAAA;
        String q = searchField != null ? searchField.getValue() : "";
        PmBackend.adminListAccounts(q, (ok, list, err) -> {
            if (ok) {
                accounts = list;
                scroll = 0;
                status = accounts.isEmpty() ? Component.translatable("pmchat.admin.accounts.empty") : Component.empty();
            } else {
                accounts = Collections.emptyList();
                status = Component.translatable("pmchat.admin.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, BG);
        context.outline(px, py, PANEL_W, PANEL_H, BORDER);

        context.text(textRenderer, getTitle(), px + (PANEL_W - textRenderer.getWidth(getTitle())) / 2, py + 8, TITLE, false);

        int y = listTop;
        int visible = accounts.size();
        for (int i = scroll; i < visible && y + ROW_H <= listBottom; i++) {
            PmBackend.AdminAccount a = accounts.get(i);
            boolean hovered = mouseX >= px + 8 && mouseX < px + PANEL_W - 8 && mouseY >= y && mouseY < y + ROW_H;
            if (hovered) context.fill(px + 6, y, px + PANEL_W - 6, y + ROW_H - 1, 0x22FFFFFF);

            String badge = (a.official ? "★ " : a.verified ? "✓ " : "");
            context.text(textRenderer, badge + a.username, px + 10, y + 2,
                    a.official ? 0xFFF0C34E : a.verified ? 0xFF4CC26A : LABEL, false);
            String balanceStr = String.valueOf(a.balance);
            context.text(textRenderer, balanceStr,
                    px + PANEL_W - 14 - textRenderer.getWidth(balanceStr), y + 2, VALUE, false);
            net.minecraft.network.chat.Component lastSeen = PmBackend.humanizeLastSeen(a.lastSeenAt, config.preciseLastSeen && a.sharePrecise);
            context.text(textRenderer, lastSeen, px + 10, y + 13, LABEL, false);
            y += ROW_H;
        }

        if (!status.getString().isEmpty()) {
            context.text(textRenderer, status, px + 10, listTop + 2, statusColor, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (mouseX >= px + 8 && mouseX < px + PANEL_W - 8 && mouseY >= listTop && mouseY < listBottom) {
            int idx = scroll + (int) ((mouseY - listTop) / ROW_H);
            if (idx >= 0 && idx < accounts.size()) {
                onPick.accept(accounts.get(idx).username);
                close();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int visible = (listBottom - listTop) / ROW_H;
        int maxScroll = Math.max(0, accounts.size() - visible);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
