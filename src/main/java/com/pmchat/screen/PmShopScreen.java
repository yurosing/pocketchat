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

import java.util.List;

/**
 * Магазин возможностей — оформление и функции за монеты PocketChat, на
 * ограниченный срок (см. {@code GET /v1/shop}). Открывается кнопкой «$» внизу
 * мессенджера. Если среди своих покупок активна фича {@code paid_dm}, здесь же
 * можно выставить свою цену за входящее ЛС.
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

    private TextFieldWidget dmPriceField;
    private Text status = Text.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmShopScreen(Screen parent) {
        super(Text.translatable("pmchat.shop.title"));
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
        clearChildren();

        PANEL_W = Math.max(220, Math.min(320, width - 24));
        boolean dmActive = PmBackend.hasActiveFeature("paid_dm");
        int extra = dmActive ? 44 : 0;
        int listRows = Math.max(1, PmBackend.cachedShopItems().size());
        panelH = Math.min(46 + Math.min(listRows, 5) * ROW_H + extra + 30, height - 24);
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        listTop = py + 40;
        listBottom = py + panelH - 30 - extra;

        if (dmActive) {
            int fx = px + 12;
            int fw = PANEL_W - 24;
            int fy = listBottom + 6;
            dmPriceField = new TextFieldWidget(textRenderer, fx, fy, fw - 70, 16, Text.translatable("pmchat.shop.dmprice.hint"));
            dmPriceField.setMaxLength(8);
            PmBackend.AccountInfo self = PmBackend.cachedAccountInfo(PmChatClient.selfName());
            dmPriceField.setText(self != null ? String.valueOf(self.dmPrice) : "");
            String hint = Text.translatable("pmchat.shop.dmprice.hint").getString();
            dmPriceField.setSuggestion(dmPriceField.getText().isEmpty() ? hint : "");
            dmPriceField.setChangedListener(s -> dmPriceField.setSuggestion(s.isEmpty() ? hint : ""));
            addDrawableChild(dmPriceField);
            addDrawableChild(FlatButton.centered(textRenderer, fx + fw - 66, fy, 66, 16,
                    Text.translatable("pmchat.shop.dmprice.save"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                    btn -> saveDmPrice()));
        }

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + panelH - 22, 80, 16,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    private void saveDmPrice() {
        long price;
        try {
            price = Long.parseLong(dmPriceField.getText().trim());
        } catch (NumberFormatException e) {
            setStatus(Text.translatable("pmchat.admin.badamount"), 0xFFE07A6A);
            return;
        }
        if (price < 0) return;
        PmBackend.setDmPrice(price, (ok, v, err) ->
                setStatus(ok ? Text.translatable("pmchat.admin.ok") : Text.translatable("pmchat.admin.fail", String.valueOf(err)),
                        ok ? 0xFF8FD8A8 : 0xFFE07A6A));
    }

    private void setStatus(Text text, int color) {
        status = text;
        statusColor = color;
    }

    private void buy(long itemId) {
        PmBackend.buyShopItem(itemId, (ok, v, err) -> {
            if (ok) {
                setStatus(Text.translatable("pmchat.admin.ok"), 0xFF8FD8A8);
                init(); // фича могла включиться — перестроить (появится поле цены)
            } else {
                setStatus(Text.translatable("pmchat.admin.fail", String.valueOf(err)), 0xFFE07A6A);
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, panelH, BORDER);

        Text title = getTitle();
        context.drawText(textRenderer, title, px + (PANEL_W - textRenderer.getWidth(title)) / 2, py + 8, TITLE, false);

        Long bal = PmBackend.cachedSelfBalance();
        String balStr = Text.translatable("pmchat.shop.balance", bal != null ? bal : 0L).getString();
        context.drawText(textRenderer, balStr, px + 12, py + 22, SUBTLE, false);

        buyRects.clear();
        List<PmBackend.ShopItem> items = PmBackend.cachedShopItems();
        int y = listTop;
        for (int i = scroll; i < items.size() && y + ROW_H <= listBottom; i++) {
            PmBackend.ShopItem item = items.get(i);
            context.fill(px + 8, y, px + PANEL_W - 8, y + ROW_H - 2, 0x22FFFFFF);
            context.drawText(textRenderer, item.name, px + 12, y + 3, LABEL, false);
            String desc = trim(item.description, PANEL_W - 24 - 70);
            context.drawText(textRenderer, desc, px + 12, y + 15, SUBTLE, false);

            int btnW = 62, btnH = ROW_H - 8;
            int btnX = px + PANEL_W - 12 - btnW, btnY = y + 4;
            boolean hover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, hover ? BTN_HOVER : BTN_BG);
            context.drawStrokedRectangle(btnX, btnY, btnW, btnH, BORDER);
            String priceStr = item.price + "/" + item.durationDays + "d";
            context.drawText(textRenderer, priceStr, btnX + (btnW - textRenderer.getWidth(priceStr)) / 2, btnY + 4, 0xFFF0C34E, false);
            buyRects.add(new Object[]{btnX, btnY, btnW, btnH, item.id});

            y += ROW_H;
        }
        if (items.isEmpty()) {
            context.drawText(textRenderer, Text.translatable("pmchat.shop.empty"), px + 12, listTop + 2, SUBTLE, false);
        }

        if (dmPriceField != null) {
            context.drawText(textRenderer, Text.translatable("pmchat.shop.dmprice.label"), px + 12, listBottom - 8, TITLE, false);
        }

        if (!status.getString().isEmpty()) {
            context.drawText(textRenderer, status, px + (PANEL_W - textRenderer.getWidth(status)) / 2, py + panelH - 40, statusColor, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (textRenderer.getWidth(text) <= maxWidth) return text;
        return textRenderer.trimToWidth(text, Math.max(0, maxWidth - textRenderer.getWidth("…"))) + "…";
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
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
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
