package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmHosts;
import com.pmchat.client.PmImages;
import com.pmchat.client.PmMessage;
import com.pmchat.client.PmVoice;
import com.pmchat.client.PmWire;
import net.minecraft.client.renderer.RenderPipelines;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * «Медиа и файлы» с конкретным собеседником (как раздел shared-media в Telegram,
 * открываемый из меню контакта) — три вкладки: Все / ГС (голосовые) / Медиа
 * (фото+видео), список за всю историю переписки. Палитра — общая тема мода.
 */
@Environment(EnvType.CLIENT)
public class PmContactMediaScreen extends Screen {

    private static final int TAB_ALL = 0, TAB_VOICE = 1, TAB_MEDIA = 2;

    private final Screen parent;
    private final String peer;
    private final PmConfig config = PmChatClient.getConfig();

    private int BG, PANEL, BORDER, ROW, ROW_HOVER, TEXT, SUBTLE, ACCENT;
    private int px, py, pw, ph;
    private int tab = TAB_ALL;
    private int scroll = 0;
    private int[] closeRect;
    private final int[] tabRects = new int[TAB_MEDIA + 1];
    private final int[] tabW = new int[TAB_MEDIA + 1];
    private final List<Object[]> rowRects = new ArrayList<>(); // x,y,w,h,msg
    private PmImages.Entry fullscreenImg;

    private final SimpleDateFormat fmt = new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT);

    public PmContactMediaScreen(Screen parent, String peer) {
        super(Component.translatable("pmchat.sharedmedia.title"));
        this.parent = parent;
        this.peer = peer;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = (t.bg & 0xFFFFFF) | 0xC0000000;
        PANEL = t.bg;
        BORDER = t.border;
        ROW = t.btnBg;
        ROW_HOVER = t.btnHover;
        TEXT = t.title;
        SUBTLE = t.label;
        ACCENT = 0xFF6FBF8B;
    }

    @Override
    protected void init() {
        applyTheme();
        pw = Math.min(340, width - 40);
        ph = Math.min(320, height - 60);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    private List<PmMessage> filtered() {
        List<PmMessage> all = PmChatClient.getHistory().messages(peer);
        List<PmMessage> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            PmMessage m = all.get(i);
            boolean voice = isVoice(m);
            boolean media = isMedia(m);
            if (tab == TAB_VOICE && voice) out.add(m);
            else if (tab == TAB_MEDIA && media) out.add(m);
            else if (tab == TAB_ALL && (voice || media)) out.add(m);
        }
        return out;
    }

    private static boolean isVoice(PmMessage m) {
        return m.text != null && m.money <= 0 && PmWire.parseVoice(m.text) != null;
    }

    private static boolean isMedia(PmMessage m) {
        if (m.text == null || m.money > 0) return false;
        return PmWire.parseImg(m.text) != null || PmWire.parseVid(m.text) != null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (fullscreenImg != null) {
            renderFullscreenImage(ctx);
            return;
        }
        ctx.fill(0, 0, width, height, BG);
        ctx.fill(px, py, px + pw, py + ph, PANEL);
        ctx.outline(px, py, pw, ph, BORDER);

        Component title = Component.translatable("pmchat.sharedmedia.title_of", peer);
        ctx.text(font, trim(title.getString(), pw - 40), px + 12, py + 9, TEXT, false);

        String x = "✕";
        int xw = font.width(x) + 8;
        closeRect = new int[]{px + pw - xw - 6, py + 5, xw, 14};
        boolean hovX = in(mouseX, mouseY, closeRect);
        ctx.text(font, x, closeRect[0] + 4, closeRect[1] + 3, hovX ? TEXT : SUBTLE, false);

        // Вкладки
        String[] labels = {
                Component.translatable("pmchat.sharedmedia.tab.all").getString(),
                Component.translatable("pmchat.sharedmedia.tab.voice").getString(),
                Component.translatable("pmchat.sharedmedia.tab.media").getString(),
        };
        int ty = py + 24;
        int tx = px + 10;
        for (int i = 0; i < labels.length; i++) {
            int w = font.width(labels[i]) + 14;
            tabW[i] = w;
            tabRects[i] = tx;
            boolean sel = tab == i;
            boolean hov = mouseY >= ty && mouseY < ty + 16 && mouseX >= tx && mouseX < tx + w;
            ctx.fill(tx, ty, tx + w, ty + 16, sel ? ROW_HOVER : (hov ? ROW : PANEL));
            ctx.outline(tx, ty, w, 16, sel ? ACCENT : BORDER);
            ctx.text(font, labels[i], tx + 7, ty + 4, sel ? TEXT : SUBTLE, false);
            tx += w + 4;
        }

        int listTop = ty + 22;
        int listBottom = py + ph - 8;
        List<PmMessage> items = filtered();
        rowRects.clear();
        if (items.isEmpty()) {
            ctx.text(font, Component.translatable("pmchat.sharedmedia.empty"),
                    px + 12, listTop + 6, SUBTLE, false);
        }
        int rowH = 24;
        int maxScroll = Math.max(0, items.size() * rowH - (listBottom - listTop));
        scroll = Math.min(scroll, maxScroll);
        int y = listTop - scroll;
        ctx.enableScissor(px + 1, listTop, px + pw - 1, listBottom);
        for (PmMessage m : items) {
            if (y + rowH >= listTop && y <= listBottom) {
                boolean hov = mouseX >= px + 6 && mouseX < px + pw - 6 && mouseY >= y && mouseY < y + rowH;
                ctx.fill(px + 6, y, px + pw - 6, y + rowH - 2, hov ? ROW_HOVER : ROW);
                renderRow(ctx, m, px + 8, y + 2, pw - 20, rowH - 4);
                rowRects.add(new Object[]{px + 6, y, pw - 12, rowH, m});
            }
            y += rowH;
        }
        ctx.disableScissor();
    }

    private void renderRow(GuiGraphicsExtractor ctx, PmMessage m, int x, int y, int w, int h) {
        String[] voice = PmWire.parseVoice(m.text);
        String[] img = PmWire.parseImg(m.text);
        String[] vid = PmWire.parseVid(m.text);
        String when = fmt.format(new Date(m.time));
        int iconSz = 16;
        if (voice != null) {
            PmIcons.draw(ctx, PmIcons.VOICE, x, y + (h - iconSz) / 2, iconSz, iconSz, 0xFF9CC4DC);
            boolean playing = PmVoice.isPlaying(voice[1]);
            String label = Component.translatable("pmchat.sharedmedia.voice_dur", fmtDuration(voice[2])).getString();
            ctx.text(font, label, x + iconSz + 6, y + 2, playing ? ACCENT : TEXT, false);
        } else if (img != null) {
            PmImages.Entry e = PmImages.get(img[0], img[1]);
            if (e.state == PmImages.State.READY && e.currentTexture() != null) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, e.currentTexture(), x, y, 0f, 0f,
                        iconSz, iconSz, e.width, e.height, e.width, e.height);
            } else {
                PmIcons.draw(ctx, PmIcons.PHOTO, x, y + (h - iconSz) / 2, iconSz, iconSz, 0xFF9CC4DC);
            }
            ctx.text(font, Component.translatable("pmchat.sharedmedia.photo"),
                    x + iconSz + 6, y + 2, TEXT, false);
        } else if (vid != null) {
            PmIcons.draw(ctx, PmIcons.PLAY, x, y + (h - iconSz) / 2, iconSz, iconSz, 0xFF9CC4DC);
            ctx.text(font, Component.translatable("pmchat.sharedmedia.video"),
                    x + iconSz + 6, y + 2, TEXT, false);
        }
        ctx.text(font, when, x + w - font.width(when), y + 2, SUBTLE, false);
    }

    /** Затемнённый оверлей с картинкой, вписанной в окно (как в PmScreen). */
    private void renderFullscreenImage(GuiGraphicsExtractor ctx) {
        PmImages.Entry e = fullscreenImg;
        ctx.fill(0, 0, width, height, 0xE6000000);
        if (e.state == PmImages.State.READY && e.currentTexture() != null && e.width > 0 && e.height > 0) {
            float scale = Math.min((width - 40f) / e.width, (height - 60f) / e.height);
            scale = Math.min(scale, 6f);
            int w = Math.max(1, Math.round(e.width * scale));
            int h = Math.max(1, Math.round(e.height * scale));
            int ix = (width - w) / 2;
            int iy = (height - h) / 2;
            ctx.blit(RenderPipelines.GUI_TEXTURED, e.currentTexture(), ix, iy, 0f, 0f,
                    w, h, e.width, e.height, e.width, e.height);
        }
        Component hint = Component.translatable("pmchat.image.close");
        ctx.text(font, hint, (width - font.width(hint)) / 2, height - 16, 0xFFB8C6CE, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (fullscreenImg != null) {
            fullscreenImg = null;
            return true;
        }
        if (in(mx, my, closeRect)) {
            close();
            return true;
        }
        int ty = py + 24;
        for (int i = 0; i < tabW.length; i++) {
            if (my >= ty && my < ty + 16 && mx >= tabRects[i] && mx < tabRects[i] + tabW[i]) {
                if (tab != i) {
                    tab = i;
                    scroll = 0;
                }
                return true;
            }
        }
        for (Object[] r : rowRects) {
            if (mx >= (int) r[0] && mx < (int) r[0] + (int) r[2]
                    && my >= (int) r[1] && my < (int) r[1] + (int) r[3]) {
                onRowClick((PmMessage) r[4]);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void onRowClick(PmMessage m) {
        String[] voice = PmWire.parseVoice(m.text);
        if (voice != null) {
            PmVoice.togglePlay(voice[0], voice[1]);
            return;
        }
        String[] img = PmWire.parseImg(m.text);
        if (img != null) {
            PmImages.Entry e = PmImages.get(img[0], img[1]);
            if (e.state == PmImages.State.READY && e.currentTexture() != null) {
                fullscreenImg = e;
            } else {
                try {
                    Util.getOperatingSystem().open(PmHosts.baseUrl(img[0]) + img[1]);
                } catch (Exception ignored) {
                }
            }
            return;
        }
        String[] vid = PmWire.parseVid(m.text);
        if (vid != null) {
            try {
                Util.getOperatingSystem().open(PmHosts.baseUrl(vid[0]) + vid[1]);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        scroll = Math.max(0, scroll - (int) Math.signum(v) * 24);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (fullscreenImg != null && input.key() == GLFW.GLFW_KEY_ESCAPE) {
            fullscreenImg = null;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean in(int mx, int my, int[] r) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private static String fmtDuration(String secondsStr) {
        int secs;
        try {
            secs = Integer.parseInt(secondsStr);
        } catch (NumberFormatException e) {
            return secondsStr;
        }
        return String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
    }

    private String trim(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
