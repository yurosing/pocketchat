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

    private static final int CELL = 100;
    private static final int GAP = 14;
    private static final int SPARKLES = 4;

    private int PANEL_W = 420;
    private int PANEL_H = 340;

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

        PANEL_W = Math.max(220, Math.min(420, width - 24));
        PANEL_H = Math.max(160, Math.min(380, height - 24));
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

    /** Общий фон карточки: свечение кольцами цвета редкости + дышащий значок + искры на ховере. */
    private void drawCardBase(DrawContext context, int x, int y, int index, String icon, int rarity,
                               boolean hover, boolean dim) {
        long now = System.currentTimeMillis();
        double t = now / 1000.0 + index * 0.37;
        int cx = x + CELL / 2, cy = y + CELL / 2 - 6;

        context.fill(x, y, x + CELL, y + CELL, hover ? 0x48FFFFFF : 0x26FFFFFF);
        context.drawStrokedRectangle(x, y, CELL, CELL, dim ? BTN_BORDER : rarity);

        if (!dim) {
            // Свечение кольцами — пульсирует по фазе, сдвинутой для каждой карточки.
            int glowBase = hover ? 46 : 30;
            for (int ring = 3; ring >= 1; ring--) {
                float pulse = (float) (0.6 + 0.4 * Math.sin(t * 2 + ring));
                int r = 14 + ring * 9;
                fillCircleClamped(context, cx, cy, r, (rarity & 0xFFFFFF) | (((int) (glowBase / ring * pulse)) << 24),
                        x, y, x + CELL, y + CELL);
            }
        }

        float breathe = 1f + 0.12f * (float) Math.sin(t * 2.2);
        float scale = 3.6f * breathe;
        drawScaledCentered(context, icon, cx, cy, scale, dim ? 0xFFB0B0B0 : 0xFFFFFFFF);

        if (hover && !dim) {
            for (int i = 0; i < SPARKLES; i++) {
                double angle = t * 2.4 + i * (Math.PI * 2 / SPARKLES);
                double radius = CELL * 0.42;
                int sx = cx + (int) (Math.cos(angle) * radius);
                int sy = cy + (int) (Math.sin(angle) * radius * 0.7);
                float sparkleAlpha = (float) (0.5 + 0.5 * Math.sin(t * 5 + i * 1.3));
                drawCentered(context, i % 2 == 0 ? "✦" : "·", sx, sy, (rarity & 0xFFFFFF) | ((int) (220 * sparkleAlpha) << 24));
            }
        }
    }

    /** {@link #fillCircle} с обрезкой по прямоугольнику карточки — свечение не должно вылезать на соседей. */
    private static void fillCircleClamped(DrawContext ctx, int cx, int cy, int r, int color, int minX, int minY, int maxX, int maxY) {
        for (int dy = -r; dy <= r; dy++) {
            int yy = cy + dy;
            if (yy < minY || yy >= maxY) continue;
            int dx = (int) Math.sqrt((double) r * r - dy * dy);
            int x0 = Math.max(minX, cx - dx), x1 = Math.min(maxX, cx + dx + 1);
            if (x1 > x0) ctx.fill(x0, yy, x1, yy + 1, color);
        }
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

            drawCardBase(context, x, y, i, g.icon, rarity, hover, !afford);

            String priceStr = PmBackend.formatCoins(g.price);
            context.drawText(textRenderer, priceStr, x + (CELL - textRenderer.getWidth(priceStr)) / 2,
                    y + CELL - 13, afford ? PmBackend.CURRENCY_COLOR : 0xFF9A6A6A, false);

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

        for (int i = scroll; i < got.size(); i++) {
            if (y + CELL > gridBottom) break;
            PmBackend.ReceivedGift g = got.get(i);
            PmBackend.Gift def = PmBackend.giftById(g.giftId);
            String icon = def != null ? def.icon : (g.giftId == null || g.giftId.isEmpty() ? "•" : g.giftId);
            int rarity = PmBackend.rarityColor(def != null ? def.rarity : null);
            boolean hover = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;

            drawCardBase(context, x, y, i, icon, rarity, hover, false);

            // Цветной кружок-бейдж отправителя (первая буква ника) в углу — как в Telegram.
            String from = g.from == null || g.from.isEmpty() ? "?" : g.from;
            int badgeColor = 0xFF000000 | (from.toLowerCase(Locale.ROOT).hashCode() & 0xFFFFFF);
            fillCircle(context, x + CELL - 13, y + 13, 12, badgeColor);
            String initial = from.substring(0, 1).toUpperCase(Locale.ROOT);
            context.drawText(textRenderer, initial, x + CELL - 13 - textRenderer.getWidth(initial) / 2, y + 13 - 4, 0xFFFFFFFF, false);

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
        int cy = py + PANEL_H / 2 - 30;
        double t = System.currentTimeMillis() / 1000.0;

        for (int ring = 4; ring >= 1; ring--) {
            float pulse = (float) (0.6 + 0.4 * Math.sin(t * 1.6 + ring));
            fillCircle(context, cx, cy, 26 + ring * 9, (rarity & 0xFFFFFF) | (((int) (48 / ring * pulse)) << 24));
        }

        for (int i = 0; i < SPARKLES * 2; i++) {
            double angle = t * 1.4 + i * (Math.PI * 2 / (SPARKLES * 2));
            double radius = 62 + 6 * Math.sin(t * 2 + i);
            int sx = cx + (int) (Math.cos(angle) * radius);
            int sy = cy + (int) (Math.sin(angle) * radius * 0.75);
            float sparkleAlpha = (float) (0.5 + 0.5 * Math.sin(t * 4 + i * 1.3));
            drawCentered(context, i % 2 == 0 ? "✦" : "·", sx, sy, (rarity & 0xFFFFFF) | ((int) (220 * sparkleAlpha) << 24));
        }

        float breathe = 1f + 0.08f * (float) Math.sin(t * 2.4);
        drawScaledCentered(context, icon, cx, cy - 6, 6.5f * breathe, 0xFFFFFFFF);

        String name = def != null ? def.name : g.giftId;
        drawCentered(context, name, cx, cy + 58, TITLE);
        Text from = Text.translatable("pmchat.gifts.detail.from", g.from);
        drawCentered(context, from.getString(), cx, cy + 70, LABEL);
        if (g.at > 0) {
            String date = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(new Date(g.at));
            Text when = Text.translatable("pmchat.gifts.detail.when", date);
            drawCentered(context, when.getString(), cx, cy + 82, SUBTLE);
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
