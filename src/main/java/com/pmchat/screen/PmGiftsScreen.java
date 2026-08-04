package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Полноценное окно подарков — большое отдельное окно (открывается из профиля),
 * в духе Telegram: сетка крупных карточек. Два режима:
 * <ul>
 *   <li>{@code catalogMode=false} — просмотр ПОЛУЧЕННЫХ игроком подарков,
 *   клик по карточке открывает подробности (кто подарил, когда);</li>
 *   <li>{@code catalogMode=true} — КАТАЛОГ на покупку (каталог заметно вырос,
 *   в компактный профиль без прокрутки уже не помещался), клик покупает и
 *   сразу дарит выбранный подарок этому игроку.</li>
 * </ul>
 * Обе сетки прокручиваемые — компактный профиль ({@code PmProfileScreen})
 * оставляет только маленькое превью и ссылки сюда.
 */
@Environment(EnvType.CLIENT)
public class PmGiftsScreen extends Screen {

    private static final int CELL = 64;
    private static final int GAP = 8;

    private int PANEL_W = 360;
    private int PANEL_H = 300;

    private final Screen parent;
    private final String player;
    private final boolean catalogMode;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private int BG, BORDER, LABEL, TITLE, SUBTLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private int gridTop, gridBottom, scroll = 0;
    private final List<Object[]> cardRects = new java.util.ArrayList<>(); // {x,y,w,h,index}

    /** Индекс выбранного полученного подарка для показа деталей, -1 — сетка (только не-каталог). */
    private int detailIndex = -1;

    private Text status = Text.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmGiftsScreen(Screen parent, String player, boolean catalogMode) {
        super(catalogMode ? Text.translatable("pmchat.gifts.give.title", player) : Text.translatable("pmchat.profile.gifts"));
        this.parent = parent;
        this.player = player;
        this.catalogMode = catalogMode;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        SUBTLE = t.value;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    private List<PmBackend.ReceivedGift> gifts() {
        return PmBackend.cachedGiftInbox(player);
    }

    private int itemCount() {
        return catalogMode ? PmBackend.cachedCatalog().size() : gifts().size();
    }

    @Override
    protected void init() {
        applyTheme();
        clearChildren();

        PANEL_W = Math.max(220, Math.min(360, width - 24));
        PANEL_H = Math.max(160, Math.min(320, height - 24));
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        gridTop = py + (catalogMode ? 46 : 34);
        gridBottom = py + PANEL_H - 34;

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Text.translatable(detailIndex >= 0 ? "pmchat.gifts.back" : "pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                btn -> {
                    if (detailIndex >= 0) {
                        detailIndex = -1;
                        init();
                    } else {
                        close();
                    }
                }));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, PANEL_H, BORDER);

        Text title = getTitle();
        context.drawText(textRenderer, title, px + (PANEL_W - textRenderer.getWidth(title)) / 2, py + 8, TITLE, false);

        if (catalogMode) {
            Long bal = PmBackend.cachedSelfBalance();
            String balStr = Text.translatable("pmchat.shop.balance", PmBackend.formatCoins(bal != null ? bal : 0L)).getString();
            context.drawText(textRenderer, balStr, px + 12, py + 22, PmBackend.CURRENCY_COLOR, false);
            renderCatalogGrid(context, mouseX, mouseY);
            if (!status.getString().isEmpty()) {
                context.drawText(textRenderer, status, px + (PANEL_W - textRenderer.getWidth(status)) / 2,
                        py + PANEL_H - 46, statusColor, false);
            }
        } else if (detailIndex >= 0 && detailIndex < gifts().size()) {
            renderDetail(context, gifts().get(detailIndex));
        } else {
            renderReceivedGrid(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderCatalogGrid(DrawContext context, int mouseX, int mouseY) {
        cardRects.clear();
        List<PmBackend.Gift> cat = PmBackend.cachedCatalog();
        if (cat.isEmpty()) {
            Text empty = Text.translatable("pmchat.shop.empty");
            context.drawText(textRenderer, empty, px + (PANEL_W - textRenderer.getWidth(empty)) / 2, gridTop + 8, SUBTLE, false);
            return;
        }
        Long selfBal = PmBackend.cachedSelfBalance();

        int cols = Math.max(1, (PANEL_W - 16) / (CELL + GAP));
        int startX = px + (PANEL_W - cols * (CELL + GAP) + GAP) / 2;
        int col = 0;
        int x = startX, y = gridTop;

        for (int i = scroll; i < cat.size(); i++) {
            if (y + CELL > gridBottom) break;
            PmBackend.Gift g = cat.get(i);
            boolean afford = selfBal != null && selfBal >= g.price;
            int rarity = PmBackend.rarityColor(g.rarity);
            boolean hover = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;

            context.fill(x, y, x + CELL, y + CELL, hover ? 0x40FFFFFF : 0x28FFFFFF);
            context.drawStrokedRectangle(x, y, CELL, CELL, afford ? rarity : BTN_BORDER);
            drawScaledCentered(context, g.icon, x + CELL / 2, y + CELL / 2 - 12, 2.2f, 0xFFFFFFFF);

            String priceStr = PmBackend.formatCoins(g.price);
            context.drawText(textRenderer, priceStr, x + (CELL - textRenderer.getWidth(priceStr)) / 2,
                    y + CELL - 12, afford ? PmBackend.CURRENCY_COLOR : 0xFF9A6A6A, false);

            cardRects.add(new Object[]{x, y, CELL, CELL, i});

            col++;
            x += CELL + GAP;
            if (col >= cols) {
                col = 0;
                x = startX;
                y += CELL + GAP;
            }
        }
    }

    private void buy(int catalogIndex) {
        List<PmBackend.Gift> cat = PmBackend.cachedCatalog();
        if (catalogIndex < 0 || catalogIndex >= cat.size()) return;
        PmBackend.Gift g = cat.get(catalogIndex);
        PmBackend.sendGift(player, g.id, (ok, v, err) -> {
            status = ok ? Text.translatable("pmchat.profile.gifts.sent")
                    : Text.translatable("pmchat.profile.gifts.fail", String.valueOf(err));
            statusColor = ok ? 0xFF6FBF8B : 0xFFE0574C;
        });
    }

    private void renderReceivedGrid(DrawContext context, int mouseX, int mouseY) {
        cardRects.clear();
        List<PmBackend.ReceivedGift> got = gifts();
        if (got.isEmpty()) {
            Text empty = Text.translatable("pmchat.profile.gifts.empty");
            context.drawText(textRenderer, empty, px + (PANEL_W - textRenderer.getWidth(empty)) / 2, gridTop + 8, SUBTLE, false);
            return;
        }

        int cols = Math.max(1, (PANEL_W - 16) / (CELL + GAP));
        int startX = px + (PANEL_W - cols * (CELL + GAP) + GAP) / 2;
        int col = 0;
        int x = startX, y = gridTop;
        long now = System.currentTimeMillis();

        for (int i = scroll; i < got.size(); i++) {
            if (y + CELL > gridBottom) break;
            PmBackend.ReceivedGift g = got.get(i);
            PmBackend.Gift def = PmBackend.giftById(g.giftId);
            String icon = def != null ? def.icon : (g.giftId == null || g.giftId.isEmpty() ? "•" : g.giftId);
            int rarity = PmBackend.rarityColor(def != null ? def.rarity : null);
            boolean hover = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;

            context.fill(x, y, x + CELL, y + CELL, hover ? 0x40FFFFFF : 0x28FFFFFF);
            context.drawStrokedRectangle(x, y, CELL, CELL, rarity);
            float bob = hover ? 0 : (float) Math.sin(now / 450.0 + i * 0.7) * 1.5f;
            drawScaledCentered(context, icon, x + CELL / 2, y + CELL / 2 - 8 + (int) bob, 2.2f, 0xFFFFFFFF);

            String from = g.from == null || g.from.isEmpty() ? "?" : g.from;
            int badgeColor = 0xFF000000 | (from.toLowerCase(Locale.ROOT).hashCode() & 0xFFFFFF);
            fillCircle(context, x + CELL - 8, y + 8, 8, badgeColor);
            String initial = from.substring(0, 1).toUpperCase(Locale.ROOT);
            context.drawText(textRenderer, initial, x + CELL - 8 - textRenderer.getWidth(initial) / 2, y + 8 - 4, 0xFFFFFFFF, false);

            cardRects.add(new Object[]{x, y, CELL, CELL, i});

            col++;
            x += CELL + GAP;
            if (col >= cols) {
                col = 0;
                x = startX;
                y += CELL + GAP;
            }
        }

        Text caption = Text.translatable("pmchat.gifts.caption", PmNames.displayString(player));
        context.drawText(textRenderer, caption, px + (PANEL_W - textRenderer.getWidth(caption)) / 2, py + PANEL_H - 40, SUBTLE, false);
    }

    private void renderDetail(DrawContext context, PmBackend.ReceivedGift g) {
        PmBackend.Gift def = PmBackend.giftById(g.giftId);
        String icon = def != null ? def.icon : (g.giftId == null || g.giftId.isEmpty() ? "•" : g.giftId);
        int rarity = PmBackend.rarityColor(def != null ? def.rarity : null);
        int cx = px + PANEL_W / 2;
        int cy = py + PANEL_H / 2 - 20;

        for (int ring = 3; ring >= 1; ring--) {
            fillCircle(context, cx, cy, 20 + ring * 6, (rarity & 0xFFFFFF) | (0x18 * ring << 24));
        }
        drawScaledCentered(context, icon, cx, cy - 10, 4f, 0xFFFFFFFF);

        String name = def != null ? def.name : g.giftId;
        drawCentered(context, name, cx, cy + 34, TITLE);
        Text from = Text.translatable("pmchat.gifts.detail.from", g.from);
        drawCentered(context, from.getString(), cx, cy + 46, LABEL);
        if (g.at > 0) {
            String date = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(new Date(g.at));
            Text when = Text.translatable("pmchat.gifts.detail.when", date);
            drawCentered(context, when.getString(), cx, cy + 58, SUBTLE);
        }
    }

    private void drawCentered(DrawContext ctx, String s, int centerX, int y, int color) {
        ctx.drawText(textRenderer, Text.literal(s), centerX - textRenderer.getWidth(s) / 2, y, color, false);
    }

    /** Текст с масштабом вокруг точки (centerX, y — центр по вертикали). */
    private void drawScaledCentered(DrawContext ctx, String s, int centerX, int y, float scale, int color) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(centerX, y);
        m.scale(scale, scale);
        ctx.drawText(textRenderer, Text.literal(s), -textRenderer.getWidth(s) / 2, -textRenderer.fontHeight / 2, color, false);
        m.popMatrix();
    }

    private static void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) r * r - dy * dy);
            ctx.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (catalogMode || detailIndex < 0) {
            double mx = click.x(), my = click.y();
            for (Object[] rect : cardRects) {
                int x = (int) rect[0], y = (int) rect[1], w = (int) rect[2], h = (int) rect[3];
                int idx = (int) rect[4];
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    if (catalogMode) {
                        buy(idx);
                    } else {
                        detailIndex = idx;
                        init();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!catalogMode && detailIndex >= 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        int cols = Math.max(1, (PANEL_W - 16) / (CELL + GAP));
        int rows = Math.max(1, (gridBottom - gridTop) / (CELL + GAP));
        int visible = cols * rows;
        int maxScroll = Math.max(0, itemCount() - visible);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount) * cols));
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
