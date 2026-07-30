package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmServerMedia;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * «Игры» — две мини-игры на монеты Vault: подброс монетки (орёл/решка) и
 * камень-ножницы-бумага. Обе требуют серверный плагин PocketChat + Vault —
 * кнопки видны всегда (см. урок с экраном стримов), но без плагина нажатие
 * объясняет, что нужно, вместо тишины.
 */
@Environment(EnvType.CLIENT)
public class PmGamesScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int TAB_COIN = 0, TAB_RPS = 1;
    private static final String[] RPS_LABELS = {"pmchat.games.rock", "pmchat.games.paper", "pmchat.games.scissors"};

    private int BG, BORDER, LABEL, TITLE, SUBTLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();
    private final PmServerMedia sm = PmServerMedia.get();

    private int px, py, panelH;
    private int tab = TAB_COIN;
    private int lastCoinVersion = -1, lastRpsVersion = -1;
    private boolean requestedCoin = false;

    // Форма «открыть ставку»
    private TextFieldWidget coinAmountField;
    private int coinSide = 0; // 0 орёл, 1 решка

    // Форма «вызвать на КНБ»
    private TextFieldWidget rpsTargetField;
    private TextFieldWidget rpsAmountField;

    private final List<Object[]> coinAcceptRects = new ArrayList<>(); // x,y,w,h,opener

    public PmGamesScreen(Screen parent) {
        super(Text.translatable("pmchat.games.title"));
        this.parent = parent;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BG = t.bg; BORDER = t.border; LABEL = t.label; TITLE = t.title;
        SUBTLE = t.value;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    private boolean pluginPresent() {
        return sm.isAvailable();
    }

    @Override
    protected void init() {
        applyTheme();
        clearChildren();
        coinAcceptRects.clear();
        lastCoinVersion = sm.coinVersion();
        lastRpsVersion = sm.rpsVersion();

        if (pluginPresent() && !requestedCoin) {
            requestedCoin = true;
            sm.requestCoinBets();
        }

        int bets = Math.max(1, sm.coinBets().size());
        panelH = tab == TAB_COIN ? (60 + Math.min(bets, 5) * 20 + 40) : 150;
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 100, py + 24, 96, 16,
                Text.translatable("pmchat.games.coin"), tab == TAB_COIN ? BTN_HOVER : BTN_BG, BTN_HOVER, BTN_BORDER,
                tab == TAB_COIN ? 0xFFFFFFFF : VALUE, btn -> {
                    tab = TAB_COIN;
                    init();
                }));
        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 + 4, py + 24, 96, 16,
                Text.translatable("pmchat.games.rps"), tab == TAB_RPS ? BTN_HOVER : BTN_BG, BTN_HOVER, BTN_BORDER,
                tab == TAB_RPS ? 0xFFFFFFFF : VALUE, btn -> {
                    tab = TAB_RPS;
                    init();
                }));

        if (tab == TAB_COIN) initCoinTab();
        else initRpsTab();

        addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 40, py + panelH - 24, 80, 18,
                Text.translatable("pmchat.settings.done"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> close()));
    }

    private void initCoinTab() {
        boolean mine = sm.coinBets().stream()
                .anyMatch(b -> b.opener().equalsIgnoreCase(PmChatClient.selfName()));
        int fy = py + 48;
        if (mine) {
            addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 60, fy, 120, 18,
                    Text.translatable("pmchat.games.coin.cancel"),
                    0xFF5A2A22, 0xFF6E332A, 0xFFA0463A, 0xFFE07A6A, btn -> {
                        sm.coinCancel();
                        init();
                    }));
        } else {
            coinAmountField = new TextFieldWidget(textRenderer, px + 12, fy, 100, 16,
                    Text.translatable("pmchat.streams.amounthint"));
            coinAmountField.setMaxLength(12);
            addDrawableChild(coinAmountField);
            addDrawableChild(FlatButton.centered(textRenderer, px + 118, fy, 70, 16,
                    Text.translatable(coinSide == 0 ? "pmchat.games.heads" : "pmchat.games.tails"),
                    BTN_BG, BTN_HOVER, BTN_BORDER, 0xFFF0C34E, btn -> {
                        coinSide = 1 - coinSide;
                        init();
                    }));
            addDrawableChild(FlatButton.centered(textRenderer, px + 194, fy, 94, 16,
                    Text.translatable("pmchat.games.coin.open"),
                    0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> {
                        if (!pluginPresent()) {
                            init();
                            return;
                        }
                        double amount = parseAmount(coinAmountField.getText());
                        if (amount > 0) sm.coinOpen(amount, coinSide);
                        init();
                    }));
        }
    }

    private void initRpsTab() {
        int fy = py + 52;
        PmServerMedia.RpsChallenge incoming = sm.rpsIncoming();
        String opponent = sm.rpsOpponent();
        if (incoming != null) {
            addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 - 90, fy + 20, 84, 18,
                    Text.translatable("pmchat.streams.cancel"),
                    BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> {
                        sm.rpsDecline(incoming.challenger());
                        init();
                    }));
            addDrawableChild(FlatButton.centered(textRenderer, px + PANEL_W / 2 + 6, fy + 20, 84, 18,
                    Text.translatable("pmchat.games.rps.accept"),
                    0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> {
                        sm.rpsAccept(incoming.challenger());
                        init();
                    }));
        } else if (opponent != null) {
            if (!sm.rpsChoiceSent()) {
                int bw = 84, gap = 6;
                int totalW = bw * 3 + gap * 2;
                int bx = px + (PANEL_W - totalW) / 2;
                for (int i = 0; i < 3; i++) {
                    int choice = i;
                    addDrawableChild(FlatButton.centered(textRenderer, bx + i * (bw + gap), fy + 20, bw, 18,
                            Text.translatable(RPS_LABELS[i]),
                            BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> {
                                sm.rpsChoose(choice);
                                init();
                            }));
                }
            }
        } else {
            rpsTargetField = new TextFieldWidget(textRenderer, px + 12, fy, PANEL_W - 24, 16,
                    Text.translatable("pmchat.games.rps.targethint"));
            rpsTargetField.setMaxLength(24);
            addDrawableChild(rpsTargetField);
            rpsAmountField = new TextFieldWidget(textRenderer, px + 12, fy + 22, 120, 16,
                    Text.translatable("pmchat.streams.amounthint"));
            rpsAmountField.setMaxLength(12);
            addDrawableChild(rpsAmountField);
            addDrawableChild(FlatButton.centered(textRenderer, px + 138, fy + 22, 90, 16,
                    Text.translatable("pmchat.games.rps.challenge"),
                    0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> {
                        if (!pluginPresent()) {
                            init();
                            return;
                        }
                        String target = rpsTargetField.getText().trim();
                        double amount = parseAmount(rpsAmountField.getText());
                        if (!target.isEmpty() && amount > 0) sm.rpsChallenge(target, amount);
                        init();
                    }));
        }
    }

    private static double parseAmount(String s) {
        try {
            double d = Double.parseDouble(s.trim().replace(',', '.'));
            return Double.isFinite(d) ? d : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if ((sm.coinVersion() != lastCoinVersion || sm.rpsVersion() != lastRpsVersion)) {
            init();
        }

        context.fill(px + 2, py, px + PANEL_W - 2, py + panelH, BG);
        context.fill(px, py + 2, px + PANEL_W, py + panelH - 2, BG);
        context.drawStrokedRectangle(px, py, PANEL_W, panelH, BORDER);

        Text title = Text.translatable("pmchat.games.title");
        context.drawText(textRenderer, title,
                px + (PANEL_W - textRenderer.getWidth(title)) / 2, py + 9, TITLE, false);

        if (!pluginPresent()) {
            String note = Text.translatable("pmchat.games.needplugin").getString();
            context.drawText(textRenderer, trimTo(note, PANEL_W - 24),
                    px + (PANEL_W - textRenderer.getWidth(trimTo(note, PANEL_W - 24))) / 2, py + 43, SUBTLE, false);
        }

        if (tab == TAB_COIN) drawCoinTab(context, mouseX, mouseY);
        else drawRpsTab(context);

        String err = sm.lastGameErr();
        if (err != null && System.currentTimeMillis() - sm.lastGameErrAt() < 4000) {
            context.drawText(textRenderer, trimTo(err, PANEL_W - 24),
                    px + 12, py + panelH - 34, 0xFFE0574C, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawCoinTab(DrawContext context, int mouseX, int mouseY) {
        coinAcceptRects.clear();
        boolean mine = sm.coinBets().stream().anyMatch(b -> b.opener().equalsIgnoreCase(PmChatClient.selfName()));
        int listTop = py + (mine ? 72 : 70);

        PmServerMedia.CoinResult last = sm.lastCoinResult();
        if (last != null) {
            boolean won = last.winner().equalsIgnoreCase(PmChatClient.selfName());
            boolean mine2 = last.opener().equalsIgnoreCase(PmChatClient.selfName())
                    || last.accepter().equalsIgnoreCase(PmChatClient.selfName());
            if (mine2) {
                String side = Text.translatable(last.resultSide() == 0 ? "pmchat.games.heads" : "pmchat.games.tails").getString();
                String line = side + " — " + (won
                        ? Text.translatable("pmchat.games.youwon", fmt(last.amount() * 2)).getString()
                        : Text.translatable("pmchat.games.youlost", fmt(last.amount())).getString());
                context.drawText(textRenderer, line, px + (PANEL_W - textRenderer.getWidth(line)) / 2,
                        listTop, won ? 0xFF6FBF8B : 0xFFE0574C, false);
                listTop += 12;
            }
        }

        List<PmServerMedia.CoinBet> bets = sm.coinBets();
        if (bets.isEmpty()) {
            context.drawText(textRenderer, Text.translatable("pmchat.games.coin.empty"), px + 14, listTop, SUBTLE, false);
            return;
        }
        int y = listTop;
        String self = PmChatClient.selfName();
        for (PmServerMedia.CoinBet b : bets) {
            boolean self2 = b.opener().equalsIgnoreCase(self);
            String side = Text.translatable(b.side() == 0 ? "pmchat.games.heads" : "pmchat.games.tails").getString();
            String line = config.aliasOf(b.opener()) + " — " + fmt(b.amount()) + " (" + side + ")";
            context.drawText(textRenderer, trimTo(line, PANEL_W - (self2 ? 26 : 80)), px + 14, y + 4, LABEL, false);
            if (!self2) {
                int bx = px + PANEL_W - 70, by = y, bw = 56, bh = 16;
                boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
                context.fill(bx, by, bx + bw, by + bh, hov ? BTN_HOVER : BTN_BG);
                context.drawStrokedRectangle(bx, by, bw, bh, BTN_BORDER);
                String lbl = Text.translatable("pmchat.games.coin.accept").getString();
                context.drawText(textRenderer, lbl, bx + (bw - textRenderer.getWidth(lbl)) / 2, by + 4, 0xFFF0C34E, false);
                coinAcceptRects.add(new Object[]{bx, by, bw, bh, b.opener()});
            }
            y += 20;
        }
    }

    private void drawRpsTab(DrawContext context) {
        int y = py + 52;
        PmServerMedia.RpsChallenge incoming = sm.rpsIncoming();
        String opponent = sm.rpsOpponent();
        if (incoming != null) {
            String line = Text.translatable("pmchat.games.rps.challenged", config.aliasOf(incoming.challenger()),
                    fmt(incoming.amount())).getString();
            drawWrapped(context, line, y);
        } else if (opponent != null) {
            String line = Text.translatable("pmchat.games.rps.playing", config.aliasOf(opponent), fmt(sm.rpsAmount())).getString();
            drawWrapped(context, line, y);
            if (sm.rpsChoiceSent()) {
                context.drawText(textRenderer, Text.translatable("pmchat.games.rps.waiting"),
                        px + (PANEL_W - textRenderer.getWidth(Text.translatable("pmchat.games.rps.waiting"))) / 2,
                        y + 24, SUBTLE, false);
            }
        } else {
            PmServerMedia.RpsResult last = sm.lastRpsResult();
            if (last != null) {
                boolean won = last.winner().equalsIgnoreCase(PmChatClient.selfName());
                boolean tie = last.winner().isEmpty();
                String you = Text.translatable(RPS_LABELS[last.yourChoice()]).getString();
                String opp = Text.translatable(RPS_LABELS[last.oppChoice()]).getString();
                String outcome = tie ? Text.translatable("pmchat.games.rps.tie").getString()
                        : (won ? Text.translatable("pmchat.games.youwon", fmt(last.amount() * 2)).getString()
                        : Text.translatable("pmchat.games.youlost", fmt(last.amount())).getString());
                drawWrapped(context, you + " vs " + opp + " — " + outcome, y,
                        tie ? SUBTLE : (won ? 0xFF6FBF8B : 0xFFE0574C));
            } else {
                context.drawText(textRenderer, Text.translatable("pmchat.games.rps.hint"), px + 12, y - 12, SUBTLE, false);
            }
        }
    }

    private void drawWrapped(DrawContext context, String line, int y) {
        drawWrapped(context, line, y, LABEL);
    }

    private void drawWrapped(DrawContext context, String line, int y, int color) {
        context.drawText(textRenderer, trimTo(line, PANEL_W - 24),
                px + (PANEL_W - textRenderer.getWidth(trimTo(line, PANEL_W - 24))) / 2, y, color, false);
    }

    private static String fmt(double d) {
        long l = (long) d;
        return d == l ? Long.toString(l) : String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    private String trimTo(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (tab == TAB_COIN) {
            int mx = (int) click.x(), my = (int) click.y();
            for (Object[] r : coinAcceptRects) {
                int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
                if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                    sm.coinAccept((String) r[4]);
                    return true;
                }
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
