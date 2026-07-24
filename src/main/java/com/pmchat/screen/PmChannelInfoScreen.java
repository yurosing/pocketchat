package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 3.2: экран управления публичным каналом (аналог инфо-панели Telegram-канала).
 * Владельцу — код приглашения, список подписчиков с назначением админов и
 * удаление канала; остальным — описание, число подписчиков и выход из канала.
 */
@Environment(EnvType.CLIENT)
public class PmChannelInfoScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int ROW_H = 18;

    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    private final Screen parent;
    private final String id;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py, panelH;
    private int scroll = 0;
    private int maxScroll = 0;
    private boolean deleteConfirm = false;
    private long copiedAt = -1;

    /** Кнопки строк подписчиков: {x,y,w,h,ник}. */
    private final List<Object[]> adminToggleRects = new ArrayList<>();
    private int[] copyRect;

    public PmChannelInfoScreen(Screen parent, String id) {
        super(Text.translatable("pmchat.broadcast.info.title"));
        this.parent = parent;
        this.id = id;
    }

    private PmConfig.PmBroadcast broadcast() {
        return config.findBroadcast(id);
    }

    private boolean isOwner() {
        PmConfig.PmBroadcast b = broadcast();
        return b != null && b.owner != null && b.owner.equalsIgnoreCase(PmChatClient.selfNamePublic());
    }

    @Override
    protected void init() {
        applyTheme();
        clearChildren();
        panelH = Math.min(280, height - 30);
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        addDrawableChild(FlatButton.centered(textRenderer, px + 10, py + panelH - 24, 70, 18,
                Text.translatable("pmchat.filters.back"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));

        PmConfig.PmBroadcast b = broadcast();
        if (b == null) return;

        if (isOwner()) {
            addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W - 130, py + panelH - 24, 120, 18,
                    Text.translatable(deleteConfirm ? "pmchat.broadcast.delete.confirm" : "pmchat.broadcast.delete"),
                    0xFF6A2E2E, 0xFF7A3636, 0xFF8F4242, 0xFFEED6D6, btn -> {
                        if (!deleteConfirm) {
                            deleteConfirm = true;
                            reinit();
                        } else {
                            PmChatClient.deleteBroadcast(id);
                            closeToParent();
                        }
                    }));
        } else {
            addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W - 130, py + panelH - 24, 120, 18,
                    Text.translatable("pmchat.broadcast.leave"),
                    0xFF6A2E2E, 0xFF7A3636, 0xFF8F4242, 0xFFEED6D6, btn -> {
                        PmChatClient.leaveBroadcast(id);
                        closeToParent();
                    }));
        }
    }

    private void reinit() {
        init();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        ctx.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        ctx.drawStrokedRectangle(px, py, PANEL_W, panelH, BORDER);

        PmConfig.PmBroadcast b = broadcast();
        if (b == null) {
            Text gone = Text.translatable("pmchat.broadcast.gone");
            ctx.drawText(textRenderer, gone, px + (PANEL_W - textRenderer.getWidth(gone)) / 2, py + 20, LABEL, false);
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }

        ctx.drawText(textRenderer, b.name, px + (PANEL_W - textRenderer.getWidth(b.name)) / 2, py + 8, TITLE, false);

        int y = py + 24;
        boolean owner = isOwner();
        if (!b.description.isBlank()) {
            for (String line : wrap(b.description, PANEL_W - 24)) {
                ctx.drawText(textRenderer, line, px + 12, y, LABEL, false);
                y += 10;
            }
            y += 4;
        }

        int subs = owner ? b.subscribers.size() : b.knownSubscribers;
        ctx.drawText(textRenderer, Text.translatable("pmchat.broadcast.subs", subs), px + 12, y, VALUE, false);
        y += ROW_H;

        adminToggleRects.clear();
        copyRect = null;

        if (owner) {
            String code = PmChatClient.broadcastInviteCode(b);
            ctx.drawText(textRenderer, Text.translatable("pmchat.broadcast.invitecode"), px + 12, y, LABEL, false);
            y += 10;
            ctx.fill(px + 12, y, px + PANEL_W - 60, y + 14, BTN_BG);
            ctx.drawStrokedRectangle(px + 12, y, PANEL_W - 72, 14, BTN_BORDER);
            ctx.drawText(textRenderer, trim(code, PANEL_W - 80), px + 16, y + 3, VALUE, false);
            boolean hov = mouseX >= px + PANEL_W - 56 && mouseX < px + PANEL_W - 12 && mouseY >= y && mouseY < y + 14;
            ctx.fill(px + PANEL_W - 56, y, px + PANEL_W - 12, y + 14, hov ? BTN_HOVER : BTN_BG);
            ctx.drawStrokedRectangle(px + PANEL_W - 56, y, 44, 14, BTN_BORDER);
            Text copyLbl = Text.translatable(copiedAt > 0 && System.currentTimeMillis() - copiedAt < 1500
                    ? "pmchat.broadcast.copied" : "pmchat.broadcast.copy");
            ctx.drawText(textRenderer, copyLbl, px + PANEL_W - 52, y + 3, VALUE, false);
            copyRect = new int[]{px + PANEL_W - 56, y, 44, 14};
            y += 20;

            ctx.drawText(textRenderer, Text.translatable("pmchat.broadcast.subscribers"), px + 12, y, LABEL, false);
            y += 10;

            int listTop = y;
            int listBottom = py + panelH - 30;
            ctx.enableScissor(px + 2, listTop, px + PANEL_W - 2, listBottom);
            int ry = listTop - scroll;
            int total = 0;
            for (String name : b.subscribers) {
                if (ry + ROW_H >= listTop && ry <= listBottom) {
                    boolean admin = b.admins.stream().anyMatch(a -> a.equalsIgnoreCase(name));
                    ctx.fill(px + 12, ry, px + PANEL_W - 12, ry + ROW_H - 2, 0xFF1B2530);
                    ctx.drawText(textRenderer, trim(name, 150), px + 16, ry + 4, VALUE, false);
                    int bx = px + PANEL_W - 90;
                    boolean bhov = mouseX >= bx && mouseX < bx + 78 && mouseY >= ry && mouseY < ry + ROW_H - 2;
                    ctx.fill(bx, ry + 2, bx + 78, ry + ROW_H - 4, bhov ? BTN_HOVER : BTN_BG);
                    Text toggleLbl = Text.translatable(admin ? "pmchat.broadcast.demote" : "pmchat.broadcast.promote");
                    ctx.drawText(textRenderer, toggleLbl, bx + 4, ry + 4, admin ? 0xFF8FD8A8 : VALUE, false);
                    adminToggleRects.add(new Object[]{bx, ry + 2, 78, ROW_H - 4, name});
                }
                ry += ROW_H;
                total += ROW_H;
            }
            ctx.disableScissor();
            maxScroll = Math.max(0, total - (listBottom - listTop));
            if (scroll > maxScroll) scroll = maxScroll;
            if (b.subscribers.isEmpty()) {
                ctx.drawText(textRenderer, Text.translatable("pmchat.broadcast.nosubs"), px + 16, listTop + 4, LABEL, false);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (textRenderer.getWidth(cur.toString() + c) > maxW && !cur.isEmpty()) {
                out.add(cur.toString());
                cur = new StringBuilder();
            }
            cur.append(c);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    private String trim(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        return textRenderer.trimToWidth(s, Math.max(0, maxW - textRenderer.getWidth("…"))) + "…";
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (copyRect != null && mx >= copyRect[0] && mx < copyRect[0] + copyRect[2]
                && my >= copyRect[1] && my < copyRect[1] + copyRect[3]) {
            PmConfig.PmBroadcast b = broadcast();
            if (b != null) {
                MinecraftClient.getInstance().keyboard.setClipboard(PmChatClient.broadcastInviteCode(b));
                copiedAt = System.currentTimeMillis();
            }
            return true;
        }
        for (Object[] r : adminToggleRects) {
            int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
            if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                String name = (String) r[4];
                PmConfig.PmBroadcast b = broadcast();
                if (b != null) {
                    boolean admin = b.admins.stream().anyMatch(a -> a.equalsIgnoreCase(name));
                    if (admin) PmChatClient.revokeBroadcastAdmin(id, name);
                    else PmChatClient.grantBroadcastAdmin(id, name);
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(v) * 22));
        return true;
    }

    private void closeToParent() {
        MinecraftClient.getInstance().setScreen(parent instanceof PmScreen ? new PmScreen() : parent);
    }

    @Override
    public void close() {
        closeToParent();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
