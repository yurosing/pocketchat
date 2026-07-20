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

/**
 * Экран «Фильтры чата» — верхний уровень. Два тумблера (глобал/Discord) и три
 * плитки-«папки» (как стикеры): игнор в чате, игнор в Discord, фильтры текста.
 * Клик по плитке открывает {@link PmFilterListScreen} с редактированием списка.
 */
@Environment(EnvType.CLIENT)
public class PmFiltersScreen extends Screen {

    public static final int CAT_CHAT = 0, CAT_DISCORD = 1, CAT_TEXT = 2;

    private static final int PANEL_W = 300;
    private static final int ROW_H = 17;

    private static final int SECTION = 0xFF8FD8A8;

    // Тема применяется в init() до построения строк
    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
            TILE_BG, TILE_HOVER, TILE_BORDER;

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
        TILE_BG = t.btnBg; TILE_HOVER = t.btnHover; TILE_BORDER = t.btnBorder;
    }

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py, panelH;
    private final List<Object[]> labels = new ArrayList<>();
    /** Плитки-папки: {x,y,w,h,category}. */
    private final List<int[]> tiles = new ArrayList<>();

    public PmFiltersScreen(Screen parent) {
        super(Text.translatable("pmchat.filters.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        applyTheme();
        clearChildren();
        labels.clear();
        tiles.clear();

        panelH = 26 + 2 * ROW_H + 12 + 70 + 34;
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        int y = py + 26;
        y = addToggle(y, "pmchat.filters.global", config.filterGlobal,
                () -> config.filterGlobal = !config.filterGlobal);
        y = addToggle(y, "pmchat.filters.discord", config.filterDiscord,
                () -> config.filterDiscord = !config.filterDiscord);

        // Плитки-папки категорий (в ряд, как стикеры)
        int gap = 8;
        int tw = (PANEL_W - 20 - 2 * gap) / 3;
        int th = 62;
        int ty = y + 6;
        int tx = px + 10;
        int[] cats = {CAT_CHAT, CAT_DISCORD, CAT_TEXT};
        for (int cat : cats) {
            tiles.add(new int[]{tx, ty, tw, th, cat});
            tx += tw + gap;
        }

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + panelH - 24, 80, 18,
                Text.translatable("pmchat.settings.done"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> close()));
    }

    static int categoryCount(PmConfig config, int cat) {
        return switch (cat) {
            case CAT_CHAT -> config.filterPlayers.size();
            case CAT_DISCORD -> config.filterDiscordPlayers.size();
            default -> config.filterRules.size();
        };
    }

    static String categoryKey(int cat) {
        return switch (cat) {
            case CAT_CHAT -> "pmchat.filters.cat.chat";
            case CAT_DISCORD -> "pmchat.filters.cat.discord";
            default -> "pmchat.filters.cat.text";
        };
    }

    private int addToggle(int y, String labelKey, boolean on, Runnable toggle) {
        labels.add(new Object[]{Text.translatable(labelKey).getString(), px + 10, y + 3, LABEL});
        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W - 92, y, 84, 14,
                Text.translatable(on ? "pmchat.set.on" : "pmchat.set.off"),
                BTN_BG, BTN_HOVER, BTN_BORDER, on ? 0xFF8FD8A8 : VALUE, btn -> {
                    toggle.run();
                    config.save();
                    PmChatClient.reloadPatterns();
                    reinit();
                }));
        return y + ROW_H;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, panelH, BORDER);

        Text title = Text.translatable("pmchat.filters.title");
        context.drawText(textRenderer, title,
                px + (PANEL_W - textRenderer.getWidth(title)) / 2, py + 9, TITLE, false);

        for (Object[] l : labels) {
            context.drawText(textRenderer, (String) l[0], (int) l[1], (int) l[2], (int) l[3], false);
        }

        // Плитки
        for (int[] t : tiles) {
            boolean hov = mouseX >= t[0] && mouseX < t[0] + t[2] && mouseY >= t[1] && mouseY < t[1] + t[3];
            context.fill(t[0], t[1], t[0] + t[2], t[1] + t[3], hov ? TILE_HOVER : TILE_BG);
            context.drawStrokedRectangle(t[0], t[1], t[2], t[3], TILE_BORDER);
            int cat = t[4];
            String count = String.valueOf(categoryCount(config, cat));
            // Крупное число по центру
            context.drawText(textRenderer, count,
                    t[0] + (t[2] - textRenderer.getWidth(count)) / 2, t[1] + 14, VALUE, false);
            // Подпись снизу (в 1–2 строки)
            String label = Text.translatable(categoryKey(cat)).getString();
            List<String> lines = wrap(label, t[2] - 8);
            int ly = t[1] + t[3] - lines.size() * 10 - 4;
            for (String line : lines) {
                context.drawText(textRenderer, line,
                        t[0] + (t[2] - textRenderer.getWidth(line)) / 2, ly, SECTION, false);
                ly += 10;
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (textRenderer.getWidth(test) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        for (int[] t : tiles) {
            if (mx >= t[0] && mx < t[0] + t[2] && my >= t[1] && my < t[1] + t[3]) {
                MinecraftClient.getInstance().setScreen(new PmFilterListScreen(this, t[4]));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void reinit() {
        init();
    }

    @Override
    public void close() {
        config.save();
        PmChatClient.reloadPatterns();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
