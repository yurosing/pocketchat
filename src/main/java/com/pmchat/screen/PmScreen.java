package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmHistory;
import com.pmchat.client.PmImages;
import com.pmchat.client.PmMessage;
import com.pmchat.client.PmSecretSession;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Мессенджер: слева список диалогов с поиском, справа переписка
 * пузырями с анимациями, статистика и перевод денег.
 */
@Environment(EnvType.CLIENT)
public class PmScreen extends Screen {

    // Палитра — заполняется applyTheme() (тёмная/светлая + цвет сообщений)
    private int PANEL_BG, PANEL_BORDER, LEFT_BG, DIVIDER, TITLE, SUBTLE,
            NAME_TEXT, PREVIEW_TEXT, ROW_HOVER, ROW_SELECTED, BADGE_BG,
            OUT_BG, OUT_TEXT, IN_BG, IN_TEXT, MONEY_BG, MONEY_TEXT,
            WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, WBTN_TEXT,
            ACCENT_BG, ACCENT_HOVER, ACCENT_BORDER, ACCENT_TEXT,
            SEP_LINE, EMOJI_BG, CHIP_BG, ROW_ALT;

    private void applyTheme() {
        int accent = com.pmchat.client.PmPalettes.OUT[
                Math.floorMod(config.outColor, com.pmchat.client.PmPalettes.OUT.length)];
        OUT_BG = accent;
        OUT_TEXT = 0xFFF2F8F4;
        ACCENT_BG = accent;
        ACCENT_HOVER = brightenBy(accent, 14);
        ACCENT_BORDER = brightenBy(accent, 44);
        ACCENT_TEXT = 0xFFEFF6F1;
        MONEY_BG = 0xFFB9862E;
        MONEY_TEXT = 0xFFFFF6E0;
        BADGE_BG = 0xFF4C8A66;

        if (config.theme == 1) {
            // Светлая
            PANEL_BG = 0xFFF3F6F8;
            PANEL_BORDER = 0xFFB9C6CE;
            LEFT_BG = 0xFFE7EDF1;
            DIVIDER = 0xFFC8D4DC;
            TITLE = 0xFF1B2B35;
            SUBTLE = 0xFF93A6B2;
            NAME_TEXT = 0xFF23333D;
            PREVIEW_TEXT = 0xFF7E929E;
            ROW_HOVER = 0xFFDAE4EA;
            ROW_SELECTED = 0xFFC2D6E4;
            IN_BG = 0xFFFFFFFF;
            IN_TEXT = 0xFF25313A;
            WBTN_BG = 0xFFE0E8ED;
            WBTN_BG_HOVER = 0xFFD2DEE5;
            WBTN_BORDER = 0xFFB9C8D1;
            WBTN_TEXT = 0xFF3A5A70;
            SEP_LINE = 0x33587488;
            EMOJI_BG = 0xF2FFFFFF;
            CHIP_BG = 0xFFE7EDF1;
            ROW_ALT = 0xFFE0E8ED;
        } else {
            // Тёмная
            PANEL_BG = 0xFF1C3644;
            PANEL_BORDER = 0xFF10222C;
            LEFT_BG = 0xFF15303D;
            DIVIDER = 0xFF2A4A5C;
            TITLE = 0xFFF2F6F8;
            SUBTLE = 0xFF54748A;
            NAME_TEXT = 0xFFEDF3F0;
            PREVIEW_TEXT = 0xFF7E9AAB;
            ROW_HOVER = 0xFF1E3E4E;
            ROW_SELECTED = 0xFF25506A;
            IN_BG = 0xFFF2F2F2;
            IN_TEXT = 0xFF222222;
            WBTN_BG = 0xFF15303D;
            WBTN_BG_HOVER = 0xFF0F2833;
            WBTN_BORDER = 0xFF2A4A5C;
            WBTN_TEXT = 0xFF9CC4DC;
            SEP_LINE = 0x33587488;
            EMOJI_BG = 0xF21C3644; // непрозрачная тёмная панель (было 0 — прозрачная)
            CHIP_BG = 0xFF15303D;
            ROW_ALT = 0xFF1A3A4A;
        }

        // Настраиваемые цвета поверх темы
        IN_BG = com.pmchat.client.PmPalettes.IN[
                Math.floorMod(config.inColor, com.pmchat.client.PmPalettes.IN.length)];
        IN_TEXT = com.pmchat.client.PmPalettes.textOn(IN_BG);
        BADGE_BG = com.pmchat.client.PmPalettes.BADGE[
                Math.floorMod(config.badgeColor, com.pmchat.client.PmPalettes.BADGE.length)];
    }

    private float textScale() {
        return Math.max(0.6f, Math.min(1.5f, config.textScalePct / 100f));
    }

    private int lineH() {
        return Math.round(10 * textScale());
    }

    private boolean isFeedTab() {
        return selected != null && (PmChatClient.GLOBAL.equals(selected)
                || PmChatClient.COREPROTECT.equals(selected)
                || selected.startsWith(PmChatClient.CHANNEL_PREFIX)
                || PmChatClient.isGroup(selected));
    }

    private boolean isGroupTab() {
        return PmChatClient.isGroup(selected);
    }

    private String groupId() {
        return PmChatClient.groupId(selected);
    }

    /** Ленты только для чтения (без ввода/реакций/закрепов). */
    private boolean isReadOnlyFeed() {
        return PmChatClient.COREPROTECT.equals(selected);
    }

    private String channelId() {
        return selected != null && selected.startsWith(PmChatClient.CHANNEL_PREFIX)
                ? selected.substring(PmChatClient.CHANNEL_PREFIX.length()) : null;
    }

    private static int brightenBy(int argb, int amount) {
        int a = argb & 0xFF000000;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + amount);
        int b = Math.min(255, (argb & 0xFF) + amount);
        return a | (r << 16) | (g << 8) | b;
    }

    /** NEW (6.10): смешивает цвет с целевым в заданной пропорции (0 — как есть, 1 — целевой). */
    private static int tintTowards(int argb, int target, float mix) {
        int a = argb & 0xFF000000;
        int r = (int) (((argb >> 16) & 0xFF) * (1 - mix) + ((target >> 16) & 0xFF) * mix);
        int g = (int) (((argb >> 8) & 0xFF) * (1 - mix) + ((target >> 8) & 0xFF) * mix);
        int b = (int) ((argb & 0xFF) * (1 - mix) + (target & 0xFF) * mix);
        return a | (r << 16) | (g << 8) | b;
    }

    // Геометрия: три размера окна (⤢), выбор хранится в конфиге
    private static final int[][] SIZES = {
            {344, 216, 116},
            {410, 260, 136},
            {480, 306, 156},
    };
    private int PANEL_W = 344;
    private int PANEL_H = 216;
    private int LEFT_W = 116;
    private int BUBBLE_MAX_TEXT_W = 128;
    private static final int ROW_H = 26;

    private final PmHistory history = PmChatClient.getHistory();
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py;
    private String selected = null;
    private boolean statsMode = false;
    private boolean moneyMode = false;
    /** NEW: меню «⋮» — редкие действия (видео/аудио, опрос, деньги, статистика). */
    private boolean moreMenuOpen = false;

    // Групповой чат (6.9): режим создания + поля
    private boolean groupCreateMode = false;
    private int[] groupNewRect = null;
    private TextFieldWidget groupNameField;
    private TextFieldWidget groupMembersField;

    private int listScroll = 0;
    private int msgScroll = 0;      // 0 — низ переписки
    private int msgMaxScroll = 0;

    private TextFieldWidget searchField;
    private TextFieldWidget inputField;
    private TextFieldWidget amountField;
    private String searchText = "";
    private String inputText = "";
    private String amountText = "";

    private long planeAt = -1;      // время последней отправки для анимации ➤

    // Tab-автодополнение ника (6.6): кандидаты и текущий индекс
    private final List<String> tabMatches = new ArrayList<>();
    private int tabIndex = -1;
    private String tabLastCompleted = null; // текст поля после последнего Tab

    // Отправка фото и голосовых
    private boolean imageMode = false;
    private boolean uploading = false;
    private boolean uploadFailed = false;
    private List<Path> screenshots = List.of();
    private List<Path> stickers = List.of();
    /** NEW (4.9): следующее отправленное фото/видео уйдёт со спойлером (размытие до клика). */
    private boolean spoilerMode = false;
    private int[] spoilerToggleRect;

    // Медиа-меню (5.9 видео / 6.0 аудиофайлы)
    private boolean mediaMode = false;
    private List<Path> videoFiles = List.of();
    private List<Path> audioFiles = List.of();
    private final List<Object[]> mediaRects = new ArrayList<>(); // x,y,w,h,path,isVideo

    // Редактирование своего сообщения (5.7): цель правки + плашка-отмена
    private PmMessage editTarget = null;
    private int editCancelX = -1, editCancelY = -1;

    // Ответ-цитата и эмодзи
    private PmMessage replyTarget = null;
    // Цитата фрагмента: выбранный кусок + его смещение в оригинале
    private String replyFragText = null;
    private int replyFragStart = -1, replyFragLen = 0;

    // Оверлей выбора фрагмента по словам (клик — начало, клик — конец)
    private PmMessage fragMsg = null;
    private int fragWordFrom = -1, fragWordTo = -1;
    private final List<String> fragWords = new ArrayList<>();
    private final List<int[]> fragSpans = new ArrayList<>();       // charStart,charEnd в оригинале
    private final List<Object[]> fragWordRects = new ArrayList<>(); // x,y,w,h,wordIndex
    private int[] fragOkRect = null, fragCancelRect = null;
    private boolean emojiMode = false;
    private int emojiCat = 0;
    // Категории эмодзи (только BMP-символы, которые рендерит шрифт Minecraft)
    private static final String[] EMOJI_CAT_ICONS = {"☺", "♥", "→", "☀", "⚙"};
    private static final String[][] EMOJI_GROUPS = {
            // Лица и жесты
            {"☺", "☻", "☹", "✌", "✊", "✋", "✔", "✖", "♡", "♥", "❤", "❣", "☮", "☯", "웃", "유"},
            // Сердца и звёзды
            {"♥", "❤", "♡", "❣", "★", "☆", "✦", "✧", "✩", "✪", "✫", "✬", "✭", "✮", "✯", "✰"},
            // Стрелки
            {"←", "↑", "→", "↓", "↔", "↕", "↖", "↗", "↘", "↙", "⇐", "⇒", "⇑", "⇓", "➤", "➜"},
            // Погода и природа
            {"☀", "☁", "☂", "☃", "☄", "☾", "☽", "❄", "❅", "❆", "⚡", "✿", "❀", "⛄", "☘", "♨"},
            // Разное
            {"⚔", "☠", "⛏", "⚒", "⚑", "⚐", "✉", "☕", "♪", "♫", "♬", "⌛", "⚓", "⚙", "☎", "✈"}
    };

    private final List<Object[]> rowRects = new ArrayList<>(); // x,y,w,h,name
    private final List<Object[]> shotRects = new ArrayList<>(); // x,y,w,h,path,isSticker
    private final List<Object[]> bubbleRects = new ArrayList<>(); // x,y,w,h,msg
    private final List<Object[]> spoilerRects = new ArrayList<>(); // NEW (4.9): x,y,w,h,msg — клик открывает спойлер
    private final List<Object[]> warnBtnRects = new ArrayList<>(); // x,y,w,h,nick (6.8 кнопка преда)
    private final List<Object[]> pollOptRects = new ArrayList<>(); // x,y,w,h,msg,optIndex
    private final List<Object[]> emojiRects = new ArrayList<>(); // x,y,w,h,emoji
    private final List<Object[]> emojiCatRects = new ArrayList<>(); // x,y,w,h,catIndex
    private int replyCancelX = -1, replyCancelY = -1;

    // Контекстное меню (ПКМ по сообщению, как в Telegram)
    private PmMessage ctxMsg = null;
    private int ctxX, ctxY;
    private final List<Object[]> ctxRects = new ArrayList<>(); // x,y,w,h,action

    // Просмотр фото на весь экран
    private PmImages.Entry fullscreenImg = null;

    // NEW (4.3): встроенный видеоплеер (VLC). Сама сессия и её жизненный цикл
    // теперь живут в персистентном com.pmchat.client.PmMedia (играет и когда чат
    // закрыт). Здесь остаётся лишь состояние ПОДГОТОВКИ (скачивание через yt-dlp).
    private String videoUrl;            // ссылка готовящегося/текущего видео
    private long videoOpenedAt;
    private boolean videoResolving = false; // NEW (5.0): фоновая подготовка YouTube (yt-dlp)
    private boolean videoOpenFailed = false; // резолв/запуск не удался — сразу показываем fallback
    private volatile String videoStatusText = null; // NEW (5.1): «yt-dlp» / «42%» при подготовке
    private boolean videoNeedsCookies = false;      // NEW (5.1): провал из-за требования входа YouTube
    private int videoSeq = 0;           // защита от «просроченных» фоновых резолвов
    private int[] videoFallbackRect; // «Открыть в браузере», если VLC не смог показать
    private boolean videoDragSeek = false;
    private boolean videoDragVolume = false;
    private static final float[] VIDEO_RATES = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private int[] videoBarRect;   // x,y,w,h полоски перемотки (для клика/драга)
    private int[] videoVolRect;   // x,y,w,h полоски громкости
    private int[] videoPlayRect;  // x,y,w,h кнопки play/pause
    private int[] videoRateRect;  // x,y,w,h кнопки скорости
    private int[] videoCloseRect; // x,y,w,h кнопки закрытия
    private int[] videoBrowserRect; // NEW (5.0): кнопка «в браузере» в панели
    private int[] videoImgRect;   // x,y,w,h самого видеокадра (клик — пауза/плей)
    private int[] videoMinRect;   // NEW (5.2): кнопка «свернуть» в полноэкранном плеере

    // Пересылка: выбранное сообщение ждёт, пока кликнут диалог-получатель
    private PmMessage forwardBuffer = null;
    private String forwardFromNick = "";
    private int[] pinBarRect = null; // x,y,w,h полоски закрепа (клик — переход к текущему)
    private int[] pinListBtnRect = null; // кнопка «список закреплённых»
    private int[] pinUnpinRect = null;   // × открепить текущий
    private int pinCursor = 0;           // какой закреп показан в полоске (5.6 циклично)
    private boolean pinListOpen = false; // оверлей списка закреплённых

    // Глобальный поиск по всем чатам (6.7)
    private boolean searchOpen = false;
    private int searchScroll = 0;
    private final List<Object[]> searchResultRects = new ArrayList<>(); // x,y,w,h,conv,PmMessage
    private final List<Object[]> pinListRects = new ArrayList<>();     // x,y,w,h,hash (переход)
    private final List<Object[]> pinListUnpinRects = new ArrayList<>();// x,y,w,h,hash (открепить)
    private String flashHash = null; // хэш сообщения для вспышки при переходе
    private long flashUntil = 0;
    private int pinnedOffsetFromBottom = -1; // позиция закреплённого от низа (для перехода)
    private final java.util.Map<String, Integer> pinOffsets = new java.util.HashMap<>(); // hash -> offset снизу
    private String pendingJumpHash = null; // отложенный переход к сообщению (после смены диалога)
    private int pendingJumpOffset = -1;

    /** NEW: выпадающее меню «⋮» — видео/аудио, опрос, деньги, статистика. */
    private void buildMoreMenu() {
        int w = 130;
        int x = px + PANEL_W - 10 - w;
        int y = py + 22;
        int rowH = 18;

        addDrawableChild(FlatButton.centered(textRenderer, x, y, w, rowH,
                Text.literal("▶ " + Text.translatable("pmchat.media.pick").getString()),
                WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, 0xFF9CC4DC, btn -> {
                    closeModes();
                    mediaMode = true;
                    loadMedia();
                    rebuild();
                }));
        y += rowH + 2;
        addDrawableChild(FlatButton.centered(textRenderer, x, y, w, rowH,
                Text.literal("▤ " + Text.translatable("pmchat.tip.poll").getString()),
                WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, 0xFF9CC4DC, btn -> {
                    closeModes();
                    pollMode = true;
                    rebuild();
                }));
        y += rowH + 2;
        addDrawableChild(FlatButton.centered(textRenderer, x, y, w, rowH,
                Text.literal("$ " + Text.translatable("pmchat.tip.money").getString()),
                WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, 0xFFF0C34E, btn -> {
                    closeModes();
                    moneyMode = true;
                    rebuild();
                }));
        y += rowH + 2;
        addDrawableChild(FlatButton.centered(textRenderer, x, y, w, rowH,
                Text.literal("▥ " + Text.translatable("pmchat.tip.stats").getString()),
                WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, WBTN_TEXT, btn -> {
                    closeModes();
                    statsMode = true;
                    clearConfirm = false;
                    rebuild();
                }));
    }

    // Композер опроса (только личный чат)
    private boolean pollMode = false;
    private boolean pollMulti = false;
    private TextFieldWidget pollQ;
    private final TextFieldWidget[] pollOpts = new TextFieldWidget[4];

    private void buildPollComposer() {
        int cx = px + LEFT_W + 8;
        int cw = PANEL_W - LEFT_W - 16;
        int y = py + 40;
        pollQ = new TextFieldWidget(textRenderer, cx, y, cw, 16, Text.translatable("pmchat.poll.q"));
        pollQ.setMaxLength(80);
        pollQ.setSuggestion(pollQ.getText().isEmpty() ? Text.translatable("pmchat.poll.q").getString() : "");
        pollQ.setChangedListener(s -> pollQ.setSuggestion(s.isEmpty() ? Text.translatable("pmchat.poll.q").getString() : ""));
        addDrawableChild(pollQ);
        y += 20;
        for (int i = 0; i < pollOpts.length; i++) {
            int fi = i;
            pollOpts[i] = new TextFieldWidget(textRenderer, cx, y, cw, 14,
                    Text.literal(Text.translatable("pmchat.poll.opt").getString() + " " + (i + 1)));
            pollOpts[i].setMaxLength(48);
            String hint = Text.translatable("pmchat.poll.opt").getString() + " " + (i + 1);
            pollOpts[i].setSuggestion(hint);
            pollOpts[i].setChangedListener(s -> pollOpts[fi].setSuggestion(s.isEmpty() ? hint : ""));
            addDrawableChild(pollOpts[i]);
            y += 17;
        }
        addDrawableChild(FlatButton.centered(textRenderer, cx, y, 110, 14,
                Text.translatable(pollMulti ? "pmchat.poll.multi.on" : "pmchat.poll.multi.off"),
                WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, WBTN_TEXT, btn -> {
                    pollMulti = !pollMulti;
                    rebuild();
                }));
        y += 18;
        addDrawableChild(FlatButton.centered(textRenderer, cx, y, 90, 16,
                Text.translatable("pmchat.poll.create"), ACCENT_BG, ACCENT_HOVER, ACCENT_BORDER, ACCENT_TEXT,
                btn -> createPoll()));
        addDrawableChild(FlatButton.centered(textRenderer, cx + 96, y, 60, 16,
                Text.translatable("pmchat.poll.cancel"), WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, WBTN_TEXT,
                btn -> { pollMode = false; rebuild(); }));
    }

    private void createPoll() {
        if (pollQ == null || selected == null) return;
        String q = pollQ.getText().trim();
        List<String> opts = new ArrayList<>();
        for (TextFieldWidget f : pollOpts) {
            if (f != null && !f.getText().trim().isEmpty()) opts.add(f.getText().trim());
        }
        if (q.isEmpty() || opts.size() < 2) return;
        PmChatClient.sendPoll(selected, pollMulti, q, opts);
        pollMode = false;
        msgScroll = 0;
        rebuild();
    }

    // ---------- Создание группы (6.9) ----------

    private void buildGroupComposer() {
        int cx = px + LEFT_W + 10;
        int cw = PANEL_W - LEFT_W - 20;
        int y = py + 40;
        String nameHint = Text.translatable("pmchat.group.name").getString();
        groupNameField = new TextFieldWidget(textRenderer, cx, y, cw, 16, Text.literal(nameHint));
        groupNameField.setMaxLength(24);
        groupNameField.setSuggestion(nameHint);
        groupNameField.setChangedListener(s -> groupNameField.setSuggestion(s.isEmpty() ? nameHint : ""));
        addDrawableChild(groupNameField);
        y += 42;
        String memHint = Text.translatable("pmchat.group.members.hint").getString();
        groupMembersField = new TextFieldWidget(textRenderer, cx, y, cw, 16, Text.literal(memHint));
        groupMembersField.setMaxLength(160);
        groupMembersField.setSuggestion(memHint);
        groupMembersField.setChangedListener(s -> groupMembersField.setSuggestion(s.isEmpty() ? memHint : ""));
        addDrawableChild(groupMembersField);
        y += 30;
        addDrawableChild(FlatButton.centered(textRenderer, cx, y, 100, 16,
                Text.translatable("pmchat.group.create"), ACCENT_BG, ACCENT_HOVER, ACCENT_BORDER, ACCENT_TEXT,
                btn -> createGroupFromComposer()));
        addDrawableChild(FlatButton.centered(textRenderer, cx + 106, y, 60, 16,
                Text.translatable("pmchat.poll.cancel"), WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, WBTN_TEXT,
                btn -> { groupCreateMode = false; rebuild(); }));
    }

    private void createGroupFromComposer() {
        if (groupNameField == null || groupMembersField == null) return;
        String name = groupNameField.getText().trim();
        String raw = groupMembersField.getText().trim();
        List<String> members = new ArrayList<>();
        for (String part : raw.split("[,\\s]+")) {
            if (!part.isBlank()) members.add(part.trim());
        }
        if (members.isEmpty()) return;
        String id = PmChatClient.createGroup(name, members);
        groupCreateMode = false;
        if (id != null) {
            selected = PmChatClient.GROUP_PREFIX + id;
            msgScroll = 0;
        }
        rebuild();
    }

    private void renderGroupCreate(DrawContext context) {
        int cx = px + LEFT_W + 10;
        context.drawText(textRenderer, Text.translatable("pmchat.group.title"), cx, py + 10, TITLE, false);
        context.fill(px + LEFT_W + 1, py + 22, px + PANEL_W - 2, py + 23, DIVIDER);
        context.drawText(textRenderer, Text.translatable("pmchat.group.name"), cx, py + 30, SUBTLE, false);
        context.drawText(textRenderer, Text.translatable("pmchat.group.members"), cx, py + 72, SUBTLE, false);
        context.drawText(textRenderer, Text.translatable("pmchat.group.hint"),
                cx, py + PANEL_H - 40, SUBTLE, false);
    }

    /** Ник исходного автора сообщения (для пересылки). */
    private String senderOfMessage(PmMessage msg) {
        if (msg.forwardFrom != null) return msg.forwardFrom; // уже переслано — сохраняем первоисточник
        if (isFeedTab() && msg.sender != null) {
            return msg.sender.contains(" [") ? msg.sender.substring(0, msg.sender.indexOf(" [")) : msg.sender;
        }
        if (msg.out) return PmChatClient.selfNamePublic();
        return selected != null ? selected : "?";
    }

    public PmScreen() {
        super(Text.translatable("screen.pmchat.title"));
    }

    public boolean isViewing(String player) {
        return player != null && player.equalsIgnoreCase(selected);
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        if (searchField != null) searchText = searchField.getText();
        if (inputField != null) inputText = inputField.getText();
        if (amountField != null) amountText = amountField.getText();

        clearChildren();
        searchField = null;
        inputField = null;
        amountField = null;

        applyTheme();
        if (config.fullscreen) {
            int m = 12;
            PANEL_W = Math.max(SIZES[0][0], width - m * 2);
            PANEL_H = Math.max(SIZES[0][1], height - m * 2);
            LEFT_W = Math.max(SIZES[0][2], Math.min(240, PANEL_W / 4));
        } else {
            int scale = Math.max(0, Math.min(SIZES.length - 1, config.uiScale));
            PANEL_W = SIZES[scale][0];
            PANEL_H = SIZES[scale][1];
            LEFT_W = SIZES[scale][2];
        }
        BUBBLE_MAX_TEXT_W = PANEL_W - LEFT_W - 96;

        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        // Полноэкранный режим (6.2)
        addDrawableChild(FlatButton.centered(textRenderer, px + LEFT_W - 40, py + 5, 16, 13,
                Text.literal(config.fullscreen ? "❐" : "⛶"), WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER,
                config.fullscreen ? 0xFF6FBF8B : WBTN_TEXT, btn -> {
                    config.fullscreen = !config.fullscreen;
                    config.save();
                    rebuild();
                }));
        // Смена размера окна (в фуллскрине не действует)
        if (!config.fullscreen) {
            addDrawableChild(icon(px + LEFT_W - 22, py + 5, 16, 13, PmIcons.SIZE, WBTN_TEXT, "pmchat.tip.size", btn -> {
                config.uiScale = (config.uiScale + 1) % SIZES.length;
                config.save();
                rebuild();
            }));
        }

        // Настройки и «не беспокоить» (внизу слева)
        addDrawableChild(icon(px + 6, py + PANEL_H - 19, 16, 13, PmIcons.SETTINGS, WBTN_TEXT, "pmchat.tip.settings", btn ->
                MinecraftClient.getInstance().setScreen(new PmSettingsScreen(this))));
        addDrawableChild(icon(px + 26, py + PANEL_H - 19, 16, 13, PmIcons.BELL,
                config.dnd ? 0xFFE07A6A : 0xFF8FD8A8, "pmchat.tip.dnd", btn -> {
                    config.dnd = !config.dnd;
                    config.save();
                    rebuild();
                }));

        // Поиск (слева сверху)
        searchField = new TextFieldWidget(textRenderer, px + 6, py + 22, LEFT_W - 12, 14,
                Text.translatable("pmchat.search"));
        searchField.setMaxLength(48);
        searchField.setText(searchText);
        String hint = Text.translatable("pmchat.search").getString();
        searchField.setSuggestion(searchText.isEmpty() ? hint : "");
        searchField.setChangedListener(s -> {
            listScroll = 0;
            searchField.setSuggestion(s.isEmpty() ? hint : "");
        });
        addDrawableChild(searchField);

        boolean isGlobal = isFeedTab();
        if (selected != null && !statsMode && !isGlobal && !groupCreateMode) {
            // Меньше кнопок в ряд (по многочисленным просьбам): в строке — только
            // самое частое (стикеры, голос, фото, звонок), редкое спрятано за «⋮».
            addDrawableChild(icon(px + PANEL_W - 130, py + 6, 18, 14, PmIcons.STICKERS, 0xFFE8A0C8, "pmchat.tip.stickers", btn -> {
                boolean was = stickerMode;
                closeModes();
                stickerMode = !was;
                if (stickerMode) {
                    loadStickerTabs();
                    stickerScroll = 0;
                }
                rebuild();
            }));
            addDrawableChild(icon(px + PANEL_W - 108, py + 6, 18, 14, PmIcons.VOICE,
                    com.pmchat.client.PmVoice.isRecording() ? 0xFFE07A6A : 0xFFCB8A8A,
                    "pmchat.tip.voice", btn -> toggleVoice()));
            addDrawableChild(icon(px + PANEL_W - 86, py + 6, 18, 14, PmIcons.PHOTO, 0xFF6FBF8B, "pmchat.tip.photo", btn -> {
                boolean was = imageMode;
                closeModes();
                imageMode = !was;
                if (imageMode) {
                    loadScreenshots();
                    loadStickers();
                }
                rebuild();
            }));
            // NEW: звонок через Simple Voice Chat — прямо в строке, как и просили
            if (config.isModUser(selected) && !PmChatClient.isLocalChat(selected)) {
                addDrawableChild(icon(px + PANEL_W - 64, py + 6, 18, 14, PmIcons.CALL, 0xFF8FD8A8, "pmchat.tip.call", btn -> {
                    PmChatClient.startCall(selected);
                }));
            }
            // «⋮» — остальное реже нужное: видео/аудио, опрос, деньги, статистика
            addDrawableChild(icon(px + PANEL_W - 26, py + 6, 18, 14, PmIcons.MORE, WBTN_TEXT, "pmchat.tip.more", btn -> {
                moreMenuOpen = !moreMenuOpen;
                rebuild();
            }));

            if (moreMenuOpen) {
                buildMoreMenu();
            }

            int inputY = py + PANEL_H - 24;
            if (moneyMode) {
                amountField = new TextFieldWidget(textRenderer, px + LEFT_W + 8, inputY, PANEL_W - LEFT_W - 78, 16,
                        Text.translatable("pmchat.money.hint"));
                amountField.setMaxLength(12);
                amountField.setText(amountText);
                String moneyHint = Text.translatable("pmchat.money.hint").getString();
                amountField.setSuggestion(amountText.isEmpty() ? moneyHint : "");
                amountField.setChangedListener(s -> amountField.setSuggestion(s.isEmpty() ? moneyHint : ""));
                addDrawableChild(amountField);
                addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W - 66, inputY, 58, 16,
                        Text.literal("$ ➤"), 0xFF8A6A20, 0xFF9A7826, 0xFFB9862E, MONEY_TEXT, btn -> doPay()));
            } else {
                addDrawableChild(icon(px + LEFT_W + 8, inputY, 16, 16, PmIcons.EMOJI, 0xFFF0C34E, "pmchat.tip.emoji",
                        btn -> { boolean was = emojiMode; closeModes(); emojiMode = !was; rebuild(); }));
                addSttButton(inputY);
                boolean secretActive = PmChatClient.isSecretActive(selected);
                inputField = new TextFieldWidget(textRenderer, px + LEFT_W + 54, inputY, PANEL_W - LEFT_W - 90, 16,
                        Text.translatable("pmchat.input.hint"));
                // NEW (6.10): в секретном чате сообщения короче — шифротекст должен влезть в /m
                inputField.setMaxLength(secretActive ? com.pmchat.client.PmWire.SECRET_MAX_CHARS : 200);
                inputField.setText(inputText);
                String inputHint = Text.translatable(
                        secretActive ? "pmchat.secret.input.hint" : "pmchat.input.hint").getString();
                inputField.setSuggestion(inputText.isEmpty() ? inputHint : "");
                String typingTarget = selected;
                inputField.setChangedListener(s -> {
                    inputField.setSuggestion(s.isEmpty() ? inputHint : "");
                    if (!s.isEmpty()) {
                        PmChatClient.sendTyping(typingTarget);
                    }
                });
                addDrawableChild(inputField);
                addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W - 32, inputY, 24, 16,
                        Text.literal("➤"), ACCENT_BG, ACCENT_HOVER, ACCENT_BORDER, ACCENT_TEXT, btn -> doSend()));
            }

            if (pollMode) {
                buildPollComposer();
            }
        }

        if (isGlobal && !isReadOnlyFeed() && !groupCreateMode) {
            // Общий чат: только эмодзи + поле + отправка
            int inputY = py + PANEL_H - 24;
            addDrawableChild(icon(px + LEFT_W + 8, inputY, 16, 16, PmIcons.EMOJI, 0xFFF0C34E, "pmchat.tip.emoji",
                    btn -> { boolean was = emojiMode; closeModes(); emojiMode = !was; rebuild(); }));
            addSttButton(inputY);
            inputField = new TextFieldWidget(textRenderer, px + LEFT_W + 54, inputY, PANEL_W - LEFT_W - 90, 16,
                    Text.translatable("pmchat.input.hint"));
            inputField.setMaxLength(200);
            inputField.setText(inputText);
            String inputHint = Text.translatable("pmchat.input.hint").getString();
            inputField.setSuggestion(inputText.isEmpty() ? inputHint : "");
            inputField.setChangedListener(s -> inputField.setSuggestion(s.isEmpty() ? inputHint : ""));
            addDrawableChild(inputField);
            addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W - 32, inputY, 24, 16,
                    Text.literal("➤"), ACCENT_BG, ACCENT_HOVER, ACCENT_BORDER, ACCENT_TEXT, btn -> doSend()));
        }

        if (statsMode) {
            addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W - 66, py + 6, 60, 14,
                    Text.translatable("pmchat.stats.back"), WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, WBTN_TEXT, btn -> {
                        statsMode = false;
                        clearConfirm = false;
                        rebuild();
                    }));

            // NEW (6.10 / звонки): секретный чат и звонок — только для личного диалога
            // с игроком, у кого стоит мод. Кнопки встают НАД рядами «в контакты»/«очистить»
            // (те остаются на месте, -44/-24), стек растёт вверх по мере надобности.
            boolean secretEligible = selected != null && !isFeedTab()
                    && !PmChatClient.isLocalChat(selected) && config.isModUser(selected);
            int extraRow = 64;
            if (secretEligible) {
                PmSecretSession.State st = PmChatClient.secretState(selected);
                String labelKey = switch (st) {
                    case ACTIVE -> "pmchat.secret.end";
                    case PENDING -> "pmchat.secret.pending";
                    default -> "pmchat.secret.start";
                };
                int labelColor = st == PmSecretSession.State.ACTIVE ? 0xFF8FD8A8
                        : st == PmSecretSession.State.PENDING ? 0xFFE0B040 : 0xFF9CC4DC;
                addDrawableChild(FlatButton.centered(textRenderer,
                        px + LEFT_W + 10, py + PANEL_H - extraRow, PANEL_W - LEFT_W - 20, 16,
                        Text.translatable(labelKey), WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, labelColor, btn -> {
                            if (st == PmSecretSession.State.NONE) {
                                PmChatClient.startSecretChat(selected);
                            } else {
                                PmChatClient.endSecretChat(selected);
                            }
                            rebuild();
                        }));
                extraRow += 20;
                if (st == PmSecretSession.State.ACTIVE) {
                    int ttl = PmChatClient.secretTtl(selected);
                    String ttlLabel = ttlLabel(ttl);
                    addDrawableChild(FlatButton.centered(textRenderer,
                            px + LEFT_W + 10, py + PANEL_H - extraRow, PANEL_W - LEFT_W - 20, 16,
                            Text.translatable("pmchat.secret.ttl", ttlLabel), WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER,
                            0xFFCB8A8A, btn -> {
                                PmChatClient.cycleSecretTtl(selected);
                                rebuild();
                            }));
                    extraRow += 20;
                }
            }
            // В контакты / из контактов (личный диалог)
            if (selected != null && !isFeedTab()) {
                boolean isC = config.isContact(selected);
                addDrawableChild(FlatButton.centered(textRenderer,
                        px + LEFT_W + 10, py + PANEL_H - 44, PANEL_W - LEFT_W - 20, 16,
                        Text.translatable(isC ? "pmchat.contact.remove" : "pmchat.contact.add"),
                        isC ? 0xFF5A4A1A : WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER,
                        0xFFF0C34E, btn -> {
                            config.toggleContact(selected);
                            rebuild();
                        }));
            }

            // Очистка диалога — с подтверждением вторым кликом
            if (selected != null) {
                addDrawableChild(FlatButton.centered(textRenderer,
                        px + LEFT_W + 10, py + PANEL_H - 24, PANEL_W - LEFT_W - 20, 16,
                        Text.translatable(clearConfirm ? "pmchat.clear.confirm" : "pmchat.clear"),
                        clearConfirm ? 0xFF6E2A22 : WBTN_BG,
                        clearConfirm ? 0xFF813328 : WBTN_BG_HOVER,
                        clearConfirm ? 0xFFA0463A : WBTN_BORDER,
                        0xFFE07A6A, btn -> {
                            if (!clearConfirm) {
                                clearConfirm = true;
                                rebuild();
                            } else {
                                history.clearConversation(selected);
                                selected = null;
                                statsMode = false;
                                clearConfirm = false;
                                rebuild();
                            }
                        }));
            }
        }

        if (groupCreateMode) {
            buildGroupComposer();
        }

        // Очистка ленты общего чата (без подтверждения — это кэш сессии)
        if (isGlobal && !statsMode && !groupCreateMode) {
            addDrawableChild(icon(px + PANEL_W - 28, py + 6, 20, 14, PmIcons.CLEAR, 0xFFE07A6A, "pmchat.tip.clear", btn -> {
                        if (PmChatClient.GLOBAL.equals(selected)) {
                            PmChatClient.clearGlobalChat();
                        } else if (PmChatClient.COREPROTECT.equals(selected)) {
                            PmChatClient.clearCoreProtect();
                        } else if (isGroupTab()) {
                            PmChatClient.deleteGroup(groupId());
                            selected = null; // «удалить группу»: вкладка исчезает
                        } else if (channelId() != null) {
                            PmChatClient.clearChannel(channelId());
                            selected = null; // «удалить чат»: вкладка исчезает
                        }
                        msgScroll = 0;
                        rebuild();
                    }));
        }
    }

    private boolean clearConfirm = false;

    /** Закрывает все режимы-композеры (фото/опрос/стикеры/деньги/эмодзи) — чтобы не накладывались. */
    private void closeModes() {
        imageMode = false;
        pollMode = false;
        stickerMode = false;
        moneyMode = false;
        emojiMode = false;
        mediaMode = false;
        uploadFailed = false;
        groupCreateMode = false;
        spoilerMode = false;
        moreMenuOpen = false;
    }

    /** NEW (6.10): человекочитаемая метка таймера самоуничтожения. */
    private static String ttlLabel(int seconds) {
        if (seconds <= 0) return Text.translatable("pmchat.secret.ttl.off").getString();
        if (seconds < 60) return seconds + Text.translatable("pmchat.secret.ttl.s").getString();
        if (seconds < 3600) return (seconds / 60) + Text.translatable("pmchat.secret.ttl.m").getString();
        return (seconds / 3600) + Text.translatable("pmchat.secret.ttl.h").getString();
    }

    /** Кнопка со своей пиксельной иконкой (PmIcons) и всплывающей подсказкой. */
    private FlatButton icon(int x, int y, int w, int h, String[] bmp, int color, String tipKey,
                            FlatButton.PressAction act) {
        FlatButton b = FlatButton.centered(textRenderer, x, y, w, h, Text.translatable(tipKey),
                WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, color, act).withIcon(bmp);
        b.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable(tipKey)));
        return b;
    }

    /** Кнопка распознавания речи: V — готов, %/… — загрузка модели, ■ — идёт запись. */
    private void addSttButton(int inputY) {
        String label;
        int color;
        switch (com.pmchat.client.PmStt.state) {
            case DOWNLOADING -> {
                label = com.pmchat.client.PmStt.progressPct + "%";
                color = 0xFFF0C34E;
            }
            case UNPACKING, LOADING -> {
                label = "…";
                color = 0xFFF0C34E;
            }
            case LISTENING -> {
                label = "■";
                color = 0xFFE07A6A;
            }
            case ERROR -> {
                label = "!";
                color = 0xFFE07A6A;
            }
            default -> {
                label = "V";
                color = WBTN_TEXT;
            }
        }
        addDrawableChild(FlatButton.centered(textRenderer, px + LEFT_W + 28, inputY, 22, 16,
                Text.literal(label), WBTN_BG, WBTN_BG_HOVER, WBTN_BORDER, color, btn -> {
                    switch (com.pmchat.client.PmStt.state) {
                        case READY -> com.pmchat.client.PmStt.startListening(text -> {
                            if (inputField != null) {
                                String current = inputField.getText();
                                inputField.setText((current.isBlank() ? text : current + " " + text).trim());
                                inputField.setFocused(true);
                            }
                        });
                        case LISTENING -> com.pmchat.client.PmStt.stopListening();
                        case NONE, ERROR -> com.pmchat.client.PmStt.ensureModelAsync();
                        default -> {
                        }
                    }
                }));
    }

    private void doSend() {
        if (inputField == null || selected == null) return;
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        if (isFeedTab()) {
            if (PmChatClient.GLOBAL.equals(selected)) {
                PmChatClient.sendGlobal(text);
            } else if (isGroupTab()) {
                PmChatClient.sendGroup(groupId(), text);
            } else {
                PmChatClient.sendChannel(channelId(), text);
            }
            inputField.setText("");
            inputText = "";
            msgScroll = 0;
            planeAt = System.currentTimeMillis();
            return;
        }
        // Режим правки своего сообщения
        if (editTarget != null) {
            PmChatClient.editMessage(selected, editTarget, text);
            editTarget = null;
            emojiMode = false;
            inputField.setText("");
            inputText = "";
            rebuild();
            return;
        }
        // NEW (6.10): в активном секретном чате сообщение шифруется вместо обычной отправки
        if (PmChatClient.isSecretActive(selected)) {
            PmChatClient.sendSecretMessage(selected, text);
            inputField.setText("");
            inputText = "";
            msgScroll = 0;
            planeAt = System.currentTimeMillis();
            return;
        }
        String replyHash = replyTarget != null ? PmHistory.msgHash(replyTarget.text) : null;
        PmChatClient.sendMessage(selected, text, replyHash, replyFragStart, replyFragLen, replyFragText);
        clearReply();
        emojiMode = false;
        inputField.setText("");
        inputText = "";
        msgScroll = 0;
        planeAt = System.currentTimeMillis();
    }

    private void toggleVoice() {
        if (selected == null) return;
        if (com.pmchat.client.PmVoice.isRecording()) {
            Path wav = com.pmchat.client.PmVoice.stopRecording();
            rebuild();
            if (wav != null) {
                int secs = com.pmchat.client.PmVoice.lastDuration();
                uploading = true;
                String target = selected;
                PmImages.upload(wav).whenComplete((res, err) ->
                        MinecraftClient.getInstance().execute(() -> {
                            uploading = false;
                            if (err == null && res != null) {
                                try {
                                    com.pmchat.client.PmVoice.cache(res[0], res[1], Files.readAllBytes(wav));
                                } catch (Exception ignored) {
                                }
                                PmChatClient.sendMessage(target, com.pmchat.client.PmWire.voice(res[0], res[1], secs));
                                msgScroll = 0;
                                planeAt = System.currentTimeMillis();
                            } else {
                                uploadFailed = true;
                            }
                            rebuild();
                        }));
            }
        } else {
            com.pmchat.client.PmVoice.startRecording();
            rebuild();
        }
    }

    // ---------- Панель стикеров/гифок ----------

    private boolean stickerMode = false;
    private int stickerTab = 0;
    private int stickerScroll = 0;
    private final List<String> stickerTabs = new ArrayList<>();  // названия вкладок
    private final List<Object[]> stickerCellRects = new ArrayList<>();
    private final List<Object[]> stickerTabRects = new ArrayList<>();

    private static Path stickersRoot() {
        Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("pmchat-stickers");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir;
    }

    private static boolean isSticker(Path p, boolean gif) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return gif ? n.endsWith(".gif") : n.endsWith(".png");
    }

    /** Собирает список вкладок: Недавние, Стикеры, Гифки + подпапки-паки. */
    private void loadStickerTabs() {
        stickerTabs.clear();
        stickerTabs.add("★");       // 0 — недавние
        stickerTabs.add("stickers"); // 1 — png из корня
        stickerTabs.add("gifs");     // 2 — gif из корня
        try (Stream<Path> s = Files.list(stickersRoot())) {
            s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted()
                    .forEach(stickerTabs::add); // 3+ — паки
        } catch (Exception ignored) {
        }
    }

    /** Файлы для текущей вкладки. */
    private List<Path> stickersForTab() {
        List<Path> out = new ArrayList<>();
        if (stickerTab == 0) { // недавние
            for (String p : config.recentStickers) {
                Path path = Path.of(p);
                if (Files.exists(path)) out.add(path);
            }
            return out;
        }
        if (stickerTab == 1 || stickerTab == 2) { // стикеры / гифки из корня
            boolean gif = stickerTab == 2;
            try (Stream<Path> s = Files.list(stickersRoot())) {
                s.filter(Files::isRegularFile).filter(p -> isSticker(p, gif)).sorted().forEach(out::add);
            } catch (Exception ignored) {
            }
            return out;
        }
        // Пак-подпапка
        String pack = stickerTabs.get(stickerTab);
        try (Stream<Path> s = Files.list(stickersRoot().resolve(pack))) {
            s.filter(Files::isRegularFile)
                    .filter(p -> isSticker(p, false) || isSticker(p, true)).sorted().forEach(out::add);
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Панель стикеров: вкладки сверху + сетка превью. */
    private void renderStickerPanel(DrawContext context, int mouseX, int mouseY, int top, int bottom) {
        stickerCellRects.clear();
        stickerTabRects.clear();
        int x0 = px + LEFT_W + 2;
        int x1 = px + PANEL_W - 2;
        context.fill(x0, top, x1, bottom, PANEL_BG);

        // Ряд вкладок
        int tx = x0 + 4;
        int ty = top + 3;
        for (int i = 0; i < stickerTabs.size(); i++) {
            String label = switch (i) {
                case 0 -> "★";
                case 1 -> Text.translatable("pmchat.sticker.tab.stickers").getString();
                case 2 -> Text.translatable("pmchat.sticker.tab.gifs").getString();
                default -> stickerTabs.get(i);
            };
            int w = textRenderer.getWidth(label) + 8;
            if (tx + w > x1 - 4) break;
            boolean active = i == stickerTab;
            context.fill(tx, ty, tx + w, ty + 12, active ? ROW_SELECTED : ROW_HOVER);
            context.drawText(textRenderer, label, tx + 4, ty + 2, active ? 0xFFF0C34E : NAME_TEXT, false);
            stickerTabRects.add(new Object[]{tx, ty, w, 12, i});
            tx += w + 3;
        }

        // Сетка превью
        List<Path> items = stickersForTab();
        int cell = 30, pad = 4;
        int cols = Math.max(1, (x1 - x0 - pad) / (cell + pad));
        int gridTop = top + 18;
        int rows = Math.max(0, (bottom - gridTop - pad) / (cell + pad));
        int maxScroll = Math.max(0, (items.size() + cols - 1) / cols - rows);
        stickerScroll = Math.max(0, Math.min(stickerScroll, maxScroll));

        if (items.isEmpty()) {
            context.drawText(textRenderer, Text.translatable(stickerTab == 0
                    ? "pmchat.sticker.norecent" : "pmchat.sticker.empty"),
                    x0 + 6, gridTop + 4, SUBTLE, false);
            return;
        }

        int start = stickerScroll * cols;
        int idx = start;
        for (int r = 0; r < rows && idx < items.size(); r++) {
            for (int c = 0; c < cols && idx < items.size(); c++, idx++) {
                Path file = items.get(idx);
                int cxp = x0 + pad + c * (cell + pad);
                int cyp = gridTop + pad + r * (cell + pad);
                boolean hov = mouseX >= cxp && mouseX < cxp + cell && mouseY >= cyp && mouseY < cyp + cell;
                context.fill(cxp - 1, cyp - 1, cxp + cell + 1, cyp + cell + 1, hov ? ROW_SELECTED : 0xFF15303D);
                PmImages.Entry e = PmImages.loadLocal(file.toAbsolutePath().toString(), file);
                if (e.state == PmImages.State.READY && e.currentTexture() != null && e.width > 0) {
                    float sc = Math.min((float) cell / e.width, (float) cell / e.height);
                    int w = Math.max(1, Math.round(e.width * sc));
                    int h = Math.max(1, Math.round(e.height * sc));
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, e.currentTexture(),
                            cxp + (cell - w) / 2, cyp + (cell - h) / 2, 0f, 0f, w, h, e.width, e.height, e.width, e.height);
                }
                stickerCellRects.add(new Object[]{cxp, cyp, cell, cell, file});
            }
        }
        if (maxScroll > 0) {
            String pg = (stickerScroll + 1) + "/" + (maxScroll + 1);
            context.drawText(textRenderer, pg, x1 - 6 - textRenderer.getWidth(pg), top + 5, SUBTLE, false);
        }
    }

    private void loadStickers() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "config/pmchat-stickers");
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        List<Path> found = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir.toPath())) {
            stream.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".png") || n.endsWith(".gif");
                    })
                    .sorted()
                    .limit(12)
                    .forEach(found::add);
        } catch (Exception ignored) {
        }
        stickers = found;
    }

    /** Стикер: одноразовая загрузка на хостинг с кэшем id в конфиге. */
    private void sendSticker(Path sticker) {
        String name = sticker.getFileName().toString();
        config.pushRecentSticker(sticker.toAbsolutePath().toString());
        String cached = config.stickerCache.get(name);
        if (cached != null) {
            int sep = cached.indexOf('|');
            String code = sep > 0 ? cached.substring(0, sep) : "c";
            String id = sep > 0 ? cached.substring(sep + 1) : cached;
            PmChatClient.sendMessage(selected, com.pmchat.client.PmWire.img(code, id));
            imageMode = false;
            stickerMode = false;
            spoilerMode = false;
            msgScroll = 0;
            planeAt = System.currentTimeMillis();
            rebuild();
            return;
        }
        uploading = true;
        String target = selected;
        PmImages.upload(sticker).whenComplete((res, err) ->
                MinecraftClient.getInstance().execute(() -> {
                    uploading = false;
                    if (err == null && res != null) {
                        config.stickerCache.put(name, res[0] + "|" + res[1]);
                        config.save();
                        try {
                            com.pmchat.client.PmImages.preload(res[0], res[1], Files.readAllBytes(sticker));
                        } catch (Exception ignored) {
                        }
                        PmChatClient.sendMessage(target, com.pmchat.client.PmWire.img(res[0], res[1]));
                        imageMode = false;
                        stickerMode = false;
                        spoilerMode = false;
                        msgScroll = 0;
                        planeAt = System.currentTimeMillis();
                    } else {
                        uploadFailed = true;
                    }
                    rebuild();
                }));
    }

    private void loadScreenshots() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "screenshots");
        List<Path> found = new ArrayList<>();
        if (dir.isDirectory()) {
            try (Stream<Path> stream = Files.list(dir.toPath())) {
                stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .sorted(Comparator.comparingLong(p -> {
                            try {
                                return -Files.getLastModifiedTime(p).toMillis();
                            } catch (Exception e) {
                                return 0L;
                            }
                        }))
                        .limit(7)
                        .forEach(found::add);
            } catch (Exception ignored) {
            }
        }
        screenshots = found;
    }

    // ---------- Медиа-меню: видео и аудиофайлы ----------

    private static Path mediaDir(String sub) {
        Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(sub);
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir;
    }

    private static List<Path> listByExt(Path dir, String... exts) {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                for (String e : exts) if (n.endsWith(e)) return true;
                return false;
            }).sorted().limit(30).forEach(out::add);
        } catch (Exception ignored) {
        }
        return out;
    }

    private void loadMedia() {
        videoFiles = listByExt(mediaDir("pmchat-video"), ".mp4", ".webm", ".mkv", ".mov", ".avi");
        audioFiles = listByExt(mediaDir("pmchat-audio"), ".wav", ".au", ".aif", ".aiff");
    }

    /** Пикер медиа: секции «Видео» и «Аудио», клик — загрузка и отправка. */
    private void renderMediaPicker(DrawContext context, int mouseX, int mouseY, int areaTop, int areaBottom) {
        mediaRects.clear();
        int x = px + LEFT_W + 8;
        int w = PANEL_W - LEFT_W - 16;
        context.fill(px + LEFT_W + 1, areaTop, px + PANEL_W - 1, py + PANEL_H - 2, PANEL_BG);
        context.drawText(textRenderer, Text.translatable("pmchat.media.pick"), x, areaTop + 2, 0xFF9CC4DC, false);

        // NEW (4.9): переключатель «Спойлер» — применяется к следующему отправленному видео
        Text spoilerLbl = Text.translatable("pmchat.spoiler.toggle");
        int spoilerW = textRenderer.getWidth(spoilerLbl) + 18;
        drawSpoilerToggle(context, mouseX, mouseY, px + PANEL_W - 10 - spoilerW, areaTop + 1);

        if (uploading) {
            Text label = Text.translatable("pmchat.image.uploading");
            context.drawText(textRenderer, label,
                    px + LEFT_W + (PANEL_W - LEFT_W - textRenderer.getWidth(label)) / 2,
                    (areaTop + areaBottom) / 2, 0xFFF0C34E, false);
            return;
        }
        int y = areaTop + 16;

        // Видео
        context.drawText(textRenderer, "▶ " + Text.translatable("pmchat.media.video").getString()
                + " (config/pmchat-video)", x, y, 0xFF6FBF8B, false);
        y += 12;
        if (videoFiles.isEmpty()) {
            context.drawText(textRenderer, Text.translatable("pmchat.media.empty"), x + 4, y, SUBTLE, false);
            y += 12;
        } else {
            for (Path f : videoFiles) {
                if (y + 15 > areaBottom) break;
                boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
                context.fill(x, y, x + w, y + 14, hov ? ROW_SELECTED : ROW_HOVER);
                context.drawText(textRenderer, "▶ " + trim(f.getFileName().toString(), w - 12), x + 5, y + 3, NAME_TEXT, false);
                mediaRects.add(new Object[]{x, y, w, 14, f, true});
                y += 16;
            }
        }

        // Аудио
        y += 4;
        if (y + 14 <= areaBottom) {
            context.drawText(textRenderer, "♪ " + Text.translatable("pmchat.media.audio").getString()
                    + " (config/pmchat-audio, WAV)", x, y, 0xFFF0C34E, false);
            y += 12;
            if (audioFiles.isEmpty()) {
                context.drawText(textRenderer, Text.translatable("pmchat.media.empty"), x + 4, y, SUBTLE, false);
            } else {
                for (Path f : audioFiles) {
                    if (y + 15 > areaBottom) break;
                    boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
                    context.fill(x, y, x + w, y + 14, hov ? ROW_SELECTED : ROW_ALT);
                    context.drawText(textRenderer, "♪ " + trim(f.getFileName().toString(), w - 12), x + 5, y + 3, NAME_TEXT, false);
                    mediaRects.add(new Object[]{x, y, w, 14, f, false});
                    y += 16;
                }
            }
        }
        if (uploadFailed) {
            context.drawText(textRenderer, Text.translatable("pmchat.image.failed"), x, areaBottom - 10, 0xFFE07A6A, false);
        }
    }

    /** Загрузка видеофайла на хостинг + отправка pmc vid. */
    private void startVideoUpload(Path file) {
        if (uploading || selected == null) return;
        uploading = true;
        uploadFailed = false;
        String target = selected;
        boolean spoiler = spoilerMode; // NEW (4.9): снимок настройки на момент отправки
        PmImages.upload(file).whenComplete((res, err) ->
                MinecraftClient.getInstance().execute(() -> {
                    uploading = false;
                    if (err == null && res != null) {
                        PmChatClient.sendMessage(target, com.pmchat.client.PmWire.vid(res[0], res[1], spoiler));
                        mediaMode = false;
                        spoilerMode = false;
                        msgScroll = 0;
                        planeAt = System.currentTimeMillis();
                    } else {
                        uploadFailed = true;
                    }
                    rebuild();
                }));
    }

    /** Загрузка аудиофайла (WAV) + отправка как голосовое pmc voice. */
    private void startAudioUpload(Path file) {
        if (uploading || selected == null) return;
        int secs = com.pmchat.client.PmVoice.fileDurationSeconds(file);
        if (secs <= 0) secs = 1;
        int seconds = secs;
        uploading = true;
        uploadFailed = false;
        String target = selected;
        PmImages.upload(file).whenComplete((res, err) ->
                MinecraftClient.getInstance().execute(() -> {
                    uploading = false;
                    if (err == null && res != null) {
                        try {
                            com.pmchat.client.PmVoice.cache(res[0], res[1], Files.readAllBytes(file));
                        } catch (Exception ignored) {
                        }
                        PmChatClient.sendMessage(target, com.pmchat.client.PmWire.voice(res[0], res[1], seconds));
                        mediaMode = false;
                        spoilerMode = false;
                        msgScroll = 0;
                        planeAt = System.currentTimeMillis();
                    } else {
                        uploadFailed = true;
                    }
                    rebuild();
                }));
    }

    private void startUpload(Path file) {
        if (uploading || selected == null) return;
        uploading = true;
        uploadFailed = false;
        String target = selected;
        boolean spoiler = spoilerMode; // NEW (4.9): снимок настройки на момент отправки
        PmImages.upload(file).whenComplete((res, err) ->
                MinecraftClient.getInstance().execute(() -> {
                    uploading = false;
                    if (err == null && res != null) {
                        try {
                            com.pmchat.client.PmImages.preload(res[0], res[1], Files.readAllBytes(file));
                        } catch (Exception ignored) {
                        }
                        PmChatClient.sendMessage(target, com.pmchat.client.PmWire.img(res[0], res[1], spoiler));
                        imageMode = false;
                        spoilerMode = false;
                        msgScroll = 0;
                        planeAt = System.currentTimeMillis();
                    } else {
                        uploadFailed = true;
                    }
                    rebuild();
                }));
    }

    private void doPay() {
        if (amountField == null || selected == null) return;
        try {
            long amount = Long.parseLong(amountField.getText().trim().replace(" ", ""));
            if (amount <= 0) return;
            PmChatClient.sendMoney(selected, amount);
            amountField.setText("");
            amountText = "";
            moneyMode = false;
            msgScroll = 0;
            planeAt = System.currentTimeMillis();
            rebuild();
        } catch (NumberFormatException ignored) {
        }
    }

    // ---------- Отрисовка ----------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Кнопка распознавания меняет вид при смене состояния
        if (com.pmchat.client.PmStt.consumeDirty()) {
            rebuild();
        }

        // Панель
        context.fill(px + 2, py, px + PANEL_W - 2, py + PANEL_H, PANEL_BG);
        context.fill(px, py + 2, px + PANEL_W, py + PANEL_H - 2, PANEL_BG);
        context.fill(px, py + 2, px + LEFT_W, py + PANEL_H - 2, LEFT_BG);
        context.fill(px + LEFT_W, py + 2, px + LEFT_W + 1, py + PANEL_H - 2, DIVIDER);
        context.drawStrokedRectangle(px, py, PANEL_W, PANEL_H, PANEL_BORDER);

        context.drawText(textRenderer, Text.literal("✉"), px + 8, py + 8, 0xFF6FBF8B, false);
        context.drawText(textRenderer, Text.translatable("screen.pmchat.title"), px + 20, py + 8, TITLE, false);

        renderConversationList(context, mouseX, mouseY);

        if (groupCreateMode) {
            renderGroupCreate(context);
        } else if (statsMode) {
            renderStats(context);
        } else if (selected == null) {
            Text hint = Text.translatable("pmchat.empty.chat");
            int hx = px + LEFT_W + (PANEL_W - LEFT_W - textRenderer.getWidth(hint)) / 2;
            context.drawText(textRenderer, hint, hx, py + PANEL_H / 2 - 4, SUBTLE, false);
        } else {
            renderChat(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);

        renderPlane(context);
        renderCtxMenu(context, mouseX, mouseY);
        renderFragSelector(context, mouseX, mouseY);
        renderPinList(context, mouseX, mouseY);
        renderSearchResults(context, mouseX, mouseY);

        // Всплывашка «Скопировано»
        if (copiedAt > 0) {
            long age = System.currentTimeMillis() - copiedAt;
            if (age < 900) {
                float fade = age < 600 ? 1f : 1f - (age - 600) / 300f;
                Text label = Text.translatable("pmchat.copied");
                int lw = textRenderer.getWidth(label) + 8;
                int lx = Math.min(copiedX, width - lw - 2);
                int ly = copiedY - 14 - Math.round(age / 90f);
                context.fill(lx, ly, lx + lw, ly + 12, applyAlpha(0xE62E5F46, fade));
                context.drawText(textRenderer, label, lx + 4, ly + 2, applyAlpha(0xFFCFEEDA, fade), false);
            } else {
                copiedAt = -1;
            }
        }

        // Подсказка режима пересылки
        if (forwardBuffer != null) {
            Text hint = Text.translatable("pmchat.fwd.pick");
            int hw = textRenderer.getWidth(hint) + 12;
            int hx = px + (PANEL_W - hw) / 2;
            context.fill(hx, py - 16, hx + hw, py - 3, 0xF03A6FB0);
            context.drawText(textRenderer, hint, hx + 6, py - 13, 0xFFFFFFFF, false);
        }

        // Фото на весь экран — поверх всего
        if (fullscreenImg != null) {
            renderFullscreenImage(context);
        }
        // NEW (5.3): видео/медиа — поверх всего. Подготовка (yt-dlp) и
        // полноэкранное видео рисуются здесь; активную сессию хранит PmMedia,
        // свёрнутое окошко (видео или музыка) рисует сам PmMedia.
        com.pmchat.client.PmMedia media = com.pmchat.client.PmMedia.get();
        if (videoResolving || videoOpenFailed) {
            renderVideoPlayer(context, mouseX, mouseY);
        } else if (media.hasActive()) {
            if (media.isMinimized()) media.renderMini(context, mouseX, mouseY, true);
            else renderVideoPlayer(context, mouseX, mouseY);
        }
    }

    /** NEW (4.3): открыть видео по ссылке во встроенном плеере (закрывает предыдущий сеанс, если был). */
    private void openVideoPlayer(String url) {
        closeVideoPlayer();
        videoUrl = url;
        videoOpenedAt = System.currentTimeMillis();
        final int seq = ++videoSeq;
        if (com.pmchat.client.PmYouTube.isYouTube(url)) {
            // NEW (5.1): напрямую отдать VLC ссылку на YouTube к 2026 нельзя —
            // потоки заперты proof-of-origin токеном. Скачиваем ролик через
            // yt-dlp во временный файл (в фоне, с прогрессом) и играем его.
            videoResolving = true;
            videoStatusText = null;
            Thread t = new Thread(() -> {
                com.pmchat.client.PmYtDlp.Media media = com.pmchat.client.PmYtDlp.download(url, st ->
                        MinecraftClient.getInstance().execute(() -> {
                            if (seq == videoSeq) videoStatusText = st;
                        }));
                MinecraftClient.getInstance().execute(() -> {
                    if (seq != videoSeq) {
                        // плеер уже закрыли/переоткрыли — убираем осиротевшие файлы
                        if (media != null) {
                            com.pmchat.client.PmYtDlp.cleanup(media.video());
                            com.pmchat.client.PmYtDlp.cleanup(media.audio());
                        }
                        return;
                    }
                    videoResolving = false;
                    videoStatusText = null;
                    videoOpenedAt = System.currentTimeMillis();
                    if (media != null) {
                        String audioPath = media.audio() != null ? media.audio().getAbsolutePath() : null;
                        startVideoSession(media.video().getAbsolutePath(), audioPath,
                                media.video(), media.audio(), url);
                    } else {
                        // yt-dlp не смог (бот-проверка/нет бинарника) — состояние
                        // ошибки с кнопкой «Открыть в браузере».
                        videoNeedsCookies = com.pmchat.client.PmYtDlp.lastNeededSignIn();
                        videoOpenFailed = true;
                    }
                });
            }, "pmchat-ytdlp");
            t.setDaemon(true);
            t.start();
        } else {
            startVideoSession(url, null, null, null, url);
        }
    }

    /** Создаёт VLC-сеанс и передаёт владение персистентному PmMedia. */
    private void startVideoSession(String mediaUrl, String audioSlaveUrl,
                                   java.io.File videoFile, java.io.File audioFile, String sourceUrl) {
        try {
            com.pmchat.client.PmVlc.Session s = com.pmchat.client.PmVlc.open(mediaUrl, audioSlaveUrl);
            String title = sourceUrl != null ? sourceUrl.replaceFirst("^https?://(www\\.)?", "") : "";
            com.pmchat.client.PmMedia.get().startVideo(s, videoFile, audioFile, sourceUrl, title);
        } catch (Exception e) {
            videoOpenFailed = true;
        }
    }

    /** Полное закрытие плеера (крестик/Esc): останавливает воспроизведение и подготовку. */
    private void closeVideoPlayer() {
        videoSeq++; // отменяем висящие фоновые загрузки
        com.pmchat.client.PmMedia.get().stop();
        videoResolving = false;
        videoOpenFailed = false;
        videoNeedsCookies = false;
        videoStatusText = null;
        videoDragSeek = false;
        videoDragVolume = false;
        videoUrl = null;
        videoFallbackRect = null;
        videoBrowserRect = null;
    }

    /**
     * NEW (5.0): переработанный оверлей плеера — заголовок с источником и
     * закрытием сверху, кадр с рамкой по центру, аккуратная плавающая панель
     * управления снизу (play/время/перемотка/громкость/скорость/браузер),
     * состояния «резолвим ссылку / буферизация / не получилось» и драг
     * ползунков (перемотка и громкость тянутся мышью).
     */
    private void renderVideoPlayer(DrawContext context, int mouseX, int mouseY) {
        com.pmchat.client.PmMedia media = com.pmchat.client.PmMedia.get();
        String url = media.sourceUrl() != null ? media.sourceUrl() : videoUrl;
        if (url == null && !videoResolving && !videoOpenFailed) return;
        com.pmchat.client.PmVlc.Session s = media.session();
        if (s != null) s.tick();

        // Драг ползунков: пока зажата ЛКМ — тянем, отпустили — закончили.
        boolean lmbDown = GLFW.glfwGetMouseButton(
                MinecraftClient.getInstance().getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!lmbDown) {
            videoDragSeek = false;
            videoDragVolume = false;
        } else if (s != null) {
            if (videoDragSeek && videoBarRect != null && videoBarRect[2] > 0) {
                s.seekFraction((mouseX - videoBarRect[0]) / (float) videoBarRect[2]);
            }
            if (videoDragVolume && videoVolRect != null && videoVolRect[2] > 0) {
                float f = Math.max(0f, Math.min(1f, (mouseX - videoVolRect[0]) / (float) videoVolRect[2]));
                s.setVolume(Math.round(f * 150));
            }
        }

        // Затемнение
        context.fill(0, 0, width, height, 0xF0070B09);

        // ---- Заголовок ----
        String title = url != null ? url.replaceFirst("^https?://(www\\.)?", "") : "";
        if (title.length() > 64) title = title.substring(0, 61) + "…";
        context.drawText(textRenderer, "▶ " + title, 14, 13, 0xFF9CC4DC, false);

        int closeSz = 20;
        int closeX = width - 12 - closeSz, closeY = 9;
        videoCloseRect = new int[]{closeX, closeY, closeSz, closeSz};
        boolean hovClose = inRect(mouseX, mouseY, videoCloseRect);
        context.fill(closeX, closeY, closeX + closeSz, closeY + closeSz, hovClose ? 0xFF6E2A22 : 0x66223530);
        PmIcons.draw(context, PmIcons.CLEAR, closeX, closeY, closeSz, closeSz,
                hovClose ? 0xFFE07A6A : 0xFFB8C6CE);

        // Свернуть в окошко (слева от крестика)
        int minX = closeX - 6 - closeSz, minY = closeY;
        videoMinRect = new int[]{minX, minY, closeSz, closeSz};
        boolean hovMin = inRect(mouseX, mouseY, videoMinRect);
        context.fill(minX, minY, minX + closeSz, minY + closeSz, hovMin ? 0xFF2A4A5C : 0x66223530);
        PmIcons.draw(context, PmIcons.MINIMIZE, minX, minY, closeSz, closeSz,
                hovMin ? 0xFFEDF3F0 : 0xFFB8C6CE);

        int barH = 38;
        boolean failed = videoOpenFailed || (s != null && s.hasError());
        boolean noFrames = s == null || s.width() <= 0 || s.height() <= 0;
        boolean stuck = failed
                || (noFrames && !videoResolving && System.currentTimeMillis() - videoOpenedAt > 8000);

        // ---- Кадр видео ----
        videoFallbackRect = null;
        if (!noFrames) {
            int vw = s.width(), vh = s.height();
            float scale = Math.min((width - 48f) / vw, (height - 76f - barH) / vh);
            scale = Math.min(scale, 6f);
            int w = Math.max(1, Math.round(vw * scale));
            int h = Math.max(1, Math.round(vh * scale));
            int ix = (width - w) / 2;
            int iy = 32 + (height - 44 - barH - 32 - h) / 2 + 4;
            videoImgRect = new int[]{ix, iy, w, h};
            context.fill(ix - 2, iy - 2, ix + w + 2, iy + h + 2, 0xFF0B120F);
            context.drawStrokedRectangle(ix - 2, iy - 2, w + 4, h + 4, 0xFF2A4A5C);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, s.textureId(), ix, iy,
                    0f, 0f, w, h, vw, vh, vw, vh);
        } else {
            videoImgRect = null;
            // ---- Плашка состояния по центру ----
            // Для «нужен вход» плашка выше — там ещё строка-подсказка про куки.
            boolean cookiesHint = stuck && videoNeedsCookies;
            int pw = cookiesHint ? 320 : 280;
            int ph = stuck ? (cookiesHint ? 100 : 84) : 64;
            int pxc = (width - pw) / 2, pyc = (height - ph) / 2 - 10;
            context.fill(pxc, pyc, pxc + pw, pyc + ph, 0xE0101A16);
            context.drawStrokedRectangle(pxc, pyc, pw, ph, 0xFF2A4A5C);
            if (stuck) {
                Text err = Text.translatable(cookiesHint ? "pmchat.video.signin" : "pmchat.video.stuck");
                context.drawText(textRenderer, err,
                        pxc + (pw - textRenderer.getWidth(err)) / 2, pyc + 14, 0xFFE0B08A, false);
                if (cookiesHint) {
                    Text hint = Text.translatable("pmchat.video.signin.hint");
                    context.drawText(textRenderer, hint,
                            pxc + (pw - textRenderer.getWidth(hint)) / 2, pyc + 30, 0xFF8FA6B4, false);
                }
                Text openInBrowser = Text.translatable("pmchat.video.openweb");
                int bw2 = textRenderer.getWidth(openInBrowser) + 24;
                int bx2 = pxc + (pw - bw2) / 2, by2 = pyc + ph - 32;
                videoFallbackRect = new int[]{bx2, by2, bw2, 18};
                boolean hov = inRect(mouseX, mouseY, videoFallbackRect);
                context.fill(bx2, by2, bx2 + bw2, by2 + 18, hov ? 0xFF2E5C48 : 0xFF1C3644);
                context.drawStrokedRectangle(bx2, by2, bw2, 18, 0xFF4C8A66);
                PmIcons.draw(context, PmIcons.LINKOUT, bx2 + 2, by2 + 4, 12, 10, 0xFF8FD8A8);
                context.drawText(textRenderer, openInBrowser, bx2 + 15, by2 + 5, 0xFF8FD8A8, false);
            } else {
                // Загрузка: статус + бегущие точки. Для YouTube показываем стадию
                // yt-dlp: «Получаю yt-dlp» пока качается бинарник, «Скачивание N%»
                // при загрузке ролика; иначе — VLC-декодирование.
                String base;
                String st = videoStatusText;
                if (videoResolving) {
                    if ("yt-dlp".equals(st)) {
                        base = Text.translatable("pmchat.video.gettingtool").getString();
                    } else if (st != null && st.endsWith("%")) {
                        base = Text.translatable("pmchat.video.downloading").getString() + " " + st;
                    } else {
                        base = Text.translatable("pmchat.video.resolving").getString();
                    }
                } else {
                    base = Text.translatable("pmchat.video.decoding").getString();
                    if (s != null && s.bufferPercent() >= 0 && s.bufferPercent() < 100) {
                        base += " " + (int) s.bufferPercent() + "%";
                    }
                }
                int dots = (int) (System.currentTimeMillis() / 350 % 4);
                // Центруем по тексту без точек, чтобы надпись не «дышала»
                int lx = pxc + (pw - textRenderer.getWidth(base)) / 2;
                context.drawText(textRenderer, base + " " + "•".repeat(dots),
                        lx, pyc + (ph - 8) / 2, 0xFFB8C6CE, false);
            }
        }

        // ---- Панель управления (только когда сеанс жив и не в ошибке) ----
        videoPlayRect = null;
        videoBarRect = null;
        videoVolRect = null;
        videoRateRect = null;
        videoBrowserRect = null;
        if (s == null || failed) return;

        int barW = Math.min(560, width - 28);
        int barX = (width - barW) / 2;
        int barY = height - barH - 12;
        context.fill(barX, barY, barX + barW, barY + barH, 0xE8101A16);
        context.drawStrokedRectangle(barX, barY, barW, barH, 0xFF2A4A5C);

        // Play/Pause
        int btn = 24;
        int bx = barX + 7, by = barY + (barH - btn) / 2;
        videoPlayRect = new int[]{bx, by, btn, btn};
        boolean hovPlay = inRect(mouseX, mouseY, videoPlayRect);
        context.fill(bx, by, bx + btn, by + btn, hovPlay ? 0xFF2A4A5C : 0xFF16241E);
        PmIcons.draw(context, s.isPlaying() ? PmIcons.PAUSE : PmIcons.PLAY, bx, by, btn, btn, 0xFFEDF3F0);

        // Время
        String timeStr = fmtTime(s.timeMs()) + " / " + fmtTime(s.lengthMs());
        int timeX = bx + btn + 9;
        context.drawText(textRenderer, timeStr, timeX, barY + (barH - 8) / 2, 0xFF9CC4DC, false);
        int timeEnd = timeX + textRenderer.getWidth("88:88 / 88:88");

        // Правый кластер: [громкость][скорость][в браузере]
        int browserSz = 20;
        int browserX = barX + barW - 7 - browserSz;
        int browserY = barY + (barH - browserSz) / 2;
        videoBrowserRect = new int[]{browserX, browserY, browserSz, browserSz};
        boolean hovBrowser = inRect(mouseX, mouseY, videoBrowserRect);
        context.fill(browserX, browserY, browserX + browserSz, browserY + browserSz,
                hovBrowser ? 0xFF2E5C48 : 0xFF16241E);
        PmIcons.draw(context, PmIcons.LINKOUT, browserX, browserY, browserSz, browserSz, 0xFF8FD8A8);

        int rateW = 32, rateH = 20;
        int rateX = browserX - 6 - rateW;
        int rateY = barY + (barH - rateH) / 2;
        videoRateRect = new int[]{rateX, rateY, rateW, rateH};
        boolean hovRate = inRect(mouseX, mouseY, videoRateRect);
        context.fill(rateX, rateY, rateX + rateW, rateY + rateH, hovRate ? 0xFF2A4A5C : 0xFF16241E);
        String rateLabel = trimRate(s.getRate()) + "x";
        context.drawText(textRenderer, rateLabel,
                rateX + (rateW - textRenderer.getWidth(rateLabel)) / 2, rateY + 6, 0xFFF0C34E, false);

        int cy = barY + barH / 2;
        int volW = 44;
        int volX = rateX - 6 - volW;
        PmIcons.draw(context, PmIcons.VOLUME, volX - 13, cy - 5, 11, 11, 0xFF9CC4DC);
        videoVolRect = new int[]{volX, cy - 6, volW, 12};
        boolean hovVol = inRect(mouseX, mouseY, videoVolRect) || videoDragVolume;
        context.fill(volX, cy - 1, volX + volW, cy + 2, 0xFF23352E);
        float volFrac = Math.max(0f, Math.min(1f, s.getVolume() / 150f));
        context.fill(volX, cy - 1, volX + Math.round(volW * volFrac), cy + 2, 0xFF9CC4DC);
        if (hovVol) {
            int volKnobX = volX + Math.round(volW * volFrac);
            context.fill(volKnobX - 1, cy - 4, volKnobX + 2, cy + 5, 0xFFEDF3F0);
        }

        // Полоса перемотки — всё оставшееся место между временем и громкостью
        int seekX = timeEnd + 10;
        int seekW = volX - 22 - seekX;
        if (seekW > 30) {
            boolean hovSeek = mouseX >= seekX && mouseX < seekX + seekW
                    && mouseY >= cy - 7 && mouseY < cy + 8;
            videoBarRect = new int[]{seekX, cy - 7, seekW, 14};
            int trackH = (hovSeek || videoDragSeek) ? 5 : 3;
            int ty = cy - trackH / 2;
            context.fill(seekX, ty, seekX + seekW, ty + trackH, 0xFF23352E);
            float pos = Math.max(0f, Math.min(1f, s.positionFraction()));
            context.fill(seekX, ty, seekX + Math.round(seekW * pos), ty + trackH, 0xFF6FBF8B);
            int knobX = seekX + Math.round(seekW * pos);
            context.fill(knobX - 1, cy - 5, knobX + 2, cy + 5, 0xFFEDF3F0);
            // Подсказка времени под курсором
            if ((hovSeek || videoDragSeek) && s.lengthMs() > 0) {
                float f = Math.max(0f, Math.min(1f, (mouseX - seekX) / (float) seekW));
                String tip = fmtTime((long) (s.lengthMs() * f));
                int tw = textRenderer.getWidth(tip) + 8;
                int tx = Math.max(seekX, Math.min(seekX + seekW - tw, mouseX - tw / 2));
                context.fill(tx, cy - 22, tx + tw, cy - 10, 0xF0223530);
                context.drawText(textRenderer, tip, tx + 4, cy - 20, 0xFFEDF3F0, false);
            }
        }
    }

    private static boolean inRect(int mx, int my, int[] r) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    /** NEW (4.9): «замыленная» плашка вместо фото/видео — как в Telegram/Discord, до клика. */
    private void drawSpoilerCover(DrawContext context, int x, int y, int w, int h, float alpha) {
        context.fill(x, y, x + w, y + h, applyAlpha(0xFF1C1C1C, alpha));
        // Мозаика из квадратов — намёк на «размытие» без реального блюра
        int cell = Math.max(4, Math.min(w, h) / 8);
        boolean toggle = false;
        for (int cy = y; cy < y + h; cy += cell) {
            toggle = !toggle;
            boolean t2 = toggle;
            for (int cx = x; cx < x + w; cx += cell) {
                t2 = !t2;
                if (t2) {
                    context.fill(cx, cy, Math.min(cx + cell, x + w), Math.min(cy + cell, y + h),
                            applyAlpha(0xFF2E2E2E, alpha));
                }
            }
        }
        int iconSize = Math.min(18, Math.min(w, h) - 4);
        if (iconSize >= 9) {
            PmIcons.draw(context, PmIcons.SPOILER, x + w / 2 - iconSize / 2, y + h / 2 - iconSize / 2 - 5,
                    iconSize, iconSize, applyAlpha(0xFFEDF3F0, alpha));
        }
        Text label = Text.translatable("pmchat.spoiler.label");
        int lw = textRenderer.getWidth(label);
        if (lw + 4 <= w) {
            context.drawText(textRenderer, label, x + w / 2 - lw / 2, y + h / 2 + 6,
                    applyAlpha(0xFFEDF3F0, alpha), false);
        }
    }

    /** NEW (4.9): маленький переключатель «Спойлер» — рисует и обновляет spoilerToggleRect для клика. */
    private void drawSpoilerToggle(DrawContext context, int mouseX, int mouseY, int x, int y) {
        Text label = Text.translatable("pmchat.spoiler.toggle");
        int w = textRenderer.getWidth(label) + 18;
        spoilerToggleRect = new int[]{x, y, w, 12};
        boolean hov = inRect(mouseX, mouseY, spoilerToggleRect);
        int bg = spoilerMode ? 0xFF4C8A66 : (hov ? ROW_HOVER : ROW_ALT);
        context.fill(x, y, x + w, y + 11, bg);
        PmIcons.draw(context, PmIcons.SPOILER, x + 2, y + 1, 9, 9, spoilerMode ? 0xFFEDF3F0 : SUBTLE);
        context.drawText(textRenderer, label, x + 14, y + 2, spoilerMode ? 0xFFEDF3F0 : PREVIEW_TEXT, false);
    }

    private static String fmtTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long m = totalSec / 60, s = totalSec % 60;
        return String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    private static String trimRate(float rate) {
        if (rate == Math.round(rate)) return String.valueOf(Math.round(rate));
        return String.valueOf(Math.round(rate * 100) / 100f);
    }

    /** Затемнённый оверлей с картинкой, вписанной в окно. */
    private void renderFullscreenImage(DrawContext context) {
        PmImages.Entry e = fullscreenImg;
        context.fill(0, 0, width, height, 0xE6000000);
        if (e.state != PmImages.State.READY || e.currentTexture() == null || e.width <= 0 || e.height <= 0) {
            return;
        }
        float scale = Math.min((width - 40f) / e.width, (height - 60f) / e.height);
        scale = Math.min(scale, 6f); // не раздуваем крошечные картинки чрезмерно
        int w = Math.max(1, Math.round(e.width * scale));
        int h = Math.max(1, Math.round(e.height * scale));
        int ix = (width - w) / 2;
        int iy = (height - h) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, e.currentTexture(), ix, iy,
                0f, 0f, w, h, e.width, e.height, e.width, e.height);
        Text hint = Text.translatable("pmchat.image.close");
        context.drawText(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, height - 16, 0xFFB8C6CE, false);
    }

    /** Папка обоев в конфиге мода. */
    static java.nio.file.Path wallpapersDir() {
        java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("pmchat-wallpapers");
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir;
    }

    /** Рисует обои фона чата (вписаны с заполнением) + затемнение для читаемости. */
    private void drawWallpaper(DrawContext context, int x0, int y0, int x1, int y1) {
        String wp = config.wallpaper;
        if (wp == null || wp.isBlank()) return;
        java.nio.file.Path file = wallpapersDir().resolve(wp);
        if (!java.nio.file.Files.exists(file)) return;

        PmImages.Entry e = PmImages.loadLocal(wp, file);
        if (e.state != PmImages.State.READY || e.currentTexture() == null || e.width <= 0) return;

        int aw = x1 - x0, ah = y1 - y0;
        // cover: масштаб по большей стороне, обрезаем лишнее через scissor
        float scale = Math.max((float) aw / e.width, (float) ah / e.height);
        int w = Math.round(e.width * scale);
        int h = Math.round(e.height * scale);
        int ix = x0 + (aw - w) / 2;
        int iy = y0 + (ah - h) / 2;

        context.enableScissor(x0, y0, x1, y1);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, e.currentTexture(), ix, iy,
                0f, 0f, w, h, e.width, e.height, e.width, e.height);
        // Затемнение под тему (светлая — светлее, тёмная — темнее)
        context.fill(x0, y0, x1, y1, config.theme == 1 ? 0x99FFFFFF : 0xB0000000);
        context.disableScissor();
    }

    /**
     * Рисует голову-скин игрока (если он онлайн и скин загружен),
     * иначе — цветной квадрат с первой буквой ника.
     */
    private void drawAvatar(DrawContext context, String name, int x, int y, int size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.network.PlayerListEntry entry =
                mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerListEntry(name) : null;
        if (entry != null && entry.getSkinTextures() != null) {
            try {
                net.minecraft.client.gui.PlayerSkinDrawer.draw(context, entry.getSkinTextures(), x, y, size);
                return;
            } catch (Throwable ignored) {
            }
        }
        // Фолбэк: цветной фон + буква
        int bg = nameColor(name);
        context.fill(x, y, x + size, y + size, (0xFF << 24) | (bg & 0x00FFFFFF));
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        context.drawText(textRenderer, letter,
                x + size / 2 - textRenderer.getWidth(letter) / 2, y + size / 2 - 4, 0xFF1A1A1A, false);
    }

    private String query() {
        return (searchField != null ? searchField.getText() : searchText).trim().toLowerCase(Locale.ROOT);
    }

    private void renderConversationList(DrawContext context, int mouseX, int mouseY) {
        rowRects.clear();
        String query = query();

        List<String> names = new ArrayList<>();
        for (String name : history.conversationNames()) {
            if (PmChatClient.isLocalChat(name)) continue; // §saved закреплён отдельно
            if (query.isEmpty() || history.matches(name, query)) {
                names.add(name);
            }
        }
        // Контакты — вверх списка (сохраняя порядок по свежести внутри групп)
        names.sort((a, b) -> {
            boolean ca = config.isContact(a), cb = config.isContact(b);
            if (ca != cb) return ca ? -1 : 1;
            return 0;
        });

        int top = py + 42;
        int bottom = py + PANEL_H - 24;
        int visible = (bottom - top) / ROW_H;
        int maxScroll = Math.max(0, names.size() - visible);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));

        int y = top;

        // Заголовок секции «Каналы» (общий чат, избранное, серверные каналы)
        context.drawText(textRenderer, Text.translatable("pmchat.section.channels").getString().toUpperCase(Locale.ROOT),
                px + 7, y + 1, SUBTLE, false);
        y += 10;

        // Закреплённый общий чат
        {
            boolean hovered = mouseX >= px && mouseX < px + LEFT_W && mouseY >= y && mouseY < y + ROW_H;
            boolean isSel = PmChatClient.GLOBAL.equals(selected);
            if (isSel) {
                context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_SELECTED);
            } else if (hovered) {
                context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_HOVER);
            }
            context.drawText(textRenderer, "◎", px + 7, y + 4, 0xFF6FBF8B, false);
            context.drawText(textRenderer,
                    trim(Text.translatable("pmchat.global").getString(), LEFT_W - 26), px + 18, y + 4, NAME_TEXT, false);
            List<PmMessage> global = PmChatClient.getGlobalChat();
            if (!global.isEmpty()) {
                PmMessage last = global.get(global.size() - 1);
                String preview = (last.sender != null ? last.sender + ": " : "")
                        + PmChatClient.previewOf(last.text != null ? last.text : "");
                context.drawText(textRenderer, trim(preview, LEFT_W - 14), px + 7, y + 14, PREVIEW_TEXT, false);
            }
            rowRects.add(new Object[]{px, y, LEFT_W, ROW_H, PmChatClient.GLOBAL});
            y += ROW_H;
        }

        // Закреплённое «Избранное» (личный чат с собой)
        {
            boolean hovered = mouseX >= px && mouseX < px + LEFT_W && mouseY >= y && mouseY < y + ROW_H;
            boolean isSel = PmChatClient.SAVED.equals(selected);
            if (isSel) context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_SELECTED);
            else if (hovered) context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_HOVER);
            context.drawText(textRenderer, "✦", px + 7, y + 4, 0xFFF0C34E, false);
            context.drawText(textRenderer, trim(Text.translatable("pmchat.saved").getString(), LEFT_W - 26),
                    px + 18, y + 4, NAME_TEXT, false);
            PmMessage last = history.lastMessage(PmChatClient.SAVED);
            if (last != null) {
                context.drawText(textRenderer, trim(PmChatClient.previewOf(last.text != null ? last.text : ""), LEFT_W - 14),
                        px + 7, y + 14, PREVIEW_TEXT, false);
            }
            rowRects.add(new Object[]{px, y, LEFT_W, ROW_H, PmChatClient.SAVED});
            y += ROW_H;
        }

        // Лента логов CoreProtect (6.3) — read-only, появляется при первом логе
        if (PmChatClient.coreProtectHasMessages() && y + ROW_H <= bottom + 2) {
            boolean hovered = mouseX >= px && mouseX < px + LEFT_W && mouseY >= y && mouseY < y + ROW_H;
            boolean isSel = PmChatClient.COREPROTECT.equals(selected);
            if (isSel) context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_SELECTED);
            else if (hovered) context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_HOVER);
            context.drawText(textRenderer, "▤", px + 7, y + 4, 0xFF9CC4DC, false);
            context.drawText(textRenderer, trim("CoreProtect", LEFT_W - 26), px + 18, y + 4, NAME_TEXT, false);
            List<PmMessage> cp = PmChatClient.getCoreProtectFeed();
            if (!cp.isEmpty()) {
                context.drawText(textRenderer, trim(cp.get(cp.size() - 1).text, LEFT_W - 14),
                        px + 7, y + 14, PREVIEW_TEXT, false);
            }
            rowRects.add(new Object[]{px, y, LEFT_W, ROW_H, PmChatClient.COREPROTECT});
            y += ROW_H;
        }

        // Каналы (клан/альянс/группа): вкладка появляется при первом сообщении
        for (com.pmchat.client.PmConfig.PmChannel channel : config.channels) {
            if (!PmChatClient.channelHasMessages(channel.id) || y + ROW_H > bottom + 2) continue;
            String tabId = PmChatClient.CHANNEL_PREFIX + channel.id;
            boolean hovered = mouseX >= px && mouseX < px + LEFT_W && mouseY >= y && mouseY < y + ROW_H;
            boolean isSel = tabId.equals(selected);
            if (isSel) {
                context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_SELECTED);
            } else if (hovered) {
                context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_HOVER);
            }
            context.drawText(textRenderer, "#", px + 7, y + 4, 0xFFF0C34E, false);

            int chUnread = PmChatClient.channelUnread(channel.id);
            int labelMax = LEFT_W - 26 - (chUnread > 0 ? 16 : 0);
            context.drawText(textRenderer, trim(channel.label, labelMax), px + 16, y + 4, NAME_TEXT, false);

            List<PmMessage> feed = PmChatClient.getChannelFeed(channel.id);
            if (!feed.isEmpty()) {
                PmMessage last = feed.get(feed.size() - 1);
                String preview = (last.sender != null ? last.sender + ": " : "")
                        + PmChatClient.previewOf(last.text != null ? last.text : "");
                context.drawText(textRenderer, trim(preview, LEFT_W - 14), px + 7, y + 14, PREVIEW_TEXT, false);
            }
            if (chUnread > 0) {
                String badge = chUnread > 9 ? "9+" : String.valueOf(chUnread);
                int bw2 = textRenderer.getWidth(badge) + 6;
                context.fill(px + LEFT_W - 5 - bw2, y + 3, px + LEFT_W - 5, y + 13, BADGE_BG);
                context.drawText(textRenderer, badge, px + LEFT_W - 5 - bw2 + 3, y + 4, 0xFFFFFFFF, false);
            }
            rowRects.add(new Object[]{px, y, LEFT_W, ROW_H, tabId});
            y += ROW_H;
        }

        // Группы (6.9): пользовательские беседы с несколькими игроками
        groupNewRect = null;
        for (com.pmchat.client.PmConfig.PmGroup g : config.groups) {
            if (y + ROW_H > bottom + 2) break;
            String tabId = PmChatClient.GROUP_PREFIX + g.id;
            boolean hovered = mouseX >= px && mouseX < px + LEFT_W && mouseY >= y && mouseY < y + ROW_H;
            boolean isSel = tabId.equals(selected);
            if (isSel) {
                context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_SELECTED);
            } else if (hovered) {
                context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_HOVER);
            }
            context.drawText(textRenderer, "▣", px + 7, y + 4, 0xFF6FBF8B, false);
            int gUnread = PmChatClient.groupUnread(g.id);
            int labelMax = LEFT_W - 26 - (gUnread > 0 ? 16 : 0);
            context.drawText(textRenderer, trim(g.name, labelMax), px + 16, y + 4, NAME_TEXT, false);
            List<PmMessage> feed = PmChatClient.getGroupFeed(g.id);
            if (!feed.isEmpty()) {
                PmMessage last = feed.get(feed.size() - 1);
                String preview = (last.sender != null ? last.sender + ": " : "")
                        + PmChatClient.previewOf(last.text != null ? last.text : "");
                context.drawText(textRenderer, trim(preview, LEFT_W - 14), px + 7, y + 14, PREVIEW_TEXT, false);
            } else {
                context.drawText(textRenderer, trim(String.join(", ", g.members), LEFT_W - 14),
                        px + 7, y + 14, PREVIEW_TEXT, false);
            }
            if (gUnread > 0) {
                String badge = gUnread > 9 ? "9+" : String.valueOf(gUnread);
                int bw2 = textRenderer.getWidth(badge) + 6;
                context.fill(px + LEFT_W - 5 - bw2, y + 3, px + LEFT_W - 5, y + 13, BADGE_BG);
                context.drawText(textRenderer, badge, px + LEFT_W - 5 - bw2 + 3, y + 4, 0xFFFFFFFF, false);
            }
            rowRects.add(new Object[]{px, y, LEFT_W, ROW_H, tabId});
            y += ROW_H;
        }
        // Строка «＋ Новая группа» (только когда не идёт поиск)
        if (query.isEmpty() && y + 12 <= bottom) {
            boolean hovered = mouseX >= px && mouseX < px + LEFT_W && mouseY >= y && mouseY < y + 12;
            if (hovered) context.fill(px + 2, y, px + LEFT_W - 1, y + 11, ROW_HOVER);
            context.drawText(textRenderer, "＋ " + Text.translatable("pmchat.group.new").getString(),
                    px + 7, y + 2, hovered ? NAME_TEXT : SUBTLE, false);
            groupNewRect = new int[]{px, y, LEFT_W, 12};
            y += 14;
        }

        // Заголовок секции «Личные» (переписки с игроками)
        if (!names.isEmpty() && y + 10 <= bottom) {
            context.drawText(textRenderer, Text.translatable("pmchat.section.chats").getString().toUpperCase(Locale.ROOT),
                    px + 7, y + 1, SUBTLE, false);
            y += 10;
        }

        for (int i = listScroll; i < names.size() && y + ROW_H <= bottom + 2; i++) {
            String name = names.get(i);
            boolean hovered = mouseX >= px && mouseX < px + LEFT_W && mouseY >= y && mouseY < y + ROW_H;
            boolean isSelected = name.equalsIgnoreCase(selected);

            if (isSelected) {
                context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_SELECTED);
            } else if (hovered) {
                context.fill(px + 2, y, px + LEFT_W - 1, y + ROW_H - 1, ROW_HOVER);
            }

            int unread = history.unreadCount(name);
            // Аватарка-голова слева
            drawAvatar(context, name, px + 4, y + 4, 16);
            // Индикатор мода: зелёная точка в углу аватарки
            boolean hasMod = config.isModUser(name);
            context.fill(px + 16, y + 14, px + 20, y + 18, hasMod ? 0xFF6FBF8B : SUBTLE);
            boolean contact = config.isContact(name);
            int nameX = px + 23;
            if (contact) {
                context.drawText(textRenderer, "★", nameX, y + 3, 0xFFF0C34E, false);
                nameX += 8;
            }
            int nameMax = LEFT_W - (nameX - px) - 7 - (unread > 0 ? 16 : 0);
            context.drawText(textRenderer, trim(name, nameMax), nameX, y + 3, NAME_TEXT, false);

            PmMessage last = history.lastMessage(name);
            if (last != null) {
                String body = last.money > 0
                        ? "$ " + groupDigits(last.money)
                        : PmChatClient.previewOf(last.text != null ? last.text : "");
                String preview = (last.out ? "→ " : "") + body;
                context.drawText(textRenderer, trim(preview, LEFT_W - 30), px + 23, y + 14, PREVIEW_TEXT, false);
            }

            if (unread > 0) {
                String badge = unread > 9 ? "9+" : String.valueOf(unread);
                int bw = textRenderer.getWidth(badge) + 6;
                context.fill(px + LEFT_W - 5 - bw, y + 3, px + LEFT_W - 5, y + 13, BADGE_BG);
                context.drawText(textRenderer, badge, px + LEFT_W - 5 - bw + 3, y + 4, 0xFFFFFFFF, false);
            }

            rowRects.add(new Object[]{px, y, LEFT_W, ROW_H, name});
            y += ROW_H;
        }

        if (names.isEmpty()) {
            Text empty = Text.translatable(history.conversationNames().isEmpty()
                    ? "pmchat.empty.list" : "pmchat.notfound");
            context.drawText(textRenderer, empty, px + 8, top + 8, SUBTLE, false);
        }
    }

    private void renderChat(DrawContext context, int mouseX, int mouseY) {
        boolean isGlobal = isFeedTab();
        // Шапка: имя собеседника + «печатает…»
        String header;
        if (PmChatClient.GLOBAL.equals(selected)) {
            header = Text.translatable("pmchat.global").getString();
        } else if (PmChatClient.COREPROTECT.equals(selected)) {
            header = "▤ CoreProtect";
        } else if (PmChatClient.SAVED.equals(selected)) {
            header = "✦ " + Text.translatable("pmchat.saved").getString();
        } else if (channelId() != null) {
            com.pmchat.client.PmConfig.PmChannel channel = PmChatClient.channelById(channelId());
            header = "# " + (channel != null ? channel.label : channelId());
        } else if (isGroupTab()) {
            com.pmchat.client.PmConfig.PmGroup g = config.findGroup(groupId());
            header = "▣ " + (g != null ? g.name : Text.translatable("pmchat.group").getString());
        } else {
            header = trim(selected, PANEL_W - LEFT_W - 132);
        }
        boolean localChat = PmChatClient.isLocalChat(selected);
        int headerX = px + LEFT_W + 8;
        if (!isGlobal && !localChat) {
            // Индикатор мода собеседника в шапке
            boolean hasMod = config.isModUser(selected);
            context.drawText(textRenderer, "●", headerX, py + 8, hasMod ? 0xFF6FBF8B : SUBTLE, false);
            headerX += 9;
        }
        // NEW (6.10): замочек в шапке — секретный чат активен
        if (!isGlobal && !localChat && PmChatClient.isSecretActive(selected)) {
            PmIcons.draw(context, PmIcons.LOCK, headerX, py + 3, 9, 9, 0xFF8FD8A8);
            headerX += 11;
        }
        context.drawText(textRenderer, header, headerX, py + 8, TITLE, false);
        if (!isGlobal && !localChat && PmChatClient.isTyping(selected)) {
            int dots = (int) ((System.currentTimeMillis() / 350) % 4);
            String typing = Text.translatable("pmchat.typing").getString() + ".".repeat(dots);
            context.drawText(textRenderer, typing,
                    headerX + 4 + textRenderer.getWidth(header), py + 8, 0xFF6FBF8B, false);
        }
        // Состав группы с пометкой участников, у кого стоит мод (зелёная точка)
        if (isGroupTab()) {
            com.pmchat.client.PmConfig.PmGroup g = config.findGroup(groupId());
            if (g != null) {
                int mx = headerX + textRenderer.getWidth(header) + 10;
                int limit = px + PANEL_W - 34;
                for (String member : g.members) {
                    boolean hasMod = config.isModUser(member);
                    int w = textRenderer.getWidth(member) + 14;
                    if (mx + w > limit) {
                        context.drawText(textRenderer, "…", mx, py + 8, SUBTLE, false);
                        break;
                    }
                    context.drawText(textRenderer, "●", mx, py + 8, hasMod ? 0xFF6FBF8B : SUBTLE, false);
                    context.drawText(textRenderer, member, mx + 8, py + 8, PREVIEW_TEXT, false);
                    mx += w;
                }
            }
        }
        context.fill(px + LEFT_W + 1, py + 22, px + PANEL_W - 2, py + 23, DIVIDER);

        int areaTop = py + 26;
        int areaBottom = py + PANEL_H - 30;

        // Закреплённые сообщения — полоска сверху (несколько, как в Telegram)
        pinBarRect = null;
        pinListBtnRect = null;
        pinUnpinRect = null;
        if (!isGlobal && selected != null) {
            List<String> pins = config.pinnedList(selected);
            if (!pins.isEmpty()) {
                pinCursor = Math.floorMod(pinCursor, pins.size());
                String pinHash = pins.get(pins.size() - 1 - pinCursor); // свежие сверху
                PmMessage pinned = history.findByHash(selected, pinHash);
                int by = areaTop;
                context.fill(px + LEFT_W + 2, by, px + PANEL_W - 2, by + 12, 0x33F0C34E);
                context.fill(px + LEFT_W + 2, by, px + LEFT_W + 3, by + 12, 0xFFF0C34E);
                String counter = pins.size() > 1 ? "[" + (pinCursor + 1) + "/" + pins.size() + "] " : "";
                String body = pinned != null ? PmChatClient.previewOf(pinned.text != null ? pinned.text : "") : "…";
                String label = "⚑ " + counter + body;
                int rightPad = pins.size() > 1 ? 34 : 24;
                context.drawText(textRenderer, trim(label, PANEL_W - LEFT_W - rightPad),
                        px + LEFT_W + 7, by + 2, 0xFFF0C34E, false);
                // Кнопка списка (☰) при нескольких закрепах + × открепить текущий
                if (pins.size() > 1) {
                    context.drawText(textRenderer, "☰", px + PANEL_W - 22, by + 2, 0xFFF0C34E, false);
                    pinListBtnRect = new int[]{px + PANEL_W - 25, by, 14, 12};
                }
                context.drawText(textRenderer, "×", px + PANEL_W - 10, by + 2, 0xFFE07A6A, false);
                pinUnpinRect = new int[]{px + PANEL_W - 13, by, 12, 12};
                pinBarRect = new int[]{px + LEFT_W + 2, by, PANEL_W - LEFT_W - 4, 12};
                areaTop += 14;
            }
        }

        drawWallpaper(context, px + LEFT_W + 1, areaTop, px + PANEL_W - 1, areaBottom);

        if (imageMode) {
            renderImagePicker(context, mouseX, mouseY, areaTop, areaBottom);
            return;
        }

        if (mediaMode) {
            renderMediaPicker(context, mouseX, mouseY, areaTop, areaBottom);
            return;
        }

        if (pollMode) {
            context.fill(px + LEFT_W + 1, areaTop, px + PANEL_W - 1, py + PANEL_H - 2, PANEL_BG);
            context.drawText(textRenderer, Text.translatable("pmchat.poll.title"),
                    px + LEFT_W + 8, areaTop + 2, 0xFF9CC4DC, false);
            return;
        }

        if (stickerMode) {
            renderStickerPanel(context, mouseX, mouseY, areaTop, py + PANEL_H - 4);
            return;
        }

        if (uploading) {
            context.drawText(textRenderer, Text.translatable("pmchat.image.uploading"),
                    px + LEFT_W + 8, areaBottom - 8, 0xFFF0C34E, false);
        }

        // Статус распознавания речи над полем ввода
        switch (com.pmchat.client.PmStt.state) {
            case DOWNLOADING -> context.drawText(textRenderer,
                    Text.translatable("pmchat.stt.downloading").getString()
                            + " " + com.pmchat.client.PmStt.progressPct + "%",
                    px + LEFT_W + 8, areaBottom - 8, 0xFFF0C34E, false);
            case UNPACKING, LOADING -> context.drawText(textRenderer,
                    Text.translatable("pmchat.stt.loading"),
                    px + LEFT_W + 8, areaBottom - 8, 0xFFF0C34E, false);
            case LISTENING -> {
                boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
                String partial = com.pmchat.client.PmStt.partialText;
                String live = (blink ? "● " : "  ")
                        + (partial.isBlank() ? Text.translatable("pmchat.stt.listen").getString() : partial);
                context.drawText(textRenderer, trim(live, PANEL_W - LEFT_W - 20),
                        px + LEFT_W + 8, areaBottom - 8, 0xFFE07A6A, false);
            }
            case ERROR -> context.drawText(textRenderer,
                    trim(Text.translatable("pmchat.stt.error").getString()
                            + ": " + com.pmchat.client.PmStt.error, PANEL_W - LEFT_W - 20),
                    px + LEFT_W + 8, areaBottom - 8, 0xFFE07A6A, false);
            default -> { }
        }
        String query = query();
        long now = System.currentTimeMillis();

        List<PmMessage> all;
        if (PmChatClient.GLOBAL.equals(selected)) {
            all = new ArrayList<>(PmChatClient.getGlobalChat());
        } else if (PmChatClient.COREPROTECT.equals(selected)) {
            all = new ArrayList<>(PmChatClient.getCoreProtectFeed());
        } else if (channelId() != null) {
            all = new ArrayList<>(PmChatClient.getChannelFeed(channelId()));
        } else if (isGroupTab()) {
            all = new ArrayList<>(PmChatClient.getGroupFeed(groupId()));
        } else {
            all = history.messages(selected);
        }
        List<PmMessage> shown = new ArrayList<>();
        for (PmMessage m : all) {
            if (query.isEmpty()
                    || (m.text != null && m.text.toLowerCase(Locale.ROOT).contains(query))) {
                shown.add(m);
            }
        }

        if (shown.isEmpty()) {
            Text empty = Text.translatable(all.isEmpty() ? "pmchat.empty.messages" : "pmchat.notfound");
            int hx = px + LEFT_W + (PANEL_W - LEFT_W - textRenderer.getWidth(empty)) / 2;
            context.drawText(textRenderer, empty, hx, (areaTop + areaBottom) / 2, SUBTLE, false);
            return;
        }

        // Пузыри снизу вверх (со scissor — ничего не вылезает за область чата)
        bubbleRects.clear();
        spoilerRects.clear();
        warnBtnRects.clear();
        pinOffsets.clear();
        pollOptRects.clear();
        context.enableScissor(px + LEFT_W + 1, areaTop, px + PANEL_W, areaBottom + 2);
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd.MM.yyyy");
        int y = areaBottom + msgScroll;
        int contentH = 0;
        for (int i = shown.size() - 1; i >= 0; i--) {
            PmMessage msg = shown.get(i);
            String[] imgRef = imageIdOf(msg);
            PmImages.Entry img = imgRef != null ? PmImages.get(imgRef[0], imgRef[1]) : null;
            String[] voice = voiceOf(msg);
            String[] vid = vidOf(msg);
            PmImages.Entry vent = vid != null ? PmImages.get(vid[0], vid[1]) : null;
            boolean vidReady = vent != null && vent.state == PmImages.State.READY
                    && vent.currentTexture() != null && vent.width > 0;

            List<String> lines;
            int textW;
            int bh;
            if (msg.isPoll()) {
                lines = List.of();
                textW = BUBBLE_MAX_TEXT_W;
                bh = 12 + msg.pollOptions.size() * 13 + 10;
            } else if (img != null) {
                lines = List.of();
                int[] size = imageSize(img);
                textW = size[0];
                bh = size[1] + 6;
            } else if (voice != null) {
                lines = List.of();
                textW = 74;
                bh = 18;
            } else if (vid != null) {
                lines = List.of();
                if (vidReady) {
                    int[] size = imageSize(vent);
                    textW = size[0];
                    bh = size[1] + 6;
                } else {
                    textW = Math.min(BUBBLE_MAX_TEXT_W, textRenderer.getWidth("▶ " + vid[1]) + 8);
                    bh = 18;
                }
            } else if (msg.money > 0) {
                lines = List.of();
                textW = textRenderer.getWidth("$ " + groupDigits(msg.money));
                bh = 16;
            } else {
                float ts = textScale();
                lines = wrapText(msg.text != null ? msg.text : "",
                        Math.max(24, (int) (BUBBLE_MAX_TEXT_W / ts)));
                textW = Math.round(lines.stream().mapToInt(textRenderer::getWidth).max().orElse(10) * ts);
                bh = lines.size() * lineH() + 7;
            }
            // Галочки прочтения у исходящих (в общем чате не показываем)
            if (msg.out && !isGlobal) textW += 12;
            // Строка цитаты сверху пузыря
            String quoted = isGlobal ? null : quotedTextOf(msg);
            if (quoted != null) {
                bh += 11;
                textW = Math.max(textW, Math.min(BUBBLE_MAX_TEXT_W, textRenderer.getWidth(quoted) + 8));
            }
            // Шапка «переслано от X»
            String fwdLabel = msg.forwardFrom != null
                    ? "⤶ " + Text.translatable("pmchat.fwd.from").getString() + " " + msg.forwardFrom : null;
            if (fwdLabel != null) {
                bh += 10;
                textW = Math.max(textW, Math.min(BUBBLE_MAX_TEXT_W, textRenderer.getWidth(fwdLabel) + 4));
            }
            // Автор в общем чате
            String senderName = isGlobal && !msg.out && msg.sender != null ? msg.sender : null;
            boolean senderHasMod = senderName != null && config.isModUser(senderName);
            if (senderName != null) {
                bh += 10;
                textW = Math.max(textW, Math.min(BUBBLE_MAX_TEXT_W,
                        textRenderer.getWidth(senderName) + 10 + (senderHasMod ? 9 : 0)));
            }

            int bw = textW + 12;
            // Реакции: моя + собеседника, но одинаковые символы не дублируем
            String reacts = "";
            if (msg.reactOther != null) reacts += msg.reactOther;
            if (msg.reactMine != null && !msg.reactMine.equals(msg.reactOther)) reacts += msg.reactMine;
            int gap = reacts.isEmpty() ? 4 : 11;
            y -= bh + gap;
            contentH += bh + gap;

            // Запоминаем позиции всех закреплённых сообщений для перехода
            if (!isGlobal && selected != null && msg.text != null) {
                String h = PmHistory.msgHash(msg.text);
                if (config.isPinned(selected, h)) {
                    pinOffsets.put(h, contentH);
                }
                if (pendingJumpHash != null && h.equals(pendingJumpHash)) {
                    pendingJumpOffset = contentH;
                }
            }

            if (y + bh < areaTop || y > areaBottom) continue;

            // Анимация появления
            float alpha = 1f;
            int dx = 0, dy = 0;
            long age = now - msg.clientAddedAt;
            if (msg.clientAddedAt > 0 && age < 260) {
                float t = age / 260f;
                float ease = 1 - (1 - t) * (1 - t);
                alpha = 0.25f + 0.75f * t;
                if (msg.out) dy = Math.round((1 - ease) * 16);
                else dx = -Math.round((1 - ease) * 14);
            }

            int bx = msg.out ? px + PANEL_W - 8 - bw : px + LEFT_W + 8;
            int quoteShift = (quoted != null ? 11 : 0) + (senderName != null ? 10 : 0)
                    + (fwdLabel != null ? 10 : 0);
            int bg = msg.money > 0 ? MONEY_BG : (msg.out ? OUT_BG : IN_BG);
            // NEW (6.10): секретные сообщения — лёгкий зелёный оттенок, как в Telegram
            if (msg.secret) bg = tintTowards(bg, 0xFF1E4A32, 0.35f);

            // Фон пузыря
            context.fill(bx + dx + 1, y + dy, bx + dx + bw - 1, y + dy + bh, applyAlpha(bg, alpha));
            context.fill(bx + dx, y + dy + 1, bx + dx + bw, y + dy + bh - 1, applyAlpha(bg, alpha));

            // Подсветка упоминания (жёлтая рамка) и вспышка перехода к закрепу
            boolean mentioned = !msg.out && PmChatClient.mentionsMe(msg.text, senderName);
            boolean flash = flashHash != null && msg.text != null && PmHistory.msgHash(msg.text).equals(flashHash)
                    && System.currentTimeMillis() < flashUntil;
            if (mentioned || flash) {
                int mc = flash ? 0xFFF0C34E : 0xFFE0B040;
                context.drawStrokedRectangle(bx + dx, y + dy, bw, bh, applyAlpha(mc, alpha));
            }

            // Шапка пересылки
            if (fwdLabel != null) {
                int fc = msg.out ? 0xFFA8D8BC : 0xFF3A7AB0;
                context.drawText(textRenderer, trim(fwdLabel, bw - 8), bx + dx + 5, y + dy + 3,
                        applyAlpha(fc, alpha), false);
            }

            // Автор (общий чат) — аватарка-голова + ник цветом; точка если есть мод
            if (senderName != null) {
                int nx = bx + dx + 6;
                if (alpha > 0.95f) { // голову без прозрачности, чтобы не мигала при появлении
                    String bare = senderName.contains(" [") ? senderName.substring(0, senderName.indexOf(" [")) : senderName;
                    drawAvatar(context, bare, nx, y + dy + 2, 8);
                    nx += 10;
                }
                if (senderHasMod) {
                    context.drawText(textRenderer, "●", nx, y + dy + 3, applyAlpha(0xFF6FBF8B, alpha), false);
                    nx += 9;
                }
                context.drawText(textRenderer, trim(senderName, bx + dx + bw - 4 - nx), nx, y + dy + 3,
                        applyAlpha(nameColor(senderName), alpha), false);
                // 6.8: кнопка ⚠ «предупредить» в общем чате/каналах (staff-функции).
                // Показываем только при наведении на пузырь — иначе значки засоряют
                // общий чат.
                if (config.staffFeatures && !msg.out) {
                    int wx2 = bx + dx + bw + 3;
                    boolean rowHover = mouseX >= bx + dx && mouseX <= wx2 + 12
                            && mouseY >= y + dy && mouseY <= y + dy + bh;
                    if (rowHover) {
                        String bare = senderName.contains(" [") ? senderName.substring(0, senderName.indexOf(" [")) : senderName;
                        context.drawText(textRenderer, "⚠", wx2, y + dy + 3, applyAlpha(0xFFE0B040, alpha), false);
                        warnBtnRects.add(new Object[]{wx2 - 1, y + dy + 1, 10, 11, bare});
                    }
                }
            }

            // Цитата (ниже шапки пересылки, если она есть)
            if (quoted != null) {
                int qy = y + dy + 3 + (fwdLabel != null ? 10 : 0);
                int qc = msg.out ? 0xFFA8D8BC : 0xFF9A9A9A;
                context.fill(bx + dx + 4, qy, bx + dx + 5, qy + 8, applyAlpha(qc, alpha));
                context.drawText(textRenderer, trim(quoted, bw - 14), bx + dx + 8, qy,
                        applyAlpha(qc, alpha), false);
            }

            // Содержимое
            if (msg.isPoll()) {
                int fg = msg.out ? OUT_TEXT : IN_TEXT;
                int pyy = y + dy + quoteShift + 3;
                context.drawText(textRenderer, "▤ " + trim(msg.pollQuestion, bw - 16), bx + dx + 6, pyy, applyAlpha(fg, alpha), false);
                pyy += 12;
                int total = msg.pollOptions.size();
                int votesAll = 0;
                for (int oi = 0; oi < total; oi++) votesAll += msg.pollCount(oi);
                for (int oi = 0; oi < total; oi++) {
                    int cnt = msg.pollCount(oi);
                    boolean mine = msg.pollMyVotes != null && msg.pollMyVotes.contains(oi);
                    int barMax = bw - 12;
                    int bar = votesAll > 0 ? Math.max(0, cnt * barMax / Math.max(1, votesAll)) : 0;
                    int oy = pyy + oi * 13;
                    // Фон-полоса результата
                    context.fill(bx + dx + 6, oy, bx + dx + 6 + bar, oy + 11, applyAlpha(mine ? 0xFF4C8A66 : 0xFF2A4A5C, alpha * 0.5f));
                    context.drawText(textRenderer, (mine ? "◉ " : "○ ") + trim(msg.pollOptions.get(oi), bw - 40),
                            bx + dx + 8, oy + 2, applyAlpha(fg, alpha), false);
                    String c = String.valueOf(cnt);
                    context.drawText(textRenderer, c, bx + dx + bw - 6 - textRenderer.getWidth(c), oy + 2, applyAlpha(fg, alpha), false);
                    pollOptRects.add(new Object[]{bx, oy, bw, 11, msg, oi});
                }
            } else if (img != null) {
                boolean imgSpoiler = com.pmchat.client.PmWire.isSpoiler(imgRef) && !msg.spoilerRevealed;
                if (img.state == PmImages.State.READY && img.currentTexture() != null) {
                    int[] size = imageSize(img);
                    int ix = bx + dx + 6, iy = y + dy + 3 + quoteShift;
                    if (imgSpoiler) {
                        drawSpoilerCover(context, ix, iy, size[0], size[1], alpha);
                        spoilerRects.add(new Object[]{ix, iy, size[0], size[1], msg});
                    } else {
                        context.drawTexture(RenderPipelines.GUI_TEXTURED, img.currentTexture(), ix, iy,
                                0f, 0f, size[0], size[1], img.width, img.height, img.width, img.height);
                    }
                } else {
                    Text label = img.state == PmImages.State.FAILED
                            ? Text.translatable("pmchat.image.openweb")
                            : Text.translatable("pmchat.image.uploading");
                    context.drawText(textRenderer, label, bx + dx + 6, y + dy + quoteShift + 4,
                            applyAlpha(msg.out ? OUT_TEXT : IN_TEXT, alpha), false);
                }
            } else if (voice != null) {
                drawVoiceContent(context, bx + dx, y + dy + quoteShift, bw, msg, voice, alpha);
            } else if (vid != null) {
                boolean vidSpoiler = com.pmchat.client.PmWire.isSpoiler(vid) && !msg.spoilerRevealed;
                if (vidReady) {
                    int[] size = imageSize(vent);
                    int ix = bx + dx + 6, iy = y + dy + 3 + quoteShift;
                    if (vidSpoiler) {
                        drawSpoilerCover(context, ix, iy, size[0], size[1], alpha);
                        spoilerRects.add(new Object[]{ix, iy, size[0], size[1], msg});
                    } else {
                        context.drawTexture(RenderPipelines.GUI_TEXTURED, vent.currentTexture(), ix, iy,
                                0f, 0f, size[0], size[1], vent.width, vent.height, vent.width, vent.height);
                        // Значок ▶ по центру — намёк, что клик откроет со звуком
                        context.drawText(textRenderer, "▶", ix + size[0] / 2 - 3, iy + size[1] / 2 - 4,
                                applyAlpha(0xFFFFFFFF, alpha), true);
                    }
                } else {
                    int fg = msg.out ? OUT_TEXT : IN_TEXT;
                    Text lbl = vent != null && vent.state == PmImages.State.FAILED
                            ? Text.literal("▶ " + trim(vid[1], bw - 16))
                            : Text.translatable("pmchat.video.decoding");
                    context.drawText(textRenderer, lbl,
                            bx + dx + 6, y + dy + quoteShift + 5, applyAlpha(fg, alpha), false);
                }
            } else if (msg.money > 0) {
                context.drawText(textRenderer, "$ " + groupDigits(msg.money), bx + dx + 6, y + dy + quoteShift + 4,
                        applyAlpha(MONEY_TEXT, alpha), false);
            } else {
                float ts = textScale();
                int ty = y + dy + quoteShift + 4;
                int inLink = IN_TEXT == 0xFF222222 ? 0xFF2E6FB0 : 0xFF8FC8F0;
                int[] mtc = com.pmchat.client.PmPalettes.MSG_TEXT;
                int chosen = mtc[Math.floorMod(config.msgTextColor, mtc.length)];
                for (String line : lines) {
                    boolean isLink = line.contains("http") || line.contains("://");
                    int fg = isLink ? (msg.out ? 0xFFA8E0FF : inLink)
                            : (chosen != 0 ? chosen : (msg.out ? OUT_TEXT : IN_TEXT));
                    Matrix3x2fStack m2 = context.getMatrices();
                    m2.pushMatrix();
                    m2.translate(bx + dx + 6, ty);
                    m2.scale(ts, ts);
                    context.drawText(textRenderer, line, 0, 0, applyAlpha(fg, alpha), false);
                    m2.popMatrix();
                    ty += lineH();
                }
            }

            // Галочки: ✓ отправлено, ✓✓ прочитано
            if (msg.out && !isGlobal) {
                String ticks = msg.read ? "✔✔" : "✔";
                int tc = msg.read ? 0xFFA8E8C0 : 0xFF7FA890;
                context.drawText(textRenderer, ticks,
                        bx + dx + bw - 6 - textRenderer.getWidth(ticks), y + dy + bh - 10,
                        applyAlpha(tc, alpha), false);
            }

            // Чип реакций — внизу пузыря, как в Telegram (цветные символы)
            if (!reacts.isEmpty()) {
                int cw = textRenderer.getWidth(reacts) + 8;
                int cy = y + dy + bh - 3;
                int cx = msg.out ? bx + dx + 2 : bx + dx + bw - cw - 2;
                context.fill(cx + 1, cy, cx + cw - 1, cy + 12, CHIP_BG);
                context.fill(cx, cy + 1, cx + cw, cy + 11, CHIP_BG);
                context.drawStrokedRectangle(cx, cy, cw, 12, DIVIDER);
                int rxx = cx + 4;
                for (int ci = 0; ci < reacts.length(); ci++) {
                    String sym = String.valueOf(reacts.charAt(ci));
                    context.drawText(textRenderer, sym, rxx, cy + 2,
                            applyAlpha(com.pmchat.client.PmWire.reactionColor(sym), alpha), false);
                    rxx += textRenderer.getWidth(sym);
                }
            }

            // Время сообщения — сбоку от пузыря (с меткой «ред.», если изменено)
            if (msg.time > 0) {
                // NEW (6.10): у секретных сообщений с таймером — обратный отсчёт до самоуничтожения
                String destructPart = "";
                if (msg.secret && msg.destructAt > 0) {
                    long left = Math.max(0, (msg.destructAt - now) / 1000);
                    destructPart = " · " + left + Text.translatable("pmchat.secret.ttl.s").getString();
                }
                String time = (msg.edited ? Text.translatable("pmchat.edited.mark").getString() + " " : "")
                        + timeFmt.format(new Date(msg.time)) + destructPart;
                int tw = textRenderer.getWidth(time);
                int tx = msg.out ? bx - 4 - tw : bx + bw + 4;
                int timeColor = msg.secret && msg.destructAt > 0 ? 0xFFCB8A8A : SUBTLE;
                context.drawText(textRenderer, time, tx + dx, y + dy + bh - 10, timeColor, false);
            }
            // NEW (6.10): замочек на секретном сообщении, у самого пузыря
            if (msg.secret) {
                int lx = msg.out ? bx + dx + bw - 10 : bx + dx + 1;
                PmIcons.draw(context, PmIcons.LOCK, lx, y + dy + 1, 9, 9, applyAlpha(0xFF8FD8A8, alpha));
            }

            // 5.5: значок закрепа на закреплённом пузыре (как в Telegram)
            if (!isGlobal && selected != null && msg.text != null
                    && config.isPinned(selected, PmHistory.msgHash(msg.text))) {
                int px2 = msg.out ? bx + dx - 9 : bx + dx + bw + 1;
                context.drawText(textRenderer, "⚑", px2, y + dy + 1, applyAlpha(0xFFF0C34E, alpha), false);
            }

            bubbleRects.add(new Object[]{bx, y, bw, bh, msg});

            // Разделитель даты над первым сообщением дня
            boolean daySep = i == 0 || !sameDay(shown.get(i - 1).time, msg.time);
            if (daySep && msg.time > 0) {
                y -= 14;
                contentH += 14;
                if (y + 10 >= areaTop && y <= areaBottom) {
                    String date = dateFmt.format(new Date(msg.time));
                    int dw = textRenderer.getWidth(date);
                    int cx = px + LEFT_W + (PANEL_W - LEFT_W) / 2;
                    context.fill(px + LEFT_W + 10, y + 6, cx - dw / 2 - 5, y + 7, SEP_LINE);
                    context.fill(cx + dw / 2 + 5, y + 6, px + PANEL_W - 10, y + 7, SEP_LINE);
                    context.drawText(textRenderer, date, cx - dw / 2, y + 2, SUBTLE, false);
                }
            }
        }
        context.disableScissor();
        msgMaxScroll = Math.max(0, contentH - (areaBottom - areaTop));

        // Отложенный переход к сообщению (напр. из глобального поиска)
        if (pendingJumpHash != null) {
            if (pendingJumpOffset > 0) {
                int areaH = areaBottom - areaTop;
                msgScroll = Math.max(0, Math.min(msgMaxScroll, pendingJumpOffset - areaH / 2));
            }
            flashHash = pendingJumpHash;
            flashUntil = System.currentTimeMillis() + 1400;
            pendingJumpHash = null;
            pendingJumpOffset = -1;
        }

        renderReplyBar(context);
        renderEditBar(context);
        renderEmojiGrid(context, mouseX, mouseY);
        renderRecordingBar(context, areaBottom);
    }

    static String[] voiceOf(PmMessage msg) {
        if (msg.text == null || msg.money > 0) return null;
        return com.pmchat.client.PmWire.parseVoice(msg.text);
    }

    static String[] vidOf(PmMessage msg) {
        if (msg.text == null || msg.money > 0) return null;
        return com.pmchat.client.PmWire.parseVid(msg.text);
    }

    private String quotedTextOf(PmMessage msg) {
        if (msg.replyTo == null || selected == null) return null;
        // Цитата фрагмента — показываем именно выбранный кусок
        if (msg.replyFragment != null && !msg.replyFragment.isBlank()) {
            return "«" + PmChatClient.previewOf(msg.replyFragment) + "»";
        }
        PmMessage quoted = history.findByHash(selected, msg.replyTo);
        if (quoted == null) return "…";
        return PmChatClient.previewOf(quoted.text != null ? quoted.text : "");
    }

    private void clearReply() {
        replyTarget = null;
        replyFragText = null;
        replyFragStart = -1;
        replyFragLen = 0;
    }

    /** Открывает оверлей выбора фрагмента: режем сообщение на слова. */
    private void openFragSelector(PmMessage msg) {
        if (msg == null || msg.text == null || msg.text.isBlank()) return;
        fragMsg = msg;
        fragWordFrom = -1;
        fragWordTo = -1;
        fragWords.clear();
        fragSpans.clear();
        Matcher m = Pattern.compile("\\S+").matcher(msg.text);
        while (m.find()) {
            fragWords.add(m.group());
            fragSpans.add(new int[]{m.start(), m.end()});
        }
    }

    /** Подтверждает выбор: формирует фрагмент-цитату и делает его целью ответа. */
    private void confirmFrag() {
        if (fragMsg == null || fragWordFrom < 0) return;
        int lo = Math.min(fragWordFrom, fragWordTo);
        int hi = Math.max(fragWordFrom, fragWordTo);
        int cs = fragSpans.get(lo)[0];
        int ce = fragSpans.get(hi)[1];
        replyTarget = fragMsg;
        replyFragStart = cs;
        replyFragLen = ce - cs;
        replyFragText = fragMsg.text.substring(cs, ce);
        fragMsg = null;
        rebuild();
    }

    /** Оверлей выбора фрагмента: слова сообщения, клик — начало, ещё клик — конец. */
    private void renderFragSelector(DrawContext context, int mouseX, int mouseY) {
        fragWordRects.clear();
        fragOkRect = null;
        fragCancelRect = null;
        if (fragMsg == null) return;

        int x0 = px + LEFT_W + 6, x1 = px + PANEL_W - 6;
        int y0 = py + 22, y1 = py + PANEL_H - 6;
        context.createNewRootLayer();
        context.fill(px, py, px + PANEL_W, py + PANEL_H, 0x88000000);
        context.fill(x0 + 1, y0, x1 - 1, y1, LEFT_BG);
        context.fill(x0, y0 + 1, x1, y1 - 1, LEFT_BG);
        context.drawStrokedRectangle(x0, y0, x1 - x0, y1 - y0, DIVIDER);

        context.drawText(textRenderer, Text.translatable("pmchat.frag.title"), x0 + 6, y0 + 5, 0xFF9CC4DC, false);
        context.drawText(textRenderer, Text.translatable("pmchat.frag.hint"), x0 + 6, y0 + 16, SUBTLE, false);

        int lo = fragWordFrom < 0 ? -1 : Math.min(fragWordFrom, fragWordTo);
        int hi = fragWordFrom < 0 ? -1 : Math.max(fragWordFrom, fragWordTo);
        int wx = x0 + 6, wy = y0 + 28;
        int lineH = textRenderer.fontHeight + 3;
        int btnTop = y1 - 20;
        for (int i = 0; i < fragWords.size(); i++) {
            String w = fragWords.get(i);
            int ww = textRenderer.getWidth(w) + 6;
            if (wx + ww > x1 - 6) {
                wx = x0 + 6;
                wy += lineH;
            }
            if (wy + lineH > btnTop - 2) break; // не влезло — обрезаем
            boolean sel = lo >= 0 && i >= lo && i <= hi;
            boolean hov = mouseX >= wx && mouseX < wx + ww && mouseY >= wy && mouseY < wy + lineH;
            if (sel) context.fill(wx, wy, wx + ww, wy + lineH, ROW_SELECTED);
            else if (hov) context.fill(wx, wy, wx + ww, wy + lineH, ROW_HOVER);
            context.drawText(textRenderer, w, wx + 3, wy + 2, sel ? 0xFFF0C34E : NAME_TEXT, false);
            fragWordRects.add(new Object[]{wx, wy, ww, lineH, i});
            wx += ww + 2;
        }

        // Кнопки
        boolean ready = fragWordFrom >= 0;
        int okW = 84, caW = 60;
        int okX = x0 + 6, caX = okX + okW + 6, by = btnTop;
        context.fill(okX, by, okX + okW, by + 16, ready ? ACCENT_BG : WBTN_BG);
        context.drawStrokedRectangle(okX, by, okW, 16, ready ? ACCENT_BORDER : WBTN_BORDER);
        Text okT = Text.translatable("pmchat.frag.ok");
        context.drawText(textRenderer, okT, okX + okW / 2 - textRenderer.getWidth(okT) / 2, by + 4,
                ready ? ACCENT_TEXT : SUBTLE, false);
        context.fill(caX, by, caX + caW, by + 16, WBTN_BG);
        context.drawStrokedRectangle(caX, by, caW, 16, WBTN_BORDER);
        Text caT = Text.translatable("pmchat.frag.cancel");
        context.drawText(textRenderer, caT, caX + caW / 2 - textRenderer.getWidth(caT) / 2, by + 4, WBTN_TEXT, false);
        fragOkRect = new int[]{okX, by, okW, 16};
        fragCancelRect = new int[]{caX, by, caW, 16};
    }

    /** Содержимое голосового пузыря: ▶/⏸, полоски и длительность. */
    private void drawVoiceContent(DrawContext context, int x, int y, int w, PmMessage msg, String[] voice, float alpha) {
        int fg = msg.out ? OUT_TEXT : IN_TEXT;
        boolean playing = com.pmchat.client.PmVoice.isPlaying(voice[1]);
        boolean vfail = com.pmchat.client.PmVoice.isFailed(voice[1]);
        context.drawText(textRenderer, vfail ? "⚠" : (playing ? "⏸" : "▶"), x + 6, y + 5,
                applyAlpha(vfail ? 0xFFE07A6A : fg, alpha), false);

        float progress = com.pmchat.client.PmVoice.progress(voice[1]);
        int bars = 9;
        for (int b = 0; b < bars; b++) {
            int bh2 = 3 + ((b * 37 + Integer.parseInt(voice[2]) * 13) % 7);
            int bxx = x + 20 + b * 4;
            boolean passed = playing && (b / (float) bars) < progress;
            int color = passed ? 0xFFA8E8C0 : (msg.out ? 0xFF87B99B : 0xFFB0B0B0);
            context.fill(bxx, y + 12 - bh2, bxx + 2, y + 13, applyAlpha(color, alpha));
        }

        String dur = "0:" + String.format(Locale.ROOT, "%02d", Integer.parseInt(voice[2]));
        context.drawText(textRenderer, dur, x + 58, y + 5, applyAlpha(fg, alpha), false);
    }

    /** Плашка «Ответ: …» над полем ввода. */
    private void renderReplyBar(DrawContext context) {
        replyCancelX = -1;
        if (replyTarget == null || moneyMode) return;
        int y = py + PANEL_H - 36;
        int x = px + LEFT_W + 8;
        int w = PANEL_W - LEFT_W - 16;
        context.fill(x, y, x + w, y + 11, ROW_HOVER);
        context.fill(x, y, x + 1, y + 11, 0xFF6FBF8B);
        String base = replyFragText != null ? replyFragText : (replyTarget.text != null ? replyTarget.text : "");
        String label = "↩ " + PmChatClient.previewOf(base);
        context.drawText(textRenderer, trim(label, w - 20), x + 5, y + 2, 0xFF9CC4DC, false);
        context.drawText(textRenderer, "×", x + w - 9, y + 2, 0xFFE07A6A, false);
        replyCancelX = x + w - 12;
        replyCancelY = y;
    }

    /** Плашка «Изменение сообщения…» над полем ввода. */
    private void renderEditBar(DrawContext context) {
        editCancelX = -1;
        if (editTarget == null || moneyMode) return;
        int y = py + PANEL_H - 36;
        int x = px + LEFT_W + 8;
        int w = PANEL_W - LEFT_W - 16;
        context.fill(x, y, x + w, y + 11, ROW_HOVER);
        context.fill(x, y, x + 1, y + 11, 0xFFF0C34E);
        String label = "✎ " + Text.translatable("pmchat.edit.bar").getString() + ": "
                + PmChatClient.previewOf(editTarget.text != null ? editTarget.text : "");
        context.drawText(textRenderer, trim(label, w - 20), x + 5, y + 2, 0xFFF0C34E, false);
        context.drawText(textRenderer, "×", x + w - 9, y + 2, 0xFFE07A6A, false);
        editCancelX = x + w - 12;
        editCancelY = y;
    }

    /** Сетка эмодзи над полем ввода. */
    private void renderEmojiGrid(DrawContext context, int mouseX, int mouseY) {
        emojiRects.clear();
        emojiCatRects.clear();
        if (!emojiMode || selected == null || moneyMode || statsMode || imageMode) return;
        int cols = 8;
        int cell = 13;
        String[] group = EMOJI_GROUPS[Math.floorMod(emojiCat, EMOJI_GROUPS.length)];
        int rows = (group.length + cols - 1) / cols;
        int gw = cols * cell + 6;
        int gh = 14 + rows * cell + 6; // 14 — ряд категорий
        int gx = px + LEFT_W + 8;
        int gy = py + PANEL_H - 28 - gh - (replyTarget != null ? 12 : 0);

        context.fill(gx, gy, gx + gw, gy + gh, EMOJI_BG);
        context.drawStrokedRectangle(gx, gy, gw, gh, DIVIDER);

        // Ряд категорий
        int catCell = gw / EMOJI_CAT_ICONS.length;
        for (int c = 0; c < EMOJI_CAT_ICONS.length; c++) {
            int cx = gx + c * catCell;
            boolean active = c == Math.floorMod(emojiCat, EMOJI_GROUPS.length);
            boolean hov = mouseX >= cx && mouseX < cx + catCell && mouseY >= gy && mouseY < gy + 13;
            if (active || hov) {
                context.fill(cx, gy, cx + catCell, gy + 13, active ? ROW_SELECTED : ROW_HOVER);
            }
            context.drawText(textRenderer, EMOJI_CAT_ICONS[c], cx + catCell / 2 - 3, gy + 3,
                    active ? 0xFFF0C34E : NAME_TEXT, false);
            emojiCatRects.add(new Object[]{cx, gy, catCell, 13, c});
        }
        context.fill(gx + 2, gy + 13, gx + gw - 2, gy + 14, DIVIDER);

        // Сетка глифов текущей категории
        for (int i = 0; i < group.length; i++) {
            int ex = gx + 3 + (i % cols) * cell;
            int ey = gy + 16 + (i / cols) * cell;
            boolean hovered = mouseX >= ex && mouseX < ex + cell && mouseY >= ey && mouseY < ey + cell;
            if (hovered) {
                context.fill(ex, ey, ex + cell, ey + cell, ROW_SELECTED);
            }
            context.drawText(textRenderer, group[i], ex + 2, ey + 2,
                    config.theme == 1 ? 0xFF23333D : 0xFFEDF3F0, false);
            emojiRects.add(new Object[]{ex, ey, cell, cell, group[i]});
        }
    }

    /** Индикатор записи голосового. */
    private void renderRecordingBar(DrawContext context, int areaBottom) {
        if (!com.pmchat.client.PmVoice.isRecording()) return;
        int secs = com.pmchat.client.PmVoice.recordedSeconds();
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        String label = (blink ? "● " : "  ") + Text.translatable("pmchat.voice.recording").getString()
                + " 0:" + String.format(Locale.ROOT, "%02d", secs) + " / 0:" + com.pmchat.client.PmVoice.MAX_SECONDS;
        context.drawText(textRenderer, label, px + LEFT_W + 8, areaBottom - 8, 0xFFE07A6A, false);
    }

    static String[] imageIdOf(PmMessage msg) {
        if (msg.text == null || msg.money > 0) return null;
        return com.pmchat.client.PmWire.parseImg(msg.text);
    }

    /** Размер картинки в пузыре: вписываем в 110×70. */
    private int[] imageSize(PmImages.Entry img) {
        if (img.state != PmImages.State.READY || img.width <= 0 || img.height <= 0) {
            return new int[]{86, 30};
        }
        float scale = Math.min(1f, Math.min(110f / img.width, 70f / img.height));
        return new int[]{Math.max(16, Math.round(img.width * scale)), Math.max(12, Math.round(img.height * scale))};
    }

    private void drawImageBubble(DrawContext context, int x, int y, int w, int h,
                                 PmMessage msg, PmImages.Entry img, float alpha) {
        int bg = msg.out ? OUT_BG : IN_BG;
        context.fill(x + 1, y, x + w - 1, y + h, applyAlpha(bg, alpha));
        context.fill(x, y + 1, x + w, y + h - 1, applyAlpha(bg, alpha));

        if (img.state == PmImages.State.READY && img.textureId != null) {
            int[] size = imageSize(img);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, img.textureId, x + 6, y + 3,
                    0f, 0f, size[0], size[1], img.width, img.height, img.width, img.height);
        } else {
            Text label = img.state == PmImages.State.FAILED
                    ? Text.translatable("pmchat.image.loadfail")
                    : Text.translatable("pmchat.image.uploading");
            int fg = msg.out ? OUT_TEXT : IN_TEXT;
            context.drawText(textRenderer, label, x + 6, y + h / 2 - 4, applyAlpha(fg, alpha), false);
        }
    }

    private void renderImagePicker(DrawContext context, int mouseX, int mouseY, int areaTop, int areaBottom) {
        shotRects.clear();
        int x = px + LEFT_W + 8;
        context.drawText(textRenderer, Text.translatable("pmchat.image.pick"), x, areaTop + 2, 0xFF6FBF8B, false);
        Text clipHint = Text.translatable("pmchat.image.clip");
        context.drawText(textRenderer, clipHint,
                px + PANEL_W - 10 - textRenderer.getWidth(clipHint), areaTop + 2, SUBTLE, false);

        // NEW (4.9): переключатель «Спойлер» — следующее отправленное фото будет размыто до клика
        drawSpoilerToggle(context, mouseX, mouseY, x, areaTop + 13);

        if (uploading) {
            Text label = Text.translatable("pmchat.image.uploading");
            int hx = px + LEFT_W + (PANEL_W - LEFT_W - textRenderer.getWidth(label)) / 2;
            context.drawText(textRenderer, label, hx, (areaTop + areaBottom) / 2, 0xFFF0C34E, false);
            return;
        }

        if (screenshots.isEmpty() && stickers.isEmpty()) {
            context.drawText(textRenderer, Text.translatable("pmchat.image.empty"), x, areaTop + 34, SUBTLE, false);
            return;
        }

        int y = areaTop + 28;
        for (Path shot : screenshots) {
            if (y + 18 > areaBottom - 4) break;
            int w = PANEL_W - LEFT_W - 16;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
            context.fill(x, y, x + w, y + 17, hovered ? ROW_SELECTED : ROW_HOVER);

            String name = shot.getFileName().toString();
            long kb = 0;
            try {
                kb = Files.size(shot) / 1024;
            } catch (Exception ignored) {
            }
            String size = kb + " KB";
            context.drawText(textRenderer, size, x + w - 5 - textRenderer.getWidth(size), y + 5, PREVIEW_TEXT, false);
            context.drawText(textRenderer, trim(name, w - 14 - textRenderer.getWidth(size)), x + 5, y + 5, NAME_TEXT, false);

            shotRects.add(new Object[]{x, y, w, 18, shot, false});
            y += 20;
        }

        // Стикеры из config/pmchat-stickers
        if (!stickers.isEmpty() && y + 30 <= areaBottom) {
            context.drawText(textRenderer, Text.translatable("pmchat.sticker.title"), x, y + 2, 0xFFF0C34E, false);
            y += 13;
            for (Path sticker : stickers) {
                if (y + 16 > areaBottom) break;
                int w = PANEL_W - LEFT_W - 16;
                boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 15;
                context.fill(x, y, x + w, y + 14, hovered ? ROW_SELECTED : ROW_ALT);
                String name = sticker.getFileName().toString().replace(".png", "").replace(".gif", " (gif)");
                boolean cached = config.stickerCache.containsKey(sticker.getFileName().toString());
                context.drawText(textRenderer, "✿ " + trim(name, w - 30), x + 5, y + 3,
                        cached ? 0xFFA8E8C0 : NAME_TEXT, false);
                shotRects.add(new Object[]{x, y, w, 15, sticker, true});
                y += 17;
            }
        }

        if (uploadFailed) {
            context.drawText(textRenderer, Text.translatable("pmchat.image.failed"), x, areaBottom - 10, 0xFFE07A6A, false);
        }
    }

    /**
     * Перенос текста по ширине пузыря: сначала по пробелам, а если
     * отдельное "слово" (ссылка, спам без пробелов) всё равно шире —
     * рубит его посимвольно, чтобы пузырь никогда не вылезал за панель.
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                result.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String rawWord : paragraph.split(" ", -1)) {
                String word = rawWord;
                // Само слово шире пузыря — рубим посимвольно
                while (textRenderer.getWidth(word) > maxWidth) {
                    int fit = fitChars(word, maxWidth);
                    if (line.length() > 0) {
                        result.add(line.toString());
                        line.setLength(0);
                    }
                    result.add(word.substring(0, fit));
                    word = word.substring(fit);
                }
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (textRenderer.getWidth(candidate) > maxWidth && line.length() > 0) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            result.add(line.toString());
        }
        // Защита от гигантских сообщений — режем на 40 строках
        if (result.size() > 40) {
            result = new ArrayList<>(result.subList(0, 40));
            result.add("…");
        }
        return result;
    }

    /** Сколько символов строки помещается в maxWidth (бинарный поиск). */
    private int fitChars(String s, int maxWidth) {
        int lo = 1, hi = s.length(), best = 1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (textRenderer.getWidth(s.substring(0, mid)) <= maxWidth) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return best;
    }

    private void renderStats(DrawContext context) {
        int x = px + LEFT_W + 10;
        int y = py + 26;
        context.drawText(textRenderer, Text.translatable("pmchat.stats.title"), x, py + 8, TITLE, false);
        context.fill(px + LEFT_W + 1, py + 22, px + PANEL_W - 2, py + 23, DIVIDER);

        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy");
        if (selected != null) {
            context.drawText(textRenderer, trim(selected, PANEL_W - LEFT_W - 20), x, y, 0xFF6FBF8B, false);
            y += 14;
            y = statLine(context, x, y, "pmchat.stats.total", String.valueOf(history.messages(selected).size()));
            y = statLine(context, x, y, "pmchat.stats.sent", String.valueOf(history.countIn(selected, true)));
            y = statLine(context, x, y, "pmchat.stats.received", String.valueOf(history.countIn(selected, false)));
            y = statLine(context, x, y, "pmchat.stats.money", groupDigits(history.moneySent(selected)));
            long first = history.firstTime(selected);
            y = statLine(context, x, y, "pmchat.stats.first", first > 0 ? fmt.format(new Date(first)) : "—");
        } else {
            y = statLine(context, x, y, "pmchat.stats.chats", String.valueOf(history.conversationNames().size()));
            y = statLine(context, x, y, "pmchat.stats.total", String.valueOf(history.totalMessages()));
        }

        // Топ собеседников с мини-полосками
        y += 6;
        context.drawText(textRenderer, Text.translatable("pmchat.stats.top"), x, y, SUBTLE, false);
        y += 12;
        List<Map.Entry<String, Integer>> top = history.topContacts(5);
        int max = top.isEmpty() ? 1 : Math.max(1, top.get(0).getValue());
        int barMax = PANEL_W - LEFT_W - 110;
        for (Map.Entry<String, Integer> e : top) {
            if (y > py + PANEL_H - 16) break;
            context.drawText(textRenderer, trim(e.getKey(), 74), x, y, NAME_TEXT, false);
            int bar = Math.max(2, e.getValue() * barMax / max);
            context.fill(x + 78, y + 2, x + 78 + bar, y + 7, 0xFF4C8A66);
            context.drawText(textRenderer, String.valueOf(e.getValue()), x + 82 + bar, y, PREVIEW_TEXT, false);
            y += 12;
        }
    }

    private int statLine(DrawContext context, int x, int y, String key, String value) {
        Text label = Text.translatable(key);
        context.drawText(textRenderer, label, x, y, PREVIEW_TEXT, false);
        context.drawText(textRenderer, value, px + PANEL_W - 12 - textRenderer.getWidth(value), y, NAME_TEXT, false);
        return y + 12;
    }

    /** Бумажный самолётик ➤ летит от кнопки отправки вверх вдоль чата. */
    private void renderPlane(DrawContext context) {
        if (planeAt < 0) return;
        long age = System.currentTimeMillis() - planeAt;
        if (age > 380) return;
        float t = age / 380f;
        float ease = 1 - (1 - t) * (1 - t);

        float x = px + PANEL_W - 30 - ease * 26;
        float y = py + PANEL_H - 22 - ease * (PANEL_H - 60);
        float rot = -0.5f - ease * 0.6f;
        float alpha = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;

        Matrix3x2fStack m = context.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.rotate(rot);
        context.drawText(textRenderer, "➤", 0, 0, applyAlpha(0xFF9CC4DC, alpha), false);
        m.popMatrix();
    }

    // ---------- Ввод ----------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Полноэкранное фото — любой клик закрывает
        if (fullscreenImg != null) {
            fullscreenImg = null;
            return true;
        }
        com.pmchat.client.PmMedia media = com.pmchat.client.PmMedia.get();
        // NEW (5.3): свёрнутое окошко медиа — ловим только клики по нему,
        // остальной экран (чат) работает как обычно.
        if (!videoResolving && !videoOpenFailed && media.hasActive() && media.isMinimized()) {
            return media.handleMiniClick((int) click.x(), (int) click.y());
        }
        // NEW (4.3): встроенный видеоплеер (полноэкранный) — свои контролы
        if (videoResolving || videoOpenFailed || media.hasActive()) {
            int mx = (int) click.x(), my = (int) click.y();
            com.pmchat.client.PmVlc.Session vsession = media.session();
            String link = media.sourceUrl() != null ? media.sourceUrl() : videoUrl;
            if (inRect(mx, my, videoFallbackRect) || inRect(mx, my, videoBrowserRect)) {
                closeVideoPlayer();
                try {
                    if (link != null) net.minecraft.util.Util.getOperatingSystem().open(link);
                } catch (Exception ignored) {
                }
                return true;
            }
            if (inRect(mx, my, videoCloseRect)) {
                closeVideoPlayer();
                return true;
            }
            if (inRect(mx, my, videoMinRect)) {
                media.setMinimized(true);
                return true;
            }
            if (vsession != null) {
                if (inRect(mx, my, videoPlayRect)) {
                    vsession.togglePause();
                    return true;
                }
                if (inRect(mx, my, videoRateRect)) {
                    float cur = vsession.getRate();
                    int idx = 0;
                    for (int i = 0; i < VIDEO_RATES.length; i++) {
                        if (Math.abs(VIDEO_RATES[i] - cur) < 0.01f) { idx = i; break; }
                    }
                    vsession.setRate(VIDEO_RATES[(idx + 1) % VIDEO_RATES.length]);
                    return true;
                }
                if (inRect(mx, my, videoBarRect)) {
                    videoDragSeek = true;
                    vsession.seekFraction((mx - videoBarRect[0]) / (float) videoBarRect[2]);
                    return true;
                }
                if (inRect(mx, my, videoVolRect)) {
                    videoDragVolume = true;
                    vsession.setVolume(Math.round((mx - videoVolRect[0]) / (float) videoVolRect[2] * 150));
                    return true;
                }
                if (inRect(mx, my, videoImgRect)) {
                    vsession.togglePause();
                    return true;
                }
            }
            return true; // клик по затемнённому фону — просто гасим, не закрываем случайно
        }
        // Оверлей глобального поиска (6.7) перехватывает клики
        if (searchOpen) {
            for (Object[] r : searchResultRects) {
                if (hit(click, new int[]{(int) r[0], (int) r[1], (int) r[2], (int) r[3]})) {
                    String conv = (String) r[4];
                    PmMessage m = (PmMessage) r[5];
                    searchOpen = false;
                    selected = conv;
                    statsMode = false;
                    closeModes();
                    history.clearUnread(conv);
                    if (searchField != null) searchField.setText("");
                    searchText = "";
                    msgScroll = 0;
                    rebuild();
                    if (m.text != null) pendingJumpHash = PmHistory.msgHash(m.text);
                    return true;
                }
            }
            searchOpen = false; // клик мимо — закрыть
            return true;
        }
        // Оверлей списка закреплённых (5.6) перехватывает клики
        if (pinListOpen) {
            for (Object[] r : pinListUnpinRects) {
                if (hit(click, new int[]{(int) r[0], (int) r[1], (int) r[2], (int) r[3]})) {
                    PmChatClient.removePin(selected, (String) r[4], true);
                    if (config.pinnedList(selected).isEmpty()) pinListOpen = false;
                    return true;
                }
            }
            for (Object[] r : pinListRects) {
                if (hit(click, new int[]{(int) r[0], (int) r[1], (int) r[2], (int) r[3]})) {
                    jumpToPin((String) r[4]);
                    pinListOpen = false;
                    return true;
                }
            }
            pinListOpen = false; // клик мимо — закрыть
            return true;
        }
        // Оверлей выбора фрагмента перехватывает клики
        if (fragMsg != null) {
            double cx = click.x(), cy = click.y();
            if (fragOkRect != null && cx >= fragOkRect[0] && cx < fragOkRect[0] + fragOkRect[2]
                    && cy >= fragOkRect[1] && cy < fragOkRect[1] + fragOkRect[3]) {
                confirmFrag();
                return true;
            }
            if (fragCancelRect != null && cx >= fragCancelRect[0] && cx < fragCancelRect[0] + fragCancelRect[2]
                    && cy >= fragCancelRect[1] && cy < fragCancelRect[1] + fragCancelRect[3]) {
                fragMsg = null;
                return true;
            }
            for (Object[] r : fragWordRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (cx >= rx && cx < rx + rw && cy >= ry && cy < ry + rh) {
                    int i = (int) r[4];
                    if (fragWordFrom < 0) {
                        fragWordFrom = i;
                        fragWordTo = i;
                    } else {
                        fragWordTo = i;
                    }
                    return true;
                }
            }
            return true; // клик внутри оверлея — дальше не пропускаем
        }
        // Открытое контекстное меню перехватывает любой клик
        if (ctxMsg != null) {
            PmMessage msg = ctxMsg;
            for (Object[] r : ctxRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                    String action = (String) r[4];
                    ctxMsg = null;
                    boolean global = isFeedTab();
                    if (action.startsWith("react") && !global && selected != null) {
                        PmChatClient.sendReaction(selected, msg, Integer.parseInt(action.substring(5)));
                    } else if (action.equals("reply") && !global) {
                        clearReply();
                        replyTarget = msg;
                    } else if (action.equals("quotefrag") && !global) {
                        openFragSelector(msg);
                    } else if (action.equals("copy") && msg.text != null) {
                        MinecraftClient.getInstance().keyboard.setClipboard(copyText(msg));
                        copiedAt = System.currentTimeMillis();
                        copiedX = rx;
                        copiedY = ry;
                    } else if (action.equals("edit") && !global) {
                        editTarget = msg;
                        clearReply();
                        closeModes();
                        rebuild();
                        if (inputField != null) {
                            inputField.setText(msg.text != null ? msg.text : "");
                            inputField.setCursorToEnd(false); // курсор в конец, снять выделение
                            // Переводим фокус экрана на поле, иначе ввод не доходит,
                            // пока пользователь сам не кликнет в него (баг правки).
                            setFocused(inputField);
                            inputField.setFocused(true);
                        }
                    } else if (action.equals("warn")) {
                        PmChatClient.warnPlayer(senderOfMessage(msg));
                    } else if (action.equals("forward")) {
                        forwardBuffer = msg;
                        forwardFromNick = senderOfMessage(msg);
                    } else if (action.equals("save")) {
                        PmChatClient.saveToFavorites(msg);
                        copiedAt = System.currentTimeMillis();
                        copiedX = rx;
                        copiedY = ry;
                    } else if (action.equals("pin") && !global && selected != null) {
                        PmChatClient.addPin(selected, PmHistory.msgHash(msg.text), true);
                    } else if (action.equals("unpin") && !global && selected != null) {
                        PmChatClient.removePin(selected, PmHistory.msgHash(msg.text), true);
                    } else if (action.equals("delete")) {
                        if (PmChatClient.GLOBAL.equals(selected)) {
                            PmChatClient.getGlobalChat().remove(msg);
                        } else if (channelId() != null) {
                            PmChatClient.getChannelFeed(channelId()).remove(msg);
                        } else if (selected != null) {
                            history.deleteMessage(selected, msg);
                        }
                    }
                    return true;
                }
            }
            ctxMsg = null; // клик мимо — закрыть
            return true;
        }
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        // «＋ Новая группа»
        if (groupNewRect != null && hit(click, groupNewRect)) {
            closeModes();
            statsMode = false;
            groupCreateMode = true;
            rebuild();
            return true;
        }
        for (Object[] r : rowRects) {
            int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
            if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                String tab = (String) r[4];
                // Пересылка: клик по диалогу-получателю
                if (forwardBuffer != null && !PmChatClient.GLOBAL.equals(tab)
                        && !tab.startsWith(PmChatClient.CHANNEL_PREFIX)
                        && !PmChatClient.isGroup(tab)) {
                    PmChatClient.forwardMessage(tab, forwardFromNick, forwardBuffer);
                    forwardBuffer = null;
                    selected = tab;
                    msgScroll = 0;
                    statsMode = false;
                    rebuild();
                    return true;
                }
                boolean hadUnread = history.unreadCount((String) r[4]) > 0;
                selected = (String) r[4];
                history.clearUnread(selected);
                if (selected.startsWith(PmChatClient.CHANNEL_PREFIX)) {
                    PmChatClient.clearChannelUnread(
                            selected.substring(PmChatClient.CHANNEL_PREFIX.length()));
                }
                if (PmChatClient.isGroup(selected)) {
                    PmChatClient.clearGroupUnread(PmChatClient.groupId(selected));
                }
                if (hadUnread) {
                    PmChatClient.sendSeen(selected);
                }
                msgScroll = 0;
                statsMode = false;
                closeModes();
                clearReply();
                editTarget = null;
                clearConfirm = false;
                rebuild();
                return true;
            }
        }
        if (imageMode && !uploading) {
            if (inRect((int) click.x(), (int) click.y(), spoilerToggleRect)) {
                spoilerMode = !spoilerMode;
                return true;
            }
            for (Object[] r : shotRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                    if ((boolean) r[5]) {
                        sendSticker((Path) r[4]);
                    } else {
                        startUpload((Path) r[4]);
                    }
                    return true;
                }
            }
        }
        // Медиа-пикер: видео / аудиофайлы
        if (mediaMode && !uploading) {
            if (inRect((int) click.x(), (int) click.y(), spoilerToggleRect)) {
                spoilerMode = !spoilerMode;
                return true;
            }
            for (Object[] r : mediaRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                    if ((boolean) r[5]) startVideoUpload((Path) r[4]);
                    else startAudioUpload((Path) r[4]);
                    return true;
                }
            }
            return true; // клик внутри пикера — не пропускаем дальше
        }
        // Переключение категории эмодзи
        for (Object[] r : emojiCatRects) {
            int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
            if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                emojiCat = (int) r[4];
                return true;
            }
        }
        // Эмодзи — вставка в поле ввода
        for (Object[] r : emojiRects) {
            int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
            if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                if (inputField != null) {
                    inputField.setText(inputField.getText() + r[4]);
                    inputField.setFocused(true);
                }
                return true;
            }
        }
        // Отмена ответа-цитаты
        if (replyCancelX >= 0 && click.x() >= replyCancelX && click.x() < replyCancelX + 12
                && click.y() >= replyCancelY && click.y() < replyCancelY + 12) {
            clearReply();
            return true;
        }
        // Отмена режима правки
        if (editCancelX >= 0 && click.x() >= editCancelX && click.x() < editCancelX + 12
                && click.y() >= editCancelY && click.y() < editCancelY + 12) {
            editTarget = null;
            if (inputField != null) inputField.setText("");
            inputText = "";
            return true;
        }
        // Кнопка «список закреплённых» (☰)
        if (pinListBtnRect != null && hit(click, pinListBtnRect)) {
            pinListOpen = true;
            return true;
        }
        // × открепить текущий
        if (pinUnpinRect != null && selected != null && hit(click, pinUnpinRect)) {
            List<String> pins = config.pinnedList(selected);
            if (!pins.isEmpty()) {
                String h = pins.get(pins.size() - 1 - Math.floorMod(pinCursor, pins.size()));
                PmChatClient.removePin(selected, h, true);
            }
            return true;
        }
        // Клик по полоске закрепа — переход к текущему + цикл к следующему
        if (pinBarRect != null && selected != null && hit(click, pinBarRect)) {
            List<String> pins = config.pinnedList(selected);
            if (!pins.isEmpty()) {
                String h = pins.get(pins.size() - 1 - Math.floorMod(pinCursor, pins.size()));
                jumpToPin(h);
                if (pins.size() > 1) pinCursor = (pinCursor + 1) % pins.size();
            }
            return true;
        }
        // Панель стикеров: вкладки и ячейки
        if (stickerMode) {
            for (Object[] r : stickerTabRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                    stickerTab = (int) r[4];
                    stickerScroll = 0;
                    return true;
                }
            }
            for (Object[] r : stickerCellRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                    if (!uploading) sendSticker((Path) r[4]);
                    return true;
                }
            }
            return true; // клик внутри панели — не пропускаем дальше
        }
        // Голосование в опросе
        if (!imageMode && !statsMode && click.button() == 0) {
            for (Object[] r : pollOptRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                    if (selected != null && !isFeedTab()) {
                        PmChatClient.castVote(selected, (PmMessage) r[4], (int) r[5]);
                    }
                    return true;
                }
            }
        }
        // 6.8: клик по кнопке ⚠ в ленте — предупредить автора
        if (!imageMode && !statsMode && config.staffFeatures) {
            for (Object[] r : warnBtnRects) {
                if (hit(click, new int[]{(int) r[0], (int) r[1], (int) r[2], (int) r[3]})) {
                    PmChatClient.warnPlayer((String) r[4]);
                    return true;
                }
            }
        }
        // NEW (4.9): клик по «замыленному» спойлеру — первым кликом только открываем
        // (как в Telegram/Discord), второй клик по уже раскрытому фото/видео работает как обычно.
        if (!imageMode && !statsMode && click.button() == 0) {
            for (Object[] r : spoilerRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                    PmMessage msg = (PmMessage) r[4];
                    msg.spoilerRevealed = true;
                    history.save();
                    return true;
                }
            }
        }
        // Клики по пузырям: ЛКМ — голосовое играть, ПКМ — ответить цитатой
        if (!imageMode && !statsMode) {
            for (Object[] r : bubbleRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (click.x() >= rx && click.x() < rx + rw && click.y() >= ry && click.y() < ry + rh) {
                    PmMessage msg = (PmMessage) r[4];
                    if (click.button() == 1) {
                        // ПКМ — контекстное меню
                        ctxMsg = msg;
                        ctxX = (int) click.x();
                        ctxY = (int) click.y();
                        return true;
                    }
                    // Картинка — открыть на весь экран; если не загрузилась (1.0) —
                    // открыть прямую ссылку в браузере (у получателя фото может не
                    // подтянуться в игре, но по ссылке откроется).
                    String[] imgRef = imageIdOf(msg);
                    if (imgRef != null) {
                        PmImages.Entry e = PmImages.get(imgRef[0], imgRef[1]);
                        if (e.state == PmImages.State.READY && e.currentTexture() != null) {
                            fullscreenImg = e;
                        } else {
                            try {
                                net.minecraft.util.Util.getOperatingSystem()
                                        .open(com.pmchat.client.PmHosts.baseUrl(imgRef[0]) + imgRef[1]);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    }
                    String[] voice = voiceOf(msg);
                    if (voice != null) {
                        com.pmchat.client.PmVoice.togglePlay(voice[0], voice[1]);
                        return true;
                    }
                    // NEW (4.3): видео — свой плеер поверх окна (пауза/громкость/скорость),
                    // если у игрока установлен VLC. Иначе — старое поведение (открыть внешне).
                    String[] vid = vidOf(msg);
                    if (vid != null) {
                        String url = com.pmchat.client.PmHosts.baseUrl(vid[0]) + vid[1];
                        if (com.pmchat.client.PmVlc.isAvailable()) {
                            openVideoPlayer(url);
                        } else {
                            try {
                                net.minecraft.util.Util.getOperatingSystem().open(url);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    }
                    // Текст: ссылка — открыть в браузере (YouTube — во встроенном плеере), иначе — скопировать
                    if (msg.text != null && imageIdOf(msg) == null) {
                        Matcher url = URL_PATTERN.matcher(msg.text);
                        if (url.find()) {
                            String link = url.group(1);
                            // NEW (4.3): ссылка на YouTube — пробуем во встроенном плеере (через VLC)
                            if (isYouTubeUrl(link) && com.pmchat.client.PmVlc.isAvailable()) {
                                openVideoPlayer(link);
                                return true;
                            }
                            try {
                                net.minecraft.util.Util.getOperatingSystem().open(link);
                            } catch (Exception ignored) {
                            }
                            return true;
                        }
                        MinecraftClient.getInstance().keyboard.setClipboard(copyText(msg));
                        copiedAt = System.currentTimeMillis();
                        copiedX = (int) click.x();
                        copiedY = (int) click.y();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Контекстное меню в стиле Telegram: реакции сверху, действия списком. */
    private void renderCtxMenu(DrawContext context, int mouseX, int mouseY) {
        ctxRects.clear();
        if (ctxMsg == null) return;

        boolean global = isFeedTab();
        // NEW (6.10): у секретных сообщений отключаем всё, что уходит по сети в открытом виде
        // (ответ/цитата/пересылка/правка/закреп текстом) — остаются только локальные действия.
        boolean secretMsg = ctxMsg.secret;
        boolean canReact = !global && !secretMsg && selected != null && config.isModUser(selected)
                && ctxMsg.text != null && !ctxMsg.text.isBlank();

        List<String[]> items = new ArrayList<>();
        if (!global && !secretMsg) items.add(new String[]{"reply", "↩ " + Text.translatable("pmchat.menu.reply").getString()});
        if (!global && !secretMsg && ctxMsg.text != null && !ctxMsg.text.isBlank()
                && imageIdOf(ctxMsg) == null && voiceOf(ctxMsg) == null && !ctxMsg.isPoll()
                && ctxMsg.text.trim().contains(" ")) {
            items.add(new String[]{"quotefrag", "❝ " + Text.translatable("pmchat.menu.quotefrag").getString()});
        }
        if (!secretMsg) {
            items.add(new String[]{"forward", "⤶ " + Text.translatable("pmchat.menu.forward").getString()});
        }
        if (!PmChatClient.SAVED.equals(selected) && !secretMsg) {
            items.add(new String[]{"save", "✦ " + Text.translatable("pmchat.menu.save").getString()});
        }
        if (ctxMsg.text != null && !ctxMsg.text.isBlank()) {
            items.add(new String[]{"copy", "⧉ " + Text.translatable("pmchat.menu.copy").getString()});
        }
        // Правка своего текстового сообщения — только в модовом диалоге/группе (или Избранное)
        boolean editable = !global && !secretMsg && ctxMsg.out && ctxMsg.text != null && !ctxMsg.text.isBlank()
                && imageIdOf(ctxMsg) == null && voiceOf(ctxMsg) == null && !ctxMsg.isPoll() && ctxMsg.money <= 0
                && selected != null
                && (PmChatClient.isLocalChat(selected) || config.isModUser(selected));
        if (editable) {
            items.add(new String[]{"edit", "✎ " + Text.translatable("pmchat.menu.edit").getString()});
        }
        // Закрепить/открепить — только в личных диалогах (секретные сообщения не закрепляются)
        if (!global && !secretMsg && selected != null && ctxMsg.text != null && !ctxMsg.text.isBlank()) {
            boolean isPinned = config.isPinned(selected, PmHistory.msgHash(ctxMsg.text));
            items.add(isPinned
                    ? new String[]{"unpin", "⚐ " + Text.translatable("pmchat.menu.unpin").getString()}
                    : new String[]{"pin", "⚑ " + Text.translatable("pmchat.menu.pin").getString()});
        }
        // 6.1/6.8: предупреждение автору (для хелперов; по умолчанию выкл). Работает
        // и в ЛС, и в общем чате/каналах — исключаем только Избранное и свои сообщения.
        if (config.staffFeatures && !ctxMsg.out
                && !PmChatClient.SAVED.equals(selected) && !PmChatClient.isCoreProtect(selected)) {
            String who = senderOfMessage(ctxMsg);
            if (who != null && !who.isBlank() && !who.equals("?")) {
                items.add(new String[]{"warn", "⚠ " + Text.translatable("pmchat.menu.warn").getString()});
            }
        }
        items.add(new String[]{"delete", "✖ " + Text.translatable("pmchat.menu.delete").getString()});

        // 5.8: инфо-строка «прочитано когда» для своих сообщений в личном модовом диалоге
        String infoLine = null;
        if (!global && ctxMsg.out && selected != null && !PmChatClient.isLocalChat(selected)
                && config.isModUser(selected)) {
            if (ctxMsg.read) {
                String when = ctxMsg.readTime > 0
                        ? new SimpleDateFormat("dd.MM HH:mm").format(new Date(ctxMsg.readTime)) : "";
                infoLine = "✔✔ " + Text.translatable("pmchat.menu.readat").getString()
                        + (when.isEmpty() ? "" : " " + when);
            } else {
                infoLine = "✔ " + Text.translatable("pmchat.menu.unread").getString();
            }
        }
        // Метка «изменено» для отредактированных сообщений
        String editedLine = null;
        if (ctxMsg.edited) {
            String when = ctxMsg.editTime > 0
                    ? new SimpleDateFormat("dd.MM HH:mm").format(new Date(ctxMsg.editTime)) : "";
            editedLine = "✎ " + Text.translatable("pmchat.menu.editedat").getString()
                    + (when.isEmpty() ? "" : " " + when);
        }

        int itemH = 14;
        int reactH = canReact ? 19 : 0;
        int infoH = (infoLine != null ? 11 : 0) + (editedLine != null ? 11 : 0);
        int w = 104;
        for (String[] it : items) {
            w = Math.max(w, textRenderer.getWidth(it[1]) + 16);
        }
        if (infoLine != null) w = Math.max(w, textRenderer.getWidth(infoLine) + 14);
        if (editedLine != null) w = Math.max(w, textRenderer.getWidth(editedLine) + 14);
        int h = infoH + reactH + items.size() * itemH + 6;
        int mx = Math.max(px + 4, Math.min(ctxX, px + PANEL_W - w - 4));
        int my = Math.max(py + 4, Math.min(ctxY, py + PANEL_H - h - 4));

        // Отдельный слой поверх всего интерфейса (включая поля ввода)
        context.createNewRootLayer();
        // Затемнение панели под меню
        context.fill(px, py, px + PANEL_W, py + PANEL_H, 0x66000000);
        // Тень
        context.fill(mx + 3, my + 3, mx + w + 3, my + h + 3, 0x55000000);
        // Непрозрачный корпус
        context.fill(mx + 1, my, mx + w - 1, my + h, LEFT_BG);
        context.fill(mx, my + 1, mx + w, my + h - 1, LEFT_BG);
        context.drawStrokedRectangle(mx, my, w, h, DIVIDER);

        int y = my + 3;
        if (infoLine != null) {
            context.drawText(textRenderer, infoLine, mx + 7, y, ctxMsg.read ? 0xFFA8E8C0 : SUBTLE, false);
            y += 11;
        }
        if (editedLine != null) {
            context.drawText(textRenderer, editedLine, mx + 7, y, SUBTLE, false);
            y += 11;
        }
        if (infoH > 0) {
            context.fill(mx + 4, y, mx + w - 4, y + 1, DIVIDER);
            y += 2;
        }
        if (canReact) {
            int cell = Math.min(16, (w - 8) / com.pmchat.client.PmWire.REACTIONS.length);
            int rx = mx + 4;
            for (int i = 0; i < com.pmchat.client.PmWire.REACTIONS.length; i++) {
                boolean hovered = mouseX >= rx && mouseX < rx + cell && mouseY >= y && mouseY < y + 14;
                if (hovered) {
                    context.fill(rx, y, rx + cell, y + 14, ROW_SELECTED);
                }
                context.drawText(textRenderer, com.pmchat.client.PmWire.REACTIONS[i], rx + 3, y + 3,
                        com.pmchat.client.PmWire.REACTION_COLORS[i], false);
                ctxRects.add(new Object[]{rx, y, cell, 14, "react" + i});
                rx += cell;
            }
            // Разделитель между реакциями и пунктами
            context.fill(mx + 4, y + 16, mx + w - 4, y + 17, DIVIDER);
            y += reactH;
        }
        for (String[] it : items) {
            boolean hovered = mouseX >= mx && mouseX < mx + w && mouseY >= y && mouseY < y + itemH;
            if (hovered) {
                context.fill(mx + 1, y, mx + w - 1, y + itemH, ROW_SELECTED);
            }
            int color = it[0].equals("delete") ? 0xFFE07A6A : NAME_TEXT;
            context.drawText(textRenderer, it[1], mx + 7, y + 3, color, false);
            ctxRects.add(new Object[]{mx, y, w, itemH, it[0]});
            y += itemH;
        }
    }

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");

    /** NEW (4.3): ссылка на youtube.com/youtu.be — пробуем открыть во встроенном плеере. */
    private static boolean isYouTubeUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("youtube.com/watch") || u.contains("youtube.com/shorts")
                || u.contains("youtu.be/");
    }
    private long copiedAt = -1;
    private int copiedX, copiedY;

    private static boolean sameDay(long a, long b) {
        java.util.Calendar ca = java.util.Calendar.getInstance();
        java.util.Calendar cb = java.util.Calendar.getInstance();
        ca.setTimeInMillis(a);
        cb.setTimeInMillis(b);
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR)
                && ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) {
            if (searchOpen) {
                searchScroll -= (int) Math.signum(vertical) * 2;
                if (searchScroll < 0) searchScroll = 0;
                return true;
            }
            if (stickerMode && mouseX >= px + LEFT_W) {
                stickerScroll -= (int) Math.signum(vertical);
                if (stickerScroll < 0) stickerScroll = 0;
                return true;
            }
            if (mouseX < px + LEFT_W && mouseX >= px) {
                listScroll -= (int) Math.signum(vertical) * 2;
                if (listScroll < 0) listScroll = 0;
                return true;
            }
            if (mouseX >= px + LEFT_W && mouseX < px + PANEL_W) {
                // Колёсико листает чат крупным шагом (одна «зазубрина» ≈ 3 строки).
                int step = Math.round((float) (vertical * 42));
                msgScroll = Math.max(0, Math.min(msgMaxScroll, msgScroll + step));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // Esc закрывает полноэкранное фото, не выходя из чата
        if (fullscreenImg != null && input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            fullscreenImg = null;
            return true;
        }
        // NEW (5.3): Esc закрывает полноэкранный видеоплеер (и останавливает VLC).
        // Свёрнутое окошко Esc не трогает — оно поверх рабочего чата, играет дальше.
        com.pmchat.client.PmMedia pmMedia = com.pmchat.client.PmMedia.get();
        boolean fullscreenVideo = videoResolving || videoOpenFailed
                || (pmMedia.hasActive() && !pmMedia.isMinimized());
        if (fullscreenVideo && input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            closeVideoPlayer();
            return true;
        }
        // Esc закрывает оверлей выбора фрагмента
        if (fragMsg != null && input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            fragMsg = null;
            return true;
        }
        // Esc закрывает список закреплённых
        if (pinListOpen && input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            pinListOpen = false;
            return true;
        }
        // Esc отменяет режим правки, не выходя из чата
        if (editTarget != null && input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            editTarget = null;
            if (inputField != null) inputField.setText("");
            inputText = "";
            return true;
        }
        // Ctrl+V: если в буфере картинка — отправляем её как фото
        if (selected != null && !statsMode && !uploading
                && input.getKeycode() == GLFW.GLFW_KEY_V
                && (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
            java.nio.file.Path clip = com.pmchat.client.PmClipboard.tryAwtImage();
            if (clip != null) {
                startUpload(clip);
                return true;
            }
            // AWT не дал картинку — проверяем через PowerShell в фоне,
            // а обычную текстовую вставку пропускаем дальше
            String target = selected;
            com.pmchat.client.PmClipboard.tryPowershellImage().thenAccept(path -> {
                if (path != null) {
                    MinecraftClient.getInstance().execute(() -> {
                        if (target.equalsIgnoreCase(selected) && !uploading) {
                            startUpload(path);
                        }
                    });
                }
            });
            return super.keyPressed(input);
        }
        // Enter в поле поиска — открыть глобальный поиск по всем чатам (6.7)
        if (searchField != null && searchField.isFocused() && !searchOpen
                && (input.getKeycode() == GLFW.GLFW_KEY_ENTER || input.getKeycode() == GLFW.GLFW_KEY_KP_ENTER)) {
            if (query().length() >= 2) {
                searchOpen = true;
                searchScroll = 0;
                return true;
            }
        }
        // Esc закрывает глобальный поиск
        if (searchOpen && input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            searchOpen = false;
            return true;
        }
        // Tab — автодополнение ника из списка игроков (6.6, как в ваниле)
        if (inputField != null && inputField.isFocused()
                && input.getKeycode() == GLFW.GLFW_KEY_TAB) {
            completeNick();
            return true;
        }
        if (inputField != null && inputField.isFocused()
                && (input.getKeycode() == GLFW.GLFW_KEY_ENTER || input.getKeycode() == GLFW.GLFW_KEY_KP_ENTER)) {
            doSend();
            return true;
        }
        if (amountField != null && amountField.isFocused()
                && (input.getKeycode() == GLFW.GLFW_KEY_ENTER || input.getKeycode() == GLFW.GLFW_KEY_KP_ENTER)) {
            doPay();
            return true;
        }
        // Клавишу открытия НЕ используем для закрытия (коллизия с русской «о» на J).
        // Меню закрывается по Esc (ниже) или крестику.
        return super.keyPressed(input);
    }

    /**
     * Автодополнение последнего слова ника по списку игроков (6.6).
     * Повторный Tab циклит по совпадениям, как ванильный чат.
     */
    private void completeNick() {
        if (inputField == null) return;
        String text = inputField.getText();
        // Продолжаем цикл, если поле не менялось руками после прошлого Tab
        boolean cycling = tabLastCompleted != null && tabLastCompleted.equals(text) && !tabMatches.isEmpty();
        if (!cycling) {
            int sp = Math.max(text.lastIndexOf(' '), text.lastIndexOf('\n'));
            String prefix = text.substring(sp + 1);
            if (prefix.isEmpty()) return;
            String pl = prefix.toLowerCase(Locale.ROOT);
            tabMatches.clear();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() != null) {
                java.util.List<String> names = new ArrayList<>();
                for (net.minecraft.client.network.PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                    String n = e.getProfile().name();
                    if (n != null && n.toLowerCase(Locale.ROOT).startsWith(pl)) names.add(n);
                }
                names.sort(String.CASE_INSENSITIVE_ORDER);
                tabMatches.addAll(names);
            }
            tabIndex = -1;
            if (tabMatches.isEmpty()) return;
        }
        tabIndex = (tabIndex + 1) % tabMatches.size();
        int sp = Math.max(text.lastIndexOf(' '), text.lastIndexOf('\n'));
        // При цикле надо заменять уже подставленный ник — режем по позиции прошлой замены
        String base = cycling ? tabBase : text.substring(0, sp + 1);
        if (!cycling) tabBase = base;
        String completed = base + tabMatches.get(tabIndex);
        inputField.setText(completed);
        inputField.setCursorToEnd(false);
        tabLastCompleted = completed;
    }

    private String tabBase = "";

    private boolean anyFieldFocused() {
        if (searchField != null && searchField.isFocused()) return true;
        if (inputField != null && inputField.isFocused()) return true;
        return amountField != null && amountField.isFocused();
    }

    // ---------- Утилиты ----------

    /** Текст для копирования: с ником, если включено в настройках. */
    private String copyText(PmMessage msg) {
        // Картинка/голосовое: копируем прямую ссылку, а не слово-заглушку «Фото».
        String[] imgRef = imageIdOf(msg);
        if (imgRef != null) {
            return com.pmchat.client.PmHosts.baseUrl(imgRef[0]) + imgRef[1];
        }
        String[] voice = voiceOf(msg);
        if (voice != null) {
            return com.pmchat.client.PmHosts.baseUrl(voice[0]) + voice[1];
        }
        String[] vid = vidOf(msg);
        if (vid != null) {
            return com.pmchat.client.PmHosts.baseUrl(vid[0]) + vid[1];
        }
        String body = PmChatClient.previewOf(msg.text);
        if (config.mentionOnCopy) {
            String nick = senderOfMessage(msg);
            return nick + ": " + body;
        }
        return body;
    }

    private static boolean hit(Click click, int[] r) {
        return r != null && click.x() >= r[0] && click.x() < r[0] + r[2]
                && click.y() >= r[1] && click.y() < r[1] + r[3];
    }

    /** Прокручивает чат к закреплённому сообщению по хэшу + вспышка. */
    private void jumpToPin(String hash) {
        Integer off = pinOffsets.get(hash);
        if (off != null && off > 0) {
            int areaH = (py + PANEL_H - 30) - (py + 40);
            msgScroll = Math.max(0, Math.min(msgMaxScroll, off - areaH / 2));
        }
        flashHash = hash;
        flashUntil = System.currentTimeMillis() + 1400;
    }

    /** Собирает совпадения запроса во всех личных диалогах: {conv, msg}. */
    private List<Object[]> collectSearchHits(String q) {
        List<Object[]> hits = new ArrayList<>();
        for (String conv : history.conversationNames()) {
            for (PmMessage m : history.messages(conv)) {
                if (m.text != null && m.text.toLowerCase(Locale.ROOT).contains(q)) {
                    hits.add(new Object[]{conv, m});
                }
            }
        }
        // Сначала свежие
        hits.sort((a, b) -> Long.compare(((PmMessage) b[1]).time, ((PmMessage) a[1]).time));
        return hits;
    }

    /** Оверлей глобального поиска по всем чатам (6.7, как в Telegram). */
    private void renderSearchResults(DrawContext context, int mouseX, int mouseY) {
        searchResultRects.clear();
        if (!searchOpen) return;
        String q = query();
        if (q.length() < 2) return;

        int x0 = px + 6, x1 = px + PANEL_W - 6;
        int y0 = py + 22, y1 = py + PANEL_H - 6;
        context.createNewRootLayer();
        context.fill(px, py, px + PANEL_W, py + PANEL_H, 0x99000000);
        context.fill(x0 + 1, y0, x1 - 1, y1, LEFT_BG);
        context.fill(x0, y0 + 1, x1, y1 - 1, LEFT_BG);
        context.drawStrokedRectangle(x0, y0, x1 - x0, y1 - y0, DIVIDER);

        List<Object[]> hits = collectSearchHits(q);
        String title = "🔍 " + Text.translatable("pmchat.search.results").getString() + " (" + hits.size() + ")";
        context.drawText(textRenderer, title, x0 + 6, y0 + 5, 0xFF6FBF8B, false);
        context.drawText(textRenderer, "×", x1 - 12, y0 + 5, 0xFFE07A6A, false);

        int rowH = 22;
        int top = y0 + 20;
        int visible = Math.max(1, (y1 - top - 4) / rowH);
        int maxScroll = Math.max(0, hits.size() - visible);
        searchScroll = Math.max(0, Math.min(searchScroll, maxScroll));

        if (hits.isEmpty()) {
            context.drawText(textRenderer, Text.translatable("pmchat.notfound"), x0 + 6, top + 4, SUBTLE, false);
            return;
        }
        int y = top;
        for (int i = searchScroll; i < hits.size() && y + rowH <= y1 - 2; i++) {
            String conv = (String) hits.get(i)[0];
            PmMessage m = (PmMessage) hits.get(i)[1];
            int rw = x1 - x0 - 12;
            boolean hov = mouseX >= x0 + 6 && mouseX < x0 + 6 + rw && mouseY >= y && mouseY < y + rowH - 2;
            context.fill(x0 + 6, y, x0 + 6 + rw, y + rowH - 2, hov ? ROW_SELECTED : ROW_HOVER);
            String who = (m.out ? "→ " : "") + conv;
            context.drawText(textRenderer, trim(who, rw - 60), x0 + 10, y + 3, 0xFF9CC4DC, false);
            String when = new SimpleDateFormat("dd.MM HH:mm").format(new Date(m.time));
            context.drawText(textRenderer, when, x0 + 6 + rw - textRenderer.getWidth(when) - 4, y + 3, SUBTLE, false);
            context.drawText(textRenderer, trim(PmChatClient.previewOf(m.text), rw - 12), x0 + 10, y + 12, NAME_TEXT, false);
            searchResultRects.add(new Object[]{x0 + 6, y, rw, rowH - 2, conv, m});
            y += rowH;
        }
    }

    /** Оверлей списка закреплённых сообщений (5.6, как в Telegram). */
    private void renderPinList(DrawContext context, int mouseX, int mouseY) {
        pinListRects.clear();
        pinListUnpinRects.clear();
        if (!pinListOpen || selected == null) return;
        List<String> pins = config.pinnedList(selected);
        if (pins.isEmpty()) { pinListOpen = false; return; }

        int x0 = px + LEFT_W + 6, x1 = px + PANEL_W - 6;
        int y0 = py + 22, y1 = py + PANEL_H - 6;
        context.createNewRootLayer();
        context.fill(px, py, px + PANEL_W, py + PANEL_H, 0x88000000);
        context.fill(x0 + 1, y0, x1 - 1, y1, LEFT_BG);
        context.fill(x0, y0 + 1, x1, y1 - 1, LEFT_BG);
        context.drawStrokedRectangle(x0, y0, x1 - x0, y1 - y0, DIVIDER);
        context.drawText(textRenderer, "⚑ " + Text.translatable("pmchat.pin.list").getString()
                + " (" + pins.size() + ")", x0 + 6, y0 + 5, 0xFFF0C34E, false);
        context.drawText(textRenderer, "×", x1 - 12, y0 + 5, 0xFFE07A6A, false);

        int rowH = 18;
        int y = y0 + 20;
        for (int i = pins.size() - 1; i >= 0 && y + rowH <= y1 - 4; i--) { // свежие сверху
            String h = pins.get(i);
            PmMessage m = history.findByHash(selected, h);
            String body = m != null ? PmChatClient.previewOf(m.text != null ? m.text : "") : "…";
            int rw = x1 - x0 - 12;
            boolean hov = mouseX >= x0 + 6 && mouseX < x0 + 6 + rw - 16 && mouseY >= y && mouseY < y + rowH - 2;
            context.fill(x0 + 6, y, x0 + 6 + rw, y + rowH - 2, hov ? ROW_SELECTED : ROW_HOVER);
            context.drawText(textRenderer, trim(body, rw - 22), x0 + 10, y + 4, NAME_TEXT, false);
            context.drawText(textRenderer, "×", x0 + 6 + rw - 10, y + 4, 0xFFE07A6A, false);
            pinListRects.add(new Object[]{x0 + 6, y, rw - 16, rowH - 2, h});
            pinListUnpinRects.add(new Object[]{x0 + 6 + rw - 14, y, 14, rowH - 2, h});
            y += rowH;
        }
    }

    private int nameColor(String name) {
        int[] palette = com.pmchat.client.PmPalettes.NAMES;
        if (config.uniformNames) {
            return palette[Math.floorMod(config.nameColor, palette.length)];
        }
        return palette[Math.abs(name.toLowerCase(Locale.ROOT).hashCode()) % palette.length];
    }

    private String trim(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) return text;
        return textRenderer.trimToWidth(text, Math.max(0, maxWidth - textRenderer.getWidth("…"))) + "…";
    }

    private String groupDigits(long value) {
        String grouped = String.format(Locale.US, "%,d", value);
        return PmChatClient.isRussian() ? grouped.replace(',', ' ') : grouped;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        if (a < 4) a = 4;
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // NEW (5.3): закрытие окна мессенджера НЕ останавливает медиа — PmMedia
    // продолжает играть и рисует окошко поверх HUD. Но если идёт ПОДГОТОВКА
    // видео (yt-dlp), а сессии ещё нет и юзер закрыл экран — отменяем её,
    // чтобы не тратить трафик впустую (кроме уже свёрнутого сценария).
    @Override
    public void close() {
        cancelPrepIfNoSession();
        super.close();
    }

    @Override
    public void removed() {
        cancelPrepIfNoSession();
        super.removed();
    }

    /** Отменяет незавершённую подготовку видео (нет активной сессии) при закрытии экрана. */
    private void cancelPrepIfNoSession() {
        if ((videoResolving || videoOpenFailed) && !com.pmchat.client.PmMedia.get().hasActive()) {
            videoSeq++; // «просрочить» фоновый резолв — результат будет отброшен
            videoResolving = false;
            videoOpenFailed = false;
            videoUrl = null;
        }
    }
}
