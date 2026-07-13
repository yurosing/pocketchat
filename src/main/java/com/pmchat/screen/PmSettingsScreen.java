package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmImages;
import com.pmchat.client.PmPalettes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Экран настроек мода: открывается кнопкой ⚙ в чате и из Mod Menu.
 * Каждая строка — параметр, клик по значению перебирает варианты,
 * всё применяется и сохраняется сразу.
 */
@Environment(EnvType.CLIENT)
public class PmSettingsScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int ROW_H = 17;

    private static final int BG = 0xFF1C3644;
    private static final int BORDER = 0xFF10222C;
    private static final int LABEL = 0xFF9CC4DC;
    private static final int TITLE = 0xFFF2F6F8;
    private static final int BTN_BG = 0xFF15303D;
    private static final int BTN_HOVER = 0xFF0F2833;
    private static final int BTN_BORDER = 0xFF2A4A5C;
    private static final int VALUE = 0xFFEDF3F0;

    private static final int[] SCALES = {80, 90, 100, 110, 125};
    private static final int[] VOLUMES = {25, 50, 75, 100};

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py, panelH;

    public PmSettingsScreen(Screen parent) {
        super(Text.translatable("pmchat.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        optionLabels.clear();
        clearChildren();
        int rows = 18;
        panelH = 26 + rows * ROW_H + 28;
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        int y = py + 24;
        y = addOption(y, "pmchat.set.theme",
                () -> Text.translatable(config.theme == 1 ? "pmchat.set.theme.light" : "pmchat.set.theme.dark"),
                VALUE, () -> config.theme = config.theme == 1 ? 0 : 1);

        y = addOption(y, "pmchat.set.outcolor",
                () -> Text.literal("■ " + (config.outColor % PmPalettes.OUT.length + 1)),
                () -> PmPalettes.OUT[Math.floorMod(config.outColor, PmPalettes.OUT.length)],
                () -> config.outColor = (config.outColor + 1) % PmPalettes.OUT.length);

        y = addOption(y, "pmchat.set.incolor",
                () -> Text.literal("■ " + (config.inColor % PmPalettes.IN.length + 1)),
                () -> PmPalettes.IN[Math.floorMod(config.inColor, PmPalettes.IN.length)],
                () -> config.inColor = (config.inColor + 1) % PmPalettes.IN.length);

        y = addOption(y, "pmchat.set.names",
                () -> Text.translatable(config.uniformNames ? "pmchat.set.names.uniform" : "pmchat.set.names.rainbow"),
                VALUE, () -> config.uniformNames = !config.uniformNames);

        y = addOption(y, "pmchat.set.namecolor",
                () -> Text.literal("■ " + (config.nameColor % PmPalettes.NAMES.length + 1)),
                () -> PmPalettes.NAMES[Math.floorMod(config.nameColor, PmPalettes.NAMES.length)],
                () -> config.nameColor = (config.nameColor + 1) % PmPalettes.NAMES.length);

        y = addOption(y, "pmchat.set.textscale",
                () -> Text.literal(config.textScalePct + "%"),
                VALUE, () -> config.textScalePct = SCALES[(indexOf(SCALES, config.textScalePct) + 1) % SCALES.length]);

        y = addOption(y, "pmchat.set.uiscale",
                () -> Text.literal(switch (Math.floorMod(config.uiScale, 3)) {
                    case 1 -> "M";
                    case 2 -> "L";
                    default -> "S";
                }),
                VALUE, () -> config.uiScale = (config.uiScale + 1) % 3);

        y = addOption(y, "pmchat.set.mention",
                () -> Text.translatable(config.mentionEnabled ? "pmchat.set.mention.on" : "pmchat.set.mention.off"),
                () -> config.mentionEnabled ? 0xFFF0C34E : VALUE,
                () -> config.mentionEnabled = !config.mentionEnabled);

        y = addOption(y, "pmchat.set.dnd",
                () -> Text.translatable(config.dnd ? "pmchat.set.dnd.on" : "pmchat.set.dnd.off"),
                () -> config.dnd ? 0xFFE07A6A : 0xFF8FD8A8,
                () -> config.dnd = !config.dnd);

        y = addOption(y, "pmchat.set.sound",
                () -> Text.translatable("pmchat.set.sound." + Math.floorMod(config.notifySound, 4)),
                VALUE, () -> {
                    config.notifySound = (config.notifySound + 1) % 4;
                    PmChatClient.playNotifySound(MinecraftClient.getInstance()); // предпрослушка
                });

        y = addOption(y, "pmchat.set.volume",
                () -> Text.literal(config.notifyVolume + "%"),
                VALUE, () -> {
                    config.notifyVolume = VOLUMES[(indexOf(VOLUMES, config.notifyVolume) + 1) % VOLUMES.length];
                    PmChatClient.playNotifySound(MinecraftClient.getInstance());
                });

        y = addOption(y, "pmchat.set.sttlang",
                () -> Text.translatable(config.sttLang == 1 ? "pmchat.set.sttlang.en" : "pmchat.set.sttlang.ru"),
                VALUE, () -> {
                    config.sttLang = config.sttLang == 1 ? 0 : 1;
                    com.pmchat.client.PmStt.onLanguageChanged();
                });

        y = addOption(y, "pmchat.set.globalprefix",
                () -> Text.literal(config.globalPrefix == null || config.globalPrefix.isBlank()
                        ? Text.translatable("pmchat.set.globalprefix.none").getString()
                        : config.globalPrefix),
                VALUE, () -> {
                    String[] cycle = {"!", "@", "."};
                    String cur = config.globalPrefix == null ? "" : config.globalPrefix;
                    int idx = -1;
                    for (int i = 0; i < cycle.length; i++) if (cycle[i].equals(cur)) idx = i;
                    if (idx < 0) config.globalPrefix = cycle[0];
                    else if (idx == cycle.length - 1) config.globalPrefix = "";
                    else config.globalPrefix = cycle[idx + 1];
                });

        y = addOption(y, "pmchat.set.tts",
                () -> Text.translatable(config.ttsGlobal ? "pmchat.set.tts.on" : "pmchat.set.tts.off"),
                () -> config.ttsGlobal ? 0xFF8FD8A8 : VALUE,
                () -> {
                    config.ttsGlobal = !config.ttsGlobal;
                    if (config.ttsGlobal) {
                        PmChatClient.speak(Text.translatable("pmchat.set.tts.preview").getString());
                    }
                });

        y = addOption(y, "pmchat.set.wallpaper",
                () -> Text.literal(config.wallpaper == null || config.wallpaper.isBlank()
                        ? Text.translatable("pmchat.set.wallpaper.none").getString()
                        : config.wallpaper.length() > 12 ? config.wallpaper.substring(0, 11) + "…" : config.wallpaper),
                VALUE, this::cycleWallpaper);

        y = addOption(y, "pmchat.set.closedmg",
                () -> Text.translatable(config.closeOnDamage ? "pmchat.set.on" : "pmchat.set.off"),
                () -> config.closeOnDamage ? 0xFFE07A6A : VALUE,
                () -> config.closeOnDamage = !config.closeOnDamage);

        y = addOption(y, "pmchat.set.copynick",
                () -> Text.translatable(config.mentionOnCopy ? "pmchat.set.on" : "pmchat.set.off"),
                () -> config.mentionOnCopy ? 0xFF8FD8A8 : VALUE,
                () -> config.mentionOnCopy = !config.mentionOnCopy);

        y = addOption(y, "pmchat.set.badge",
                () -> Text.literal("■ " + (config.badgeColor % PmPalettes.BADGE.length + 1)),
                () -> PmPalettes.BADGE[Math.floorMod(config.badgeColor, PmPalettes.BADGE.length)],
                () -> config.badgeColor = (config.badgeColor + 1) % PmPalettes.BADGE.length);

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + panelH - 24, 80, 18,
                Text.translatable("pmchat.settings.done"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> close()));
    }

    private interface ValueSupplier {
        Text get();
    }

    private interface ColorSupplier {
        int get();
    }

    private int addOption(int y, String labelKey, ValueSupplier value, int valueColor, Runnable cycle) {
        return addOption(y, labelKey, value, () -> valueColor, cycle);
    }

    /** Строка настройки: подпись + кнопка-значение, клик перебирает варианты. */
    private int addOption(int y, String labelKey, ValueSupplier value, ColorSupplier color, Runnable cycle) {
        FlatButton button = FlatButton.centered(textRenderer, px + PANEL_W - 92, y, 84, 14,
                value.get(), BTN_BG, BTN_HOVER, BTN_BORDER, color.get(), btn -> {
                    cycle.run();
                    config.save();
                    // Пересоздаём экран, чтобы обновить подписи и цвета кнопок
                    reinit();
                });
        addDrawableChild(button);
        optionLabels.add(new Object[]{labelKey, y});
        return y + ROW_H;
    }

    private final java.util.List<Object[]> optionLabels = new java.util.ArrayList<>();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, panelH, BORDER);

        Text title = Text.translatable("pmchat.settings.title");
        context.drawText(textRenderer, title,
                px + (PANEL_W - textRenderer.getWidth(title)) / 2, py + 8, TITLE, false);

        for (Object[] entry : optionLabels) {
            context.drawText(textRenderer, Text.translatable((String) entry[0]),
                    px + 10, (int) entry[1] + 3, LABEL, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void reinit() {
        init();
    }

    /** Перебирает обои: none → файлы из config/pmchat-wallpapers/. */
    private void cycleWallpaper() {
        java.util.List<String> files = new java.util.ArrayList<>();
        files.add(""); // "нет"
        try (var stream = java.nio.file.Files.list(PmScreen.wallpapersDir())) {
            stream.filter(java.nio.file.Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> {
                        String l = n.toLowerCase(java.util.Locale.ROOT);
                        return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".gif");
                    })
                    .sorted()
                    .forEach(files::add);
        } catch (Exception ignored) {
        }
        String cur = config.wallpaper == null ? "" : config.wallpaper;
        int idx = files.indexOf(cur);
        config.wallpaper = files.get((idx + 1 + files.size()) % files.size());
        PmImages.forgetLocal(config.wallpaper);
    }

    private static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) return i;
        }
        return 0;
    }

    @Override
    public void close() {
        config.save();
        MinecraftClient client = MinecraftClient.getInstance();
        // Возвращаемся в чат с уже применёнными настройками
        client.setScreen(parent instanceof PmScreen ? new PmScreen() : parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
