package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmUpdate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Панелька «Что нового» — открывается из мессенджера (кнопка 📖 внизу слева).
 * Показывает версию мода и список изменений текущего релиза. Тексты — в lang
 * (pmchat.whatsnew.*), поэтому и RU, и EN подхватываются автоматически.
 */
@Environment(EnvType.CLIENT)
public class PmWhatsNewScreen extends Screen {

    private static final int LINE_H = 11;

    // Не static final — подгоняются под размер экрана в init() (см. GUI Scale 4 и т.п.),
    // иначе панель шире/выше настоящего окна и всё наезжает друг на друга.
    private int PANEL_W = 300;
    private int TEXT_W = PANEL_W - 40;   // отступы слева/справа + буллет

    /** Ключи пунктов «что нового» этого релиза (порядок = порядок показа). */
    private static final String[] ITEMS = {
            "pmchat.whatsnew.no_discord_sound",
            "pmchat.whatsnew.video_preview",
            "pmchat.whatsnew.broadcast_author",
            "pmchat.whatsnew.contact_star",
            "pmchat.whatsnew.streams",
            "pmchat.whatsnew.photoedit_pro",
            "pmchat.whatsnew.discord_btn",
            "pmchat.whatsnew.profile_fix",
            "pmchat.whatsnew.views_fix",
            "pmchat.whatsnew.video_title",
            "pmchat.whatsnew.photo_preview",
            "pmchat.whatsnew.minigames",
            "pmchat.whatsnew.minigames_anim",
    };

    private int BG, BORDER, LABEL, TITLE, SUBTLE, BTN_BG, BTN_HOVER, BTN_BORDER;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py, panelH;
    // Заранее развёрнутые в строки пункты (иконка-буллет рисуется у первой строки).
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final List<Boolean> firstLine = new ArrayList<>();

    public PmWhatsNewScreen(Screen parent) {
        super(Component.translatable("pmchat.whatsnew.title"));
        this.parent = parent;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        SUBTLE = t.value;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder;
    }

    @Override
    protected void init() {
        applyTheme();
        clearWidgets();
        lines.clear();
        firstLine.clear();

        PANEL_W = Math.max(160, Math.min(300, width - 24));
        TEXT_W = PANEL_W - 40;

        for (String key : ITEMS) {
            List<FormattedCharSequence> wrapped = font.split(Component.translatable(key), TEXT_W);
            if (wrapped.isEmpty()) continue;
            for (int i = 0; i < wrapped.size(); i++) {
                lines.add(wrapped.get(i));
                firstLine.add(i == 0);
            }
        }

        // Шапка (заголовок + версия) + список + кнопка «Готово».
        int headerH = 40;
        int listH = lines.size() * LINE_H + 6;
        panelH = headerH + listH + 30;
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        addRenderableWidget(FlatButton.centered(font, px + PANEL_W / 2 - 40, py + panelH - 24, 80, 18,
                Component.translatable("pmchat.settings.done"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> onClose()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.outline(px, py, PANEL_W, panelH, BORDER);

        Component title = Component.translatable("pmchat.whatsnew.title");
        context.text(font, title,
                px + (PANEL_W - font.width(title)) / 2, py + 9, TITLE, false);

        Component ver = Component.translatable("pmchat.whatsnew.version", PmUpdate.currentVersion());
        context.text(font, ver,
                px + (PANEL_W - font.width(ver)) / 2, py + 22, SUBTLE, false);

        // Разделитель под шапкой
        context.fill(px + 12, py + 36, px + PANEL_W - 12, py + 37, BORDER);

        int y = py + 43;
        for (int i = 0; i < lines.size(); i++) {
            if (firstLine.get(i)) {
                context.text(font, "✦", px + 14, y, 0xFF6FBF8B, false);
            }
            context.text(font, lines.get(i), px + 28, y, LABEL, false);
            y += LINE_H;
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Minecraft client = Minecraft.getInstance();
        client.gui.setScreen(parent instanceof PmScreen ? new PmScreen() : parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
