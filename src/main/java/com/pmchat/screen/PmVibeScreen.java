package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmVibe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Выбор трека для «общего вайба» (5.8) — список локальных файлов из
 * config/pmchat-vibe (сам туда кладёшь WAV/AU/AIFF). Клик — запускает у
 * себя и зовёт собеседника включить тот же файл (см. {@link PmVibe}).
 */
@Environment(EnvType.CLIENT)
public class PmVibeScreen extends Screen {

    private final Screen parent;
    private final String target;
    private final PmConfig config = PmChatClient.getConfig();

    private int PANEL_W = 240;
    private int PANEL_H = 200;
    private int px, py;
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private List<Path> tracks = List.of();
    private final List<int[]> rowRects = new ArrayList<>(); // x,y,w,h

    public PmVibeScreen(Screen parent, String target) {
        super(Text.translatable("pmchat.vibe.title"));
        this.parent = parent;
        this.target = target;
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
        PANEL_W = Math.max(200, Math.min(240, width - 24));
        PANEL_H = Math.min(200, height - 16);
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;
        tracks = PmVibe.listTracks();

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + PANEL_H - 22, 80, 16,
                Text.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, PANEL_H, BORDER);

        context.drawText(textRenderer, getTitle(), px + (PANEL_W - textRenderer.getWidth(getTitle())) / 2, py + 8, TITLE, false);

        int listTop = py + 24;
        int listBottom = py + PANEL_H - 28;
        int fx = px + 12;
        int fw = PANEL_W - 24;

        rowRects.clear();
        if (tracks.isEmpty()) {
            context.drawText(textRenderer, trim(Text.translatable("pmchat.vibe.empty").getString(), fw), fx, listTop, LABEL, false);
        } else {
            int y = listTop;
            for (Path f : tracks) {
                if (y + 15 > listBottom) break;
                boolean hov = mouseX >= fx && mouseX < fx + fw && mouseY >= y && mouseY < y + 14;
                context.fill(fx, y, fx + fw, y + 14, hov ? BTN_HOVER : BTN_BG);
                context.drawStrokedRectangle(fx, y, fw, 14, BTN_BORDER);
                context.drawText(textRenderer, "♪ " + trim(f.getFileName().toString(), fw - 12), fx + 5, y + 3, VALUE, false);
                rowRects.add(new int[]{fx, y, fw, 14});
                y += 16;
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private String trim(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        for (int i = 0; i < rowRects.size(); i++) {
            int[] r = rowRects.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                PmVibe.startForConversation(target, tracks.get(i));
                close();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
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
