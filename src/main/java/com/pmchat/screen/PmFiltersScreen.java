package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран «Фильтры чата» («No Global Chat»): в стиле настроек PocketChat.
 * Позволяет отключить глобальный чат и Discord целиком, игнорировать
 * отдельных игроков (в чате и в Discord) и прятать сообщения по тексту
 * с выбором области (глобал/Discord/везде).
 */
@Environment(EnvType.CLIENT)
public class PmFiltersScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int ROW_H = 17;

    private static final int BG = 0xFF1C3644;
    private static final int BORDER = 0xFF10222C;
    private static final int LABEL = 0xFF9CC4DC;
    private static final int TITLE = 0xFFF2F6F8;
    private static final int SECTION = 0xFF8FD8A8;
    private static final int BTN_BG = 0xFF15303D;
    private static final int BTN_HOVER = 0xFF0F2833;
    private static final int BTN_BORDER = 0xFF2A4A5C;
    private static final int VALUE = 0xFFEDF3F0;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int px, py, panelH;

    // Тексты полей ввода сохраняем между reinit, чтобы не терять при добавлении/удалении.
    private String playerInput = "";
    private String discordInput = "";
    private String textInput = "";
    private int textScope = PmConfig.SCOPE_BOTH;

    private TextFieldWidget playerField, discordField, textField;

    /** Метки для render(): {текст, x, y, цвет}. */
    private final List<Object[]> labels = new ArrayList<>();

    public PmFiltersScreen(Screen parent) {
        super(Text.translatable("pmchat.filters.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Сохраняем введённое перед пересборкой
        if (playerField != null) playerInput = playerField.getText();
        if (discordField != null) discordInput = discordField.getText();
        if (textField != null) textInput = textField.getText();

        clearChildren();
        labels.clear();

        // Высота панели зависит от длины списков
        int rows = 2                                  // два тумблера
                + 2 + config.filterPlayers.size()      // заголовок+поле+строки
                + 2 + config.filterDiscordPlayers.size()
                + 2 + config.filterRules.size();
        panelH = 30 + rows * ROW_H + 30;
        panelH = Math.min(panelH, height - 20);
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        int y = py + 26;

        // --- Тумблеры ---
        y = addToggle(y, "pmchat.filters.global", config.filterGlobal,
                () -> config.filterGlobal = !config.filterGlobal);
        y = addToggle(y, "pmchat.filters.discord", config.filterDiscord,
                () -> config.filterDiscord = !config.filterDiscord);

        // --- Игнор игроков (чат) ---
        y = section(y, "pmchat.filters.players");
        y = addInputRow(y, playerField = makeField(y, playerInput, "pmchat.filters.nick"),
                () -> {
                    config.addFilteredPlayer(playerField.getText());
                    playerInput = "";
                    reinit();
                });
        for (int i = 0; i < config.filterPlayers.size(); i++) {
            final int idx = i;
            y = addEntryRow(y, config.filterPlayers.get(i), null, () -> {
                config.filterPlayers.remove(idx);
                config.save();
                reinit();
            });
        }

        // --- Игнор Discord-игроков ---
        y = section(y, "pmchat.filters.discordplayers");
        y = addInputRow(y, discordField = makeField(y, discordInput, "pmchat.filters.nick"),
                () -> {
                    config.addFilteredDiscordPlayer(discordField.getText());
                    discordInput = "";
                    reinit();
                });
        for (int i = 0; i < config.filterDiscordPlayers.size(); i++) {
            final int idx = i;
            y = addEntryRow(y, config.filterDiscordPlayers.get(i), null, () -> {
                config.filterDiscordPlayers.remove(idx);
                config.save();
                reinit();
            });
        }

        // --- Текстовые фильтры ---
        y = section(y, "pmchat.filters.text");
        textField = makeField(y, textInput, "pmchat.filters.texthint");
        // поле уже, чтобы влезла кнопка области
        textField.setWidth(PANEL_W - 132);
        addDrawableChild(textField);
        // Кнопка выбора области
        FlatButton scopeBtn = FlatButton.centered(textRenderer, px + PANEL_W - 118, y, 54, 16,
                Text.translatable(scopeKey(textScope)), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> {
                    textInput = textField.getText();
                    textScope = (textScope + 1) % 3;
                    reinit();
                });
        addDrawableChild(scopeBtn);
        // Кнопка «+»
        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W - 60, y, 52, 16,
                Text.translatable("pmchat.filters.add"), 0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> {
                    String t = textField.getText().trim();
                    if (!t.isEmpty()) {
                        config.filterRules.add(new PmConfig.FilterRule(t, textScope));
                        config.save();
                    }
                    textInput = "";
                    reinit();
                }));
        y += ROW_H;
        for (int i = 0; i < config.filterRules.size(); i++) {
            final int idx = i;
            PmConfig.FilterRule r = config.filterRules.get(i);
            String suffix = " (" + Text.translatable(scopeKey(r.scope)).getString() + ")";
            y = addEntryRow(y, r.text, suffix, () -> {
                config.filterRules.remove(idx);
                config.save();
                reinit();
            });
        }

        // Готово
        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + panelH - 24, 80, 18,
                Text.translatable("pmchat.settings.done"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> close()));
    }

    private static String scopeKey(int scope) {
        return switch (scope) {
            case PmConfig.SCOPE_GLOBAL -> "pmchat.filters.scope.global";
            case PmConfig.SCOPE_DISCORD -> "pmchat.filters.scope.discord";
            default -> "pmchat.filters.scope.both";
        };
    }

    private TextFieldWidget makeField(int y, String text, String hintKey) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, px + 10, y, PANEL_W - 72, 16,
                Text.translatable(hintKey));
        f.setMaxLength(64);
        f.setText(text);
        String hint = Text.translatable(hintKey).getString();
        f.setSuggestion(text.isEmpty() ? hint : "");
        f.setChangedListener(s -> f.setSuggestion(s.isEmpty() ? hint : ""));
        return f;
    }

    /** Строка с полем ввода и кнопкой «+». */
    private int addInputRow(int y, TextFieldWidget field, Runnable add) {
        addDrawableChild(field);
        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W - 60, y, 52, 16,
                Text.translatable("pmchat.filters.add"), 0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA,
                btn -> add.run()));
        return y + ROW_H;
    }

    /** Строка списка: значение + кнопка «✕». */
    private int addEntryRow(int y, String value, String suffix, Runnable remove) {
        labels.add(new Object[]{"• " + value + (suffix == null ? "" : suffix), px + 14, y + 3, VALUE});
        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W - 26, y, 18, 14,
                Text.literal("✕"), 0xFF3A1E1E, 0xFF522626, 0xFF6E2A22, 0xFFE07A6A, btn -> remove.run()));
        return y + ROW_H;
    }

    private int addToggle(int y, String labelKey, boolean on, Runnable toggle) {
        labels.add(new Object[]{Text.translatable(labelKey).getString(), px + 10, y + 3, LABEL});
        FlatButton button = FlatButton.centered(textRenderer, px + PANEL_W - 92, y, 84, 14,
                Text.translatable(on ? "pmchat.set.on" : "pmchat.set.off"),
                BTN_BG, BTN_HOVER, BTN_BORDER, on ? 0xFF8FD8A8 : VALUE, btn -> {
                    toggle.run();
                    config.save();
                    reinit();
                });
        addDrawableChild(button);
        return y + ROW_H;
    }

    private int section(int y, String key) {
        labels.add(new Object[]{Text.translatable(key).getString(), px + 10, y + 4, SECTION});
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
        super.render(context, mouseX, mouseY, delta);
    }

    private void reinit() {
        init();
    }

    @Override
    public void close() {
        config.save();
        PmChatClient.reloadPatterns();
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
