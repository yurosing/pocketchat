package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Магазин возможностей — оформление и функции за монеты PocketChat, на
 * ограниченный срок (см. {@code GET /v1/shop}). Открывается кнопкой «Ⓒ» внизу
 * мессенджера. Своя цена за входящее ЛС настраивается отдельно, в Настройках →
 * «Приватность» (см. {@link PmSettingsScreen}) — здесь только покупка позиций.
 */
@Environment(EnvType.CLIENT)
public class PmShopScreen extends Screen {

    private static final int ROW_H = 34;

    private int PANEL_W = 320;
    private int panelH;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, SUBTLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private int listTop, listBottom, scroll = 0;
    private final List<Object[]> buyRects = new java.util.ArrayList<>(); // {x,y,w,h,itemId}

    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmShopScreen(Screen parent) {
        super(Component.translatable("pmchat.shop.title"));
        this.parent = parent;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        SUBTLE = t.value;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    @Override
    protected void init() {
        applyTheme();
        clearWidgets();

        PANEL_W = Math.max(220, Math.min(320, width - 24));
        int listRows = Math.max(1, PmBackend.cachedShopItems().size());
        panelH = Math.min(46 + Math.min(listRows, 5) * ROW_H + 30, height - 24);
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        listTop = py + 40;
        listBottom = py + panelH - 30;

        addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 40, py + panelH - 22, 80, 16,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> onClose()));
    }

    private void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
    }

    private void buy(long itemId) {
        PmBackend.buyShopItem(itemId, (ok, v, err) -> {
            if (ok) {
                setStatus(Component.translatable("pmchat.admin.ok"), 0xFF8FD8A8);
                init();
            } else {
                setStatus(Component.translatable("pmchat.admin.fail", String.valueOf(err)), 0xFFE07A6A);
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.outline(px, py, PANEL_W, panelH, BORDER);

        Component title = getTitle();
        context.text(font, title, px + (PANEL_W - font.width(title)) / 2, py + 8, TITLE, false);

        Long bal = PmBackend.cachedSelfBalance();
        String balStr = Component.translatable("pmchat.shop.balance", PmBackend.formatCoins(bal != null ? bal : 0L)).getString();
        context.text(font, balStr, px + 12, py + 22, PmBackend.CURRENCY_COLOR, false);

        buyRects.clear();
        List<PmBackend.ShopItem> items = PmBackend.cachedShopItems();
        int y = listTop;
        for (int i = scroll; i < items.size() && y + ROW_H <= listBottom; i++) {
            PmBackend.ShopItem item = items.get(i);
            context.fill(px + 8, y, px + PANEL_W - 8, y + ROW_H - 2, 0x22FFFFFF);
            context.text(font, item.name, px + 12, y + 3, LABEL, false);
            String desc = trim(item.description, PANEL_W - 24 - 70);
            context.text(font, desc, px + 12, y + 15, SUBTLE, false);

            int btnW = 62, btnH = ROW_H - 8;
            int btnX = px + PANEL_W - 12 - btnW, btnY = y + 4;
            boolean hover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, hover ? BTN_HOVER : BTN_BG);
            context.outline(btnX, btnY, btnW, btnH, BORDER);
            String priceStr = PmBackend.formatCoins(item.price) + "/" + item.durationDays + "d";
            context.text(font, priceStr, btnX + (btnW - font.width(priceStr)) / 2, btnY + 4, PmBackend.CURRENCY_COLOR, false);
            buyRects.add(new Object[]{btnX, btnY, btnW, btnH, item.id});

            y += ROW_H;
        }
        if (items.isEmpty()) {
            context.text(font, Component.translatable("pmchat.shop.empty"), px + 12, listTop + 2, SUBTLE, false);
        }

        if (!status.getString().isEmpty()) {
            context.text(font, status, px + (PANEL_W - font.width(status)) / 2, py + panelH - 40, statusColor, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        for (Object[] rect : buyRects) {
            int x = (int) rect[0], y = (int) rect[1], w = (int) rect[2], h = (int) rect[3];
            long id = (long) rect[4];
            if (mx >= x && mx < x + w && my >= y && my < y + h) {
                buy(id);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int visible = Math.max(1, (listBottom - listTop) / ROW_H);
        int maxScroll = Math.max(0, PmBackend.cachedShopItems().size() - visible);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
        return true;
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
