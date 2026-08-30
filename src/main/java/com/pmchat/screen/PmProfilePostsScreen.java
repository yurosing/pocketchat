package com.pmchat.screen;

import com.pmchat.client.PmBackend;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Публикации на страничке профиля (стена, как заметки/посты) — простой текст
 * с лёгкой разметкой (**жирный**, _курсив_, __подчёркнутый__), которую
 * разбирает этот же клиент (см. {@link #parseMarkup}). Владелец может писать
 * новые публикации и удалять свои; чужие видны всем как read-only лента.
 */
@Environment(EnvType.CLIENT)
public class PmProfilePostsScreen extends Screen {

    // ** сначала (не то "__" ошибочно разберётся как "_" + "_..._" + "_")
    private static final Pattern MARKUP = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__|_(.+?)_");

    private final Screen parent;
    private final String player;
    private final boolean self;
    private final PmConfig config = PmChatClient.getConfig();

    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, ACCENT;
    private int px, py, pw, ph;
    private int scroll = 0;
    private int[] closeRect;

    private EditBox composeField;
    private String composeText = "";
    private List<PmBackend.ProfilePost> posts = null;
    private final List<Object[]> deleteRects = new ArrayList<>(); // x,y,w,h,postId
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    private final SimpleDateFormat fmt = new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT);

    public PmProfilePostsScreen(Screen parent, String player) {
        super(Component.translatable("pmchat.posts.title"));
        this.parent = parent;
        this.player = player;
        this.self = player.equalsIgnoreCase(PmChatClient.selfNamePublic());
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
        ACCENT = 0xFF6FBF8B;
    }

    @Override
    protected void init() {
        applyTheme();
        pw = Math.min(320, width - 24);
        ph = Math.min(280, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        if (posts == null) loadPosts();
        layout();
    }

    private void loadPosts() {
        PmBackend.profilePosts(player, (ok, list, err) -> {
            posts = ok && list != null ? list : new ArrayList<>();
            if (client != null) layout();
        });
    }

    private void layout() {
        if (composeField != null) composeText = composeField.getValue();
        clearWidgets();

        int fx = px + 12;
        int fw = pw - 24;
        int y = py + 24;

        if (self) {
            int fieldW = fw - 66;
            composeField = new EditBox(font, fx, y, fieldW, 15, Component.translatable("pmchat.posts.hint"));
            composeField.setMaxLength(2000);
            composeField.setValue(composeText);
            addRenderableWidget(composeField);

            int bw = 20;
            addRenderableWidget(FlatButton.centered(font, fx + fieldW + 2, y, bw, 15,
                    Component.literal("Ж"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> wrapField("**")));
            addRenderableWidget(FlatButton.centered(font, fx + fieldW + 2 + bw + 2, y, bw, 15,
                    Component.literal("К"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> wrapField("_")));
            addRenderableWidget(FlatButton.centered(font, fx + fieldW + 2 + 2 * (bw + 2), y, bw, 15,
                    Component.literal("П"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> wrapField("__")));
            y += 19;

            addRenderableWidget(FlatButton.centered(font, fx, y, fw, 15,
                    Component.translatable("pmchat.posts.publish"), 0xFF244A33, 0xFF2E5C40, 0xFF4C8A66, 0xFFCFEEDA,
                    btn -> publish()));
            y += 20;
        }

        addRenderableWidget(FlatButton.centered(font, px + pw / 2 - 40, py + ph - 20, 80, 15,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> close()));

        closeRect = new int[]{px + pw - 18, py + 5, 14, 14};
    }

    /** Ж/К/П — оборачивает (или, если уже обёрнут этим маркером, разворачивает) весь текст поля. */
    private void wrapField(String marker) {
        String s = composeField.getValue();
        if (s.startsWith(marker) && s.endsWith(marker) && s.length() >= marker.length() * 2) {
            composeField.setValue(s.substring(marker.length(), s.length() - marker.length()));
        } else {
            composeField.setValue(marker + s + marker);
        }
    }

    private void publish() {
        String content = composeField.getValue().trim();
        if (content.isEmpty()) return;
        PmBackend.createProfilePost(content, (ok, v, err) -> {
            if (ok) {
                composeField.setValue("");
                composeText = "";
                status = Component.translatable("pmchat.posts.published");
                statusColor = 0xFF8FD8A8;
                loadPosts();
            } else {
                status = Component.translatable("pmchat.posts.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
            }
        });
    }

    private void deletePost(long id) {
        PmBackend.deleteProfilePost(id, (ok, v, err) -> {
            if (ok) loadPosts();
            else {
                status = Component.translatable("pmchat.posts.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
            }
        });
    }

    /** **жирный** / _курсив_ / __подчёркнутый__ → размеченный Component. Порядок альтернатив в MARKUP важен. */
    static MutableComponent parseMarkup(String raw) {
        MutableComponent out = Component.literal("");
        Matcher m = MARKUP.matcher(raw);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.append(Component.literal(raw.substring(last, m.start())));
            String bold = m.group(1), italic = m.group(3), underline = m.group(2);
            if (bold != null) {
                out.append(Component.literal(bold).setStyle(Style.EMPTY.withBold(true)));
            } else if (underline != null) {
                out.append(Component.literal(underline).setStyle(Style.EMPTY.withUnderline(true)));
            } else if (italic != null) {
                out.append(Component.literal(italic).setStyle(Style.EMPTY.withItalic(true)));
            }
            last = m.end();
        }
        if (last < raw.length()) out.append(Component.literal(raw.substring(last)));
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xC0000000);
        ctx.fill(px, py, px + pw, py + ph, BG);
        ctx.outline(px, py, pw, ph, BORDER);

        Component title = Component.translatable("pmchat.posts.title_of", player);
        ctx.text(font, trim(title.getString(), pw - 40), px + 12, py + 8, TITLE, false);
        String x = "✕";
        boolean hovX = closeRect != null && mouseX >= closeRect[0] && mouseX < closeRect[0] + closeRect[2]
                && mouseY >= closeRect[1] && mouseY < closeRect[1] + closeRect[3];
        ctx.text(font, x, px + pw - 15, py + 8, hovX ? TITLE : LABEL, false);

        int fx = px + 12;
        int fw = pw - 24;
        int listTop = py + 24 + (self ? 39 : 0);
        int listBottom = py + ph - 26;

        deleteRects.clear();
        ctx.enableScissor(px + 1, listTop, px + pw - 1, listBottom);
        if (posts == null) {
            ctx.text(font, Component.translatable("pmchat.bots.loading"), fx, listTop + 4, LABEL, false);
        } else if (posts.isEmpty()) {
            ctx.text(font, Component.translatable("pmchat.posts.empty"), fx, listTop + 4, LABEL, false);
        } else {
            int y = listTop - scroll;
            for (PmBackend.ProfilePost p : posts) {
                int lines = wrapLines(p.content, fw - 8).size();
                int rowH = 12 + lines * 10 + 4;
                if (y + rowH >= listTop && y <= listBottom) {
                    ctx.fill(fx, y, fx + fw, y + rowH - 2, BTN_BG);
                    String head = p.author + (p.at > 0 ? "  ·  " + fmt.format(new Date(p.at)) : "");
                    ctx.text(font, trim(head, fw - 24), fx + 4, y + 2, ACCENT, false);
                    int ly = y + 12;
                    for (String line : wrapLines(p.content, fw - 8)) {
                        ctx.text(font, parseMarkup(line), fx + 4, ly, VALUE, false);
                        ly += 10;
                    }
                    if (self) {
                        int dx = fx + fw - 12, dy = y + 2;
                        boolean hov = mouseX >= dx - 2 && mouseX < dx + 8 && mouseY >= dy - 1 && mouseY < dy + 9;
                        ctx.text(font, "✖", dx, dy, hov ? 0xFFE07A6A : LABEL, false);
                        deleteRects.add(new Object[]{dx - 2, dy - 1, 10, 10, p.id});
                    }
                }
                y += rowH;
            }
        }
        ctx.disableScissor();

        if (!status.getString().isEmpty()) {
            ctx.text(font, status, fx, py + ph - 36, statusColor, false);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    /** Наивный перенос по словам под ширину — публикации короткие, точность до пикселя не нужна. */
    private List<String> wrapLines(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (font.width(candidate.replaceAll("\\*\\*|__|_", "")) > maxW && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(candidate);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        for (Object[] r : deleteRects) {
            if (mx >= (int) r[0] && mx < (int) r[0] + (int) r[2]
                    && my >= (int) r[1] && my < (int) r[1] + (int) r[3]) {
                deletePost((long) r[4]);
                return true;
            }
        }
        if (closeRect != null && mx >= closeRect[0] && mx < closeRect[0] + closeRect[2]
                && my >= closeRect[1] && my < closeRect[1] + closeRect[3]) {
            close();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        scroll = Math.max(0, scroll - (int) Math.signum(v) * 24);
        return true;
    }

    private String trim(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
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
