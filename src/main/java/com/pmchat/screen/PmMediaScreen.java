package com.pmchat.screen;

import com.pmchat.client.PmMedia;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * NEW (5.3): экран «Медиа» — браузер плейлистов. Показывает папки с музыкой
 * из {@code config/pmchat-music} (и отдельные аудиофайлы): клик по папке
 * запускает её как плейлист, клик по треку — играет с него. Сверху —
 * то же свёрнутое окошко с управлением, что и в углу (PmMedia.renderMini),
 * так что паузой/перемоткой/следующим можно рулить прямо отсюда.
 */
public class PmMediaScreen extends Screen {

    private static final int BG = 0xF00B120F;
    private static final int PANEL = 0xFF101A16;
    private static final int BORDER = 0xFF2E5C48;
    private static final int ROW = 0xFF16241E;
    private static final int ROW_HOVER = 0xFF23423A;
    private static final int TEXT = 0xFFEDF3F0;
    private static final int SUBTLE = 0xFF7FA694;

    private int px, py, pw, ph;
    private int scroll = 0;
    private final List<Object[]> rowRects = new ArrayList<>(); // x,y,w,h,type,payload
    private int[] openFolderRect;

    public PmMediaScreen() {
        super(Text.translatable("pmchat.media.title"));
    }

    @Override
    protected void init() {
        pw = Math.min(360, width - 40);
        ph = Math.min(300, height - 60);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Тёмная заливка вместо renderBackground(): его блюр падает
        // «Can only blur once per frame», если кадр уже размывался.
        ctx.fill(0, 0, width, height, 0xC00B120F);
        // Панель
        ctx.fill(px, py, px + pw, py + ph, PANEL);
        ctx.drawStrokedRectangle(px, py, pw, ph, BORDER);

        // Заголовок
        Text title = Text.translatable("pmchat.media.title");
        ctx.drawText(textRenderer, title, px + 12, py + 10, TEXT, false);
        // Кнопка «открыть папку музыки»
        Text openLbl = Text.translatable("pmchat.media.openfolder");
        int owL = textRenderer.getWidth(openLbl) + 16;
        int oX = px + pw - owL - 10, oY = py + 7;
        openFolderRect = new int[]{oX, oY, owL, 16};
        boolean hovOpen = in(mouseX, mouseY, openFolderRect);
        ctx.fill(oX, oY, oX + owL, oY + 16, hovOpen ? ROW_HOVER : ROW);
        ctx.drawStrokedRectangle(oX, oY, owL, 16, BORDER);
        ctx.drawText(textRenderer, openLbl, oX + 8, oY + 4, hovOpen ? TEXT : SUBTLE, false);

        int listTop = py + 30;
        int listBottom = py + ph - 8;

        // Список папок/файлов
        rowRects.clear();
        List<File> entries = scanEntries();
        int y = listTop - scroll;
        int rowH = 18;
        if (entries.isEmpty()) {
            ctx.drawText(textRenderer, Text.translatable("pmchat.media.empty"),
                    px + 12, listTop + 6, SUBTLE, false);
            ctx.drawText(textRenderer, Text.translatable("pmchat.media.hint"),
                    px + 12, listTop + 20, SUBTLE, false);
        }
        ctx.enableScissor(px + 1, listTop, px + pw - 1, listBottom);
        for (File f : entries) {
            if (y + rowH >= listTop && y <= listBottom) {
                boolean hov = mouseX >= px + 6 && mouseX < px + pw - 6 && mouseY >= y && mouseY < y + rowH;
                ctx.fill(px + 6, y, px + pw - 6, y + rowH - 2, hov ? ROW_HOVER : ROW);
                boolean dir = f.isDirectory();
                String[] icon = dir ? PmIcons.NOTE : PmIcons.PLAY;
                PmIcons.draw(ctx, icon, px + 9, y + 2, 12, 12, dir ? 0xFF8FD8A8 : 0xFF9CC4DC);
                String label = dir ? f.getName() + "  ♫" : PmMedia.get().hasActive() ? f.getName() : f.getName();
                int count = dir ? countAudio(f) : 0;
                String right = dir ? count + " " + Text.translatable("pmchat.media.tracks").getString() : "";
                ctx.drawText(textRenderer, trim(label, pw - 60), px + 26, y + 5, TEXT, false);
                if (!right.isEmpty()) {
                    ctx.drawText(textRenderer, right, px + pw - 12 - textRenderer.getWidth(right), y + 5, SUBTLE, false);
                }
                rowRects.add(new Object[]{px + 6, y, pw - 12, rowH, f});
            }
            y += rowH;
        }
        ctx.disableScissor();

        // Свёрнутое окошко-плеер с управлением (то же, что в углу)
        if (PmMedia.get().hasActive()) {
            PmMedia.get().renderMini(ctx, mouseX, mouseY, true);
        }
    }

    /** Папки и отдельные аудиофайлы в config/pmchat-music (папки первыми). */
    private List<File> scanEntries() {
        File dir = PmMedia.musicDir();
        File[] all = dir.listFiles();
        List<File> dirs = new ArrayList<>();
        List<File> files = new ArrayList<>();
        if (all != null) {
            for (File f : all) {
                if (f.isDirectory()) dirs.add(f);
                else if (PmMedia.isAudio(f)) files.add(f);
            }
        }
        dirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        List<File> out = new ArrayList<>(dirs);
        out.addAll(files);
        return out;
    }

    private int countAudio(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return 0;
        int n = 0;
        for (File f : fs) if (PmMedia.isAudio(f)) n++;
        return n;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        // Клик по окошку-плееру (управление)
        if (PmMedia.get().hasActive() && PmMedia.get().handleMiniClick(mx, my)) {
            return true;
        }
        if (in(mx, my, openFolderRect)) {
            Util.getOperatingSystem().open(PmMedia.musicDir());
            return true;
        }
        for (Object[] r : rowRects) {
            if (mx >= (int) r[0] && mx < (int) r[0] + (int) r[2]
                    && my >= (int) r[1] && my < (int) r[1] + (int) r[3]) {
                File f = (File) r[4];
                if (f.isDirectory()) {
                    PmMedia.get().startMusicFolder(f);
                } else {
                    // Отдельный трек — играем его вместе с соседними в той же папке
                    File parent = f.getParentFile();
                    File[] siblings = parent != null ? parent.listFiles() : new File[]{f};
                    List<File> list = new ArrayList<>();
                    if (siblings != null) {
                        Arrays.sort(siblings, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                        for (File s : siblings) if (PmMedia.isAudio(s)) list.add(s);
                    }
                    int idx = list.indexOf(f);
                    PmMedia.get().startMusicList(list, Math.max(0, idx),
                            parent != null ? parent.getName() : "");
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        scroll = Math.max(0, scroll - (int) Math.signum(v) * 24);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.getKeycode();
        if (k == GLFW.GLFW_KEY_SPACE) {
            PmMedia.get().togglePause();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean in(int mx, int my, int[] r) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private String trim(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
