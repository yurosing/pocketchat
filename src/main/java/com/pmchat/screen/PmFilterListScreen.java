package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Список одной категории фильтров: добавление, редактирование, удаление.
 * Длинные записи переносятся на несколько строк, список прокручивается —
 * поэтому не уезжает за экран при большом количестве ников/сообщений.
 * Для текстовых фильтров можно менять область (глобал/Discord/везде),
 * для ников работает автодополнение по Tab (список игроков).
 */
@Environment(EnvType.CLIENT)
public class PmFilterListScreen extends Screen {

    private static final int PANEL_W = 320;

    private static final int BG = 0xFF1C3644;
    private static final int BORDER = 0xFF10222C;
    private static final int TITLE = 0xFFF2F6F8;
    private static final int SECTION = 0xFF8FD8A8;
    private static final int BTN_BG = 0xFF15303D;
    private static final int BTN_HOVER = 0xFF0F2833;
    private static final int BTN_BORDER = 0xFF2A4A5C;
    private static final int VALUE = 0xFFEDF3F0;
    private static final int ROW_BG = 0xFF16241E;
    private static final int ROW_BG2 = 0xFF12201B;

    private final Screen parent;
    private final int category;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py, panelH;
    private int scroll = 0;
    private int maxScroll = 0;
    private int editIndex = -1;
    private String input = "";
    private int scope = PmConfig.SCOPE_BOTH;

    private TextFieldWidget field;

    /** Кнопки строк для кликов: {x,y,w,h,type(0 edit/1 delete),index}. */
    private final List<int[]> rowButtons = new ArrayList<>();

    // Tab-автодополнение ников
    private final List<String> tabMatches = new ArrayList<>();
    private int tabIndex = -1;
    private String tabLastCompleted, tabBase = "";

    public PmFilterListScreen(Screen parent, int category) {
        super(Text.translatable(PmFiltersScreen.categoryKey(category)));
        this.parent = parent;
        this.category = category;
    }

    private boolean isText() {
        return category == PmFiltersScreen.CAT_TEXT;
    }

    @Override
    protected void init() {
        if (field != null) input = field.getText();
        clearChildren();

        panelH = Math.min(300, height - 30);
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        int addY = py + 26;
        int rightBtnW = 62;
        int scopeW = isText() ? 54 : 0;
        int scopeGap = isText() ? 6 : 0;
        int fieldW = PANEL_W - 20 - rightBtnW - 6 - scopeW - scopeGap;

        String hintKey = isText() ? "pmchat.filters.texthint" : "pmchat.filters.nick";
        field = new TextFieldWidget(textRenderer, px + 10, addY, fieldW, 16, Text.translatable(hintKey));
        field.setMaxLength(256);
        field.setText(input);
        String hint = Text.translatable(hintKey).getString();
        field.setSuggestion(input.isEmpty() ? hint : "");
        field.setChangedListener(s -> {
            field.setSuggestion(s.isEmpty() ? hint : "");
            tabLastCompleted = null;
        });
        addDrawableChild(field);

        int bx = px + PANEL_W - 10 - rightBtnW;
        if (isText()) {
            int sx = bx - scopeGap - scopeW;
            addDrawableChild(FlatButton.centered(textRenderer, sx, addY, scopeW, 16,
                    Text.translatable(scopeKey(scope)), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> {
                        input = field.getText();
                        scope = (scope + 1) % 3;
                        reinit();
                    }));
        }
        addDrawableChild(FlatButton.centered(textRenderer, bx, addY, rightBtnW, 16,
                Text.translatable(editIndex >= 0 ? "pmchat.filters.save" : "pmchat.filters.add"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> commit()));

        // Назад
        addDrawableChild(FlatButton.centered(textRenderer, px + 10, py + panelH - 24, 70, 18,
                Text.translatable("pmchat.filters.back"),
                BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));
    }

    private static String scopeKey(int scope) {
        return switch (scope) {
            case PmConfig.SCOPE_GLOBAL -> "pmchat.filters.scope.global";
            case PmConfig.SCOPE_DISCORD -> "pmchat.filters.scope.discord";
            default -> "pmchat.filters.scope.both";
        };
    }

    /** Добавить новую запись или сохранить редактируемую. */
    private void commit() {
        String t = field.getText().trim();
        if (!t.isEmpty()) {
            if (isText()) {
                if (editIndex >= 0 && editIndex < config.filterRules.size()) {
                    PmConfig.FilterRule r = config.filterRules.get(editIndex);
                    r.text = t;
                    r.scope = scope;
                } else {
                    config.filterRules.add(new PmConfig.FilterRule(t, scope));
                }
            } else {
                List<String> list = list();
                if (editIndex >= 0 && editIndex < list.size()) list.set(editIndex, t);
                else if (list.stream().noneMatch(n -> n.equalsIgnoreCase(t))) list.add(t);
            }
            config.save();
            PmChatClient.reloadPatterns();
        }
        input = "";
        editIndex = -1;
        scope = PmConfig.SCOPE_BOTH;
        reinit();
    }

    private List<String> list() {
        return category == PmFiltersScreen.CAT_CHAT ? config.filterPlayers : config.filterDiscordPlayers;
    }

    private int size() {
        return isText() ? config.filterRules.size() : list().size();
    }

    private String entryText(int i) {
        return isText() ? config.filterRules.get(i).text : list().get(i);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        ctx.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        ctx.drawStrokedRectangle(px, py, PANEL_W, panelH, BORDER);

        Text title = Text.translatable(PmFiltersScreen.categoryKey(category));
        ctx.drawText(textRenderer, title, px + (PANEL_W - textRenderer.getWidth(title)) / 2, py + 9, TITLE, false);

        int listTop = py + 48;
        int listBottom = py + panelH - 30;
        rowButtons.clear();

        if (size() == 0) {
            ctx.drawText(textRenderer, Text.translatable("pmchat.filters.empty"),
                    px + 12, listTop + 4, SECTION, false);
        }

        int textW = PANEL_W - 20 - 40; // место под ✎ и ✕
        ctx.enableScissor(px + 2, listTop, px + PANEL_W - 2, listBottom);
        int y = listTop - scroll;
        int total = 0;
        for (int i = 0; i < size(); i++) {
            String display = entryText(i);
            String tag = isText() ? "  (" + Text.translatable(scopeKey(config.filterRules.get(i).scope)).getString() + ")" : "";
            List<String> lines = wrap(display, textW);
            int rowH = Math.max(18, lines.size() * 10 + 6);
            if (y + rowH >= listTop && y <= listBottom) {
                boolean editing = i == editIndex;
                ctx.fill(px + 6, y, px + PANEL_W - 6, y + rowH - 2,
                        editing ? 0xFF23423A : (i % 2 == 0 ? ROW_BG : ROW_BG2));
                int ly = y + 4;
                for (int li = 0; li < lines.size(); li++) {
                    String line = lines.get(li) + (li == lines.size() - 1 ? tag : "");
                    ctx.drawText(textRenderer, line, px + 12, ly, VALUE, false);
                    ly += 10;
                }
                // Кнопки ✎ и ✕
                int by = y + 2;
                int delX = px + PANEL_W - 24, edX = delX - 20;
                drawMini(ctx, edX, by, "✎", 0xFF9CC4DC, mouseX, mouseY);
                drawMini(ctx, delX, by, "✕", 0xFFE07A6A, mouseX, mouseY);
                rowButtons.add(new int[]{edX, by, 16, 14, 0, i});
                rowButtons.add(new int[]{delX, by, 16, 14, 1, i});
            }
            y += rowH + 2;
            total += rowH + 2;
        }
        ctx.disableScissor();

        maxScroll = Math.max(0, total - (listBottom - listTop));
        if (scroll > maxScroll) scroll = maxScroll;

        // Подсказка про Tab для ников
        if (!isText()) {
            ctx.drawText(textRenderer, Text.translatable("pmchat.filters.tabhint"),
                    px + 12, py + panelH - 25, SECTION, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawMini(DrawContext ctx, int x, int y, String glyph, int color, int mx, int my) {
        boolean hov = mx >= x && mx < x + 16 && my >= y && my < y + 14;
        ctx.fill(x, y, x + 16, y + 14, hov ? 0xFF2A4A5C : 0xFF101A16);
        ctx.drawText(textRenderer, glyph, x + 8 - textRenderer.getWidth(glyph) / 2, y + 3, color, false);
    }

    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (textRenderer.getWidth(cur.toString() + c) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder();
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        for (int[] b : rowButtons) {
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                int idx = b[5];
                if (b[4] == 1) { // удалить
                    if (isText()) config.filterRules.remove(idx);
                    else list().remove(idx);
                    if (editIndex == idx) {
                        editIndex = -1;
                        input = "";
                    }
                    config.save();
                    PmChatClient.reloadPatterns();
                    reinit();
                } else { // редактировать
                    editIndex = idx;
                    input = entryText(idx);
                    if (isText()) scope = config.filterRules.get(idx).scope;
                    reinit();
                    if (field != null) field.setFocused(true);
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

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == GLFW.GLFW_KEY_TAB && !isText() && field != null && field.isFocused()) {
            completeNick();
            return true;
        }
        return super.keyPressed(input);
    }

    /** Автодополнение ника по списку игроков; повторный Tab циклит совпадения. */
    private void completeNick() {
        String text = field.getText();
        boolean cycling = tabLastCompleted != null && tabLastCompleted.equals(text) && !tabMatches.isEmpty();
        if (!cycling) {
            if (text.isEmpty()) return;
            String pl = text.toLowerCase(Locale.ROOT);
            tabMatches.clear();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() != null) {
                List<String> names = new ArrayList<>();
                for (net.minecraft.client.network.PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                    String n = e.getProfile().name();
                    if (n != null && n.toLowerCase(Locale.ROOT).startsWith(pl)) names.add(n);
                }
                names.sort(String.CASE_INSENSITIVE_ORDER);
                tabMatches.addAll(names);
            }
            tabIndex = -1;
            tabBase = "";
            if (tabMatches.isEmpty()) return;
        }
        tabIndex = (tabIndex + 1) % tabMatches.size();
        String completed = tabBase + tabMatches.get(tabIndex);
        field.setText(completed);
        field.setCursorToEnd(false);
        tabLastCompleted = completed;
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
