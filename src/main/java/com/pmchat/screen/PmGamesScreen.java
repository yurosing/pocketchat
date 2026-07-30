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

    // Анимация подброса монетки/раскрытия КНБ: показывается один раз на свежий
    // результат, пришедший, пока экран уже открыт (см. конструктор ниже).
    private static final long COIN_ANIM_MS = 900;
    private static final long RPS_ANIM_MS = 900;
    private PmServerMedia.CoinResult shownCoinResult;
    private long coinAnimStart = -1;
    private PmServerMedia.RpsResult shownRpsResult;
    private long rpsAnimStart = -1;

    public PmGamesScreen(Screen parent) {
        super(Text.translatable("pmchat.games.title"));
        this.parent = parent;
        // Не проигрываем анимацию для результата, случившегося ещё до открытия
        // экрана — только для того, что придёт, пока игрок смотрит на экран.
        this.shownCoinResult = PmServerMedia.get().lastCoinResult();
        this.shownRpsResult = PmServerMedia.get().lastRpsResult();
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
        boolean coinResultShown = isOwnCoinResult(sm.lastCoinResult());
        panelH = tab == TAB_COIN ? (60 + Math.min(bets, 5) * 20 + 40 + (coinResultShown ? 34 : 0)) : 170;
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

    private boolean isOwnCoinResult(PmServerMedia.CoinResult r) {
        if (r == null) return false;
        String self = PmChatClient.selfName();
        return r.opener().equalsIgnoreCase(self) || r.accepter().equalsIgnoreCase(self);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if ((sm.coinVersion() != lastCoinVersion || sm.rpsVersion() != lastRpsVersion)) {
            init();
        }

        PmServerMedia.CoinResult cr = sm.lastCoinResult();
        if (cr != shownCoinResult) {
            shownCoinResult = cr;
            coinAnimStart = System.currentTimeMillis();
            init(); // свежий результат мог изменить нужную высоту панели (см. panelH)
        }
        PmServerMedia.RpsResult rr = sm.lastRpsResult();
        if (rr != shownRpsResult) {
            shownRpsResult = rr;
            rpsAnimStart = System.currentTimeMillis();
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
        if (isOwnCoinResult(last)) {
            boolean won = last.winner().equalsIgnoreCase(PmChatClient.selfName());
            drawCoinFlipAnim(context, px + PANEL_W / 2, listTop + 10);
            long elapsed = System.currentTimeMillis() - coinAnimStart;
            if (elapsed >= COIN_ANIM_MS) {
                String side = Text.translatable(last.resultSide() == 0 ? "pmchat.games.heads" : "pmchat.games.tails").getString();
                String line = side + " — " + (won
                        ? Text.translatable("pmchat.games.youwon", fmt(last.amount() * 2)).getString()
                        : Text.translatable("pmchat.games.youlost", fmt(last.amount())).getString());
                context.drawText(textRenderer, line, px + (PANEL_W - textRenderer.getWidth(line)) / 2,
                        listTop + 22, won ? 0xFF6FBF8B : 0xFFE0574C, false);
            }
            listTop += 34;
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
                drawRpsRevealAnim(context, y + 22, last);
            }
        }
    }

    /** Подброс монетки: монета «крутится» (пульсирует по ширине, меняя грань),
     *  затем останавливается на выпавшей стороне — вызывается каждый кадр,
     *  скорость гасится по мере приближения к концу COIN_ANIM_MS. */
    private void drawCoinFlipAnim(DrawContext context, int cx, int cy) {
        long elapsed = System.currentTimeMillis() - coinAnimStart;
        boolean spinning = coinAnimStart >= 0 && elapsed < COIN_ANIM_MS;
        int resultSide = shownCoinResult != null ? shownCoinResult.resultSide() : 0;
        double t = spinning ? elapsed / (double) COIN_ANIM_MS : 1.0;
        int face = spinning ? (int) ((elapsed / 70) % 2) : resultSide;
        double phase = elapsed / 55.0 * (1.25 - 0.85 * t);
        int halfW = spinning ? Math.max(2, (int) (9 * Math.abs(Math.cos(phase)))) : 9;
        int r = 9;
        int color = face == 0 ? 0xFFF0C34E : 0xFFC8D2DC;
        int edge = face == 0 ? 0xFFB8892E : 0xFF8B96A0;
        context.fill(cx - halfW, cy - r, cx + halfW, cy + r, color);
        context.drawStrokedRectangle(cx - halfW, cy - r, Math.max(1, halfW * 2), r * 2, edge);
        if (halfW > 5) {
            String glyph = Text.translatable(face == 0 ? "pmchat.games.heads" : "pmchat.games.tails")
                    .getString().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            context.drawText(textRenderer, glyph, cx - textRenderer.getWidth(glyph) / 2, cy - 4, 0xFF2A2010, false);
        }
    }

    /** «Камень-ножницы-бумага-раз»: обе руки быстро перебирают ходы вразнобой,
     *  затем останавливаются на настоящих выборах игроков и показывают исход. */
    private void drawRpsRevealAnim(DrawContext context, int y, PmServerMedia.RpsResult last) {
        long elapsed = System.currentTimeMillis() - rpsAnimStart;
        boolean spinning = rpsAnimStart >= 0 && elapsed < RPS_ANIM_MS;
        int youChoice = spinning ? (int) ((elapsed / 90) % 3) : last.yourChoice();
        int oppChoice = spinning ? (int) ((elapsed / 110 + 2) % 3) : last.oppChoice();
        int cx = px + PANEL_W / 2, gap = 42;
        drawHandIcon(context, cx - gap, y, youChoice);
        drawHandIcon(context, cx + gap, y, oppChoice);
        String vs = "vs";
        context.drawText(textRenderer, vs, cx - textRenderer.getWidth(vs) / 2, y - 4, SUBTLE, false);

        if (!spinning) {
            boolean won = last.winner().equalsIgnoreCase(PmChatClient.selfName());
            boolean tie = last.winner().isEmpty();
            String outcome = tie ? Text.translatable("pmchat.games.rps.tie").getString()
                    : (won ? Text.translatable("pmchat.games.youwon", fmt(last.amount() * 2)).getString()
                    : Text.translatable("pmchat.games.youlost", fmt(last.amount())).getString());
            drawWrapped(context, outcome, y + 26, tie ? SUBTLE : (won ? 0xFF6FBF8B : 0xFFE0574C));
        }
    }

    private void drawHandIcon(DrawContext context, int cx, int y, int choice) {
        String[] bmp = choice == 0 ? PmIcons.ROCK : choice == 1 ? PmIcons.PAPER : PmIcons.SCISSORS;
        PmIcons.draw(context, bmp, cx - 9, y - 9, 18, 18, VALUE);
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
