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
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Магазин готовых ботов-файлов: игрок присылает свой файл бота на рассмотрение
 * (взнос — см. {@code /v1/botstore/price}, задаёт админ), очередь смотрит
 * админ по порядку (см. PmAdminScreen), одобренное появляется в открытом рынке
 * — там его можно скачать бесплатно или купить, если автор поставил цену.
 */
@Environment(EnvType.CLIENT)
public class PmBotStoreScreen extends Screen {

    private static final int TAB_MARKET = 0, TAB_SUBMIT = 1, TAB_MINE = 2;

    private final Screen parent;
    private final PmConfig config = PmChatClient.getConfig();

    private int BG, BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, ACCENT;
    private int px, py, pw, ph;
    private int tab = TAB_MARKET;
    private final int[] tabRects = new int[3];
    private final int[] tabW = new int[3];
    private int[] closeRect;

    // Рынок
    private EditBox marketSearchField;
    private String marketSearchText = "";
    private List<PmBackend.BotListing> market = null;
    private int scroll = 0;

    // Заявка
    private EditBox nameField;
    private EditBox descField;
    private EditBox priceField;
    private String nameText = "", descText = "", priceText = "0";
    private File selectedFile;
    private long submitPrice = -1;

    // Мои заявки
    private List<PmBackend.BotListing> mine = null;

    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    public PmBotStoreScreen(Screen parent) {
        super(Component.translatable("pmchat.botstore.title"));
        this.parent = parent;
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
        pw = Math.min(360, width - 24);
        ph = Math.min(300, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        if (market == null) loadMarket();
        if (mine == null) loadMine();
        if (submitPrice < 0) {
            PmBackend.getBotstoreSubmitPrice((ok, price, err) -> {
                submitPrice = ok && price != null ? price : 0;
                if (minecraft != null) layout();
            });
        }
        layout();
    }

    private void loadMarket() {
        PmBackend.botstoreMarket(marketSearchText, (ok, list, err) -> {
            market = ok && list != null ? list : new ArrayList<>();
            if (minecraft != null) layout();
        });
    }

    private void loadMine() {
        PmBackend.myBotListings((ok, list, err) -> {
            mine = ok && list != null ? list : new ArrayList<>();
            if (minecraft != null) layout();
        });
    }

    private static File submissionsDir() {
        File dir = new File(Minecraft.getInstance().gameDirectory, "config/pmchat-bot-submissions");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private void layout() {
        if (marketSearchField != null) marketSearchText = marketSearchField.getValue();
        if (nameField != null) nameText = nameField.getValue();
        if (descField != null) descText = descField.getValue();
        if (priceField != null) priceText = priceField.getValue();
        clearWidgets();

        int fx = px + 12;
        int fw = pw - 24;
        int ty = py + 24;

        String[] labels = {
                Component.translatable("pmchat.botstore.tab.market").getString(),
                Component.translatable("pmchat.botstore.tab.submit").getString(),
                Component.translatable("pmchat.botstore.tab.mine").getString(),
        };
        int tx = px + 10;
        for (int i = 0; i < labels.length; i++) {
            int w = font.width(labels[i]) + 14;
            tabW[i] = w;
            tabRects[i] = tx;
            tx += w + 4;
        }

        int contentTop = ty + 20;

        if (tab == TAB_MARKET) {
            marketSearchField = new EditBox(font, fx, contentTop, fw, 15,
                    Component.translatable("pmchat.botstore.search"));
            marketSearchField.setMaxLength(64);
            marketSearchField.setValue(marketSearchText);
            marketSearchField.setResponder(s -> {
                marketSearchText = s;
                loadMarket();
            });
            addRenderableWidget(marketSearchField);

            int listTop = contentTop + 19;
            int listBottom = py + ph - 8;
            int rowH = 32;
            if (market != null) {
                int y = listTop - scroll;
                for (PmBackend.BotListing l : market) {
                    if (y + rowH >= listTop && y <= listBottom) {
                        int btnW = 56;
                        boolean owned = l.owner != null && l.owner.equalsIgnoreCase(PmChatClient.selfNamePublic());
                        Component btnLabel = owned ? Component.translatable("pmchat.botstore.download")
                                : (l.price > 0 ? Component.translatable("pmchat.botstore.buy", l.price)
                                : Component.translatable("pmchat.botstore.getfree"));
                        long id = l.id;
                        addRenderableWidget(FlatButton.centered(font, fx + fw - btnW, y + 16, btnW, 14,
                                btnLabel, 0xFF244A33, 0xFF2E5C40, 0xFF4C8A66, 0xFFCFEEDA,
                                btn -> buyListing(id)));
                    }
                    y += rowH;
                }
            }
        } else if (tab == TAB_SUBMIT) {
            int y = contentTop;
            nameField = new EditBox(font, fx, y, fw, 15, Component.translatable("pmchat.botstore.name"));
            nameField.setMaxLength(64);
            nameField.setValue(nameText);
            addRenderableWidget(nameField);
            y += 19;

            descField = new EditBox(font, fx, y, fw, 15, Component.translatable("pmchat.botstore.desc"));
            descField.setMaxLength(200);
            descField.setValue(descText);
            addRenderableWidget(descField);
            y += 19;

            priceField = new EditBox(font, fx, y, 90, 15, Component.translatable("pmchat.botstore.price"));
            priceField.setMaxLength(9);
            priceField.setValue(priceText);
            addRenderableWidget(priceField);
            addRenderableWidget(FlatButton.centered(font, fx + 94, y, fw - 94, 15,
                    Component.translatable("pmchat.botstore.openfolder"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE,
                    btn -> Util.getPlatform().openFile(submissionsDir())));
            y += 22;

            // Список файлов из папки config/pmchat-bot-submissions — выбираем клик по строке.
            // Нижняя граница оставляет место под строки взноса/выбранного файла и кнопку «Отправить».
            int filesTop = y;
            int filesBottom = py + ph - 66;
            File[] files = submissionsDir().listFiles();
            if (files != null) {
                java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                int fy = filesTop;
                for (File f : files) {
                    if (f.isDirectory()) continue;
                    if (fy + 14 > filesBottom) break;
                    boolean sel = f.equals(selectedFile);
                    int fyFinal = fy;
                    addRenderableWidget(FlatButton.centered(font, fx, fyFinal, fw, 13,
                            Component.literal((sel ? "✔ " : "") + f.getName()), BTN_BG,
                            sel ? BTN_HOVER : BTN_HOVER, BTN_BORDER, sel ? ACCENT : VALUE,
                            btn -> selectedFile = f));
                    fy += 15;
                }
            }

            addRenderableWidget(FlatButton.centered(font, fx, py + ph - 40, fw, 15,
                    Component.translatable("pmchat.botstore.submit"), 0xFF244A33, 0xFF2E5C40, 0xFF4C8A66, 0xFFCFEEDA,
                    btn -> submit()));
        }

        addRenderableWidget(FlatButton.centered(font, px + pw / 2 - 40, py + ph - 20, 80, 15,
                Component.translatable("pmchat.settings.done"), BTN_BG, BTN_HOVER, BTN_BORDER, VALUE, btn -> onClose()));

        closeRect = new int[]{px + pw - 18, py + 5, 14, 14};
    }

    private void buyListing(long listingId) {
        PmBackend.buyBotListing(listingId, (ok, fileId, err) -> {
            if (ok && fileId != null) {
                status = Component.translatable("pmchat.botstore.downloading");
                statusColor = ACCENT;
                try {
                    Util.getPlatform().openUri(PmBackend.botFileUrl(fileId));
                } catch (Exception ignored) {
                }
            } else if (err != null && err.contains("insufficient balance")) {
                status = Component.translatable("pmchat.botstore.needcoins");
                statusColor = 0xFFE07A6A;
            } else {
                status = Component.translatable("pmchat.botstore.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
            }
        });
    }

    private void submit() {
        String name = nameField.getValue().trim();
        String desc = descField.getValue().trim();
        long price;
        try {
            price = Math.max(0, Long.parseLong(priceField.getValue().trim()));
        } catch (NumberFormatException e) {
            price = 0;
        }
        if (name.isEmpty()) {
            status = Component.translatable("pmchat.botstore.needname");
            statusColor = 0xFFE07A6A;
            return;
        }
        if (selectedFile == null) {
            status = Component.translatable("pmchat.botstore.needfile");
            statusColor = 0xFFE07A6A;
            return;
        }
        status = Component.translatable("pmchat.botstore.uploading");
        statusColor = LABEL;
        String finalName = name, finalDesc = desc;
        long finalPrice = price;
        PmBackend.uploadBotFile(selectedFile.toPath(), (ok, fileId, err) -> {
            if (!ok || fileId == null) {
                status = Component.translatable("pmchat.botstore.fail", String.valueOf(err));
                statusColor = 0xFFE07A6A;
                return;
            }
            PmBackend.submitBotListing(finalName, finalDesc, fileId, finalPrice, (ok2, id, err2) -> {
                if (ok2) {
                    status = Component.translatable("pmchat.botstore.submitted");
                    statusColor = 0xFF8FD8A8;
                    nameField.setValue(""); descField.setValue(""); priceField.setValue("0");
                    nameText = ""; descText = ""; priceText = "0";
                    selectedFile = null;
                    loadMine();
                } else if (err2 != null && err2.contains("insufficient balance")) {
                    status = Component.translatable("pmchat.botstore.needcoins");
                    statusColor = 0xFFE07A6A;
                } else {
                    status = Component.translatable("pmchat.botstore.fail", String.valueOf(err2));
                    statusColor = 0xFFE07A6A;
                }
            });
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xC0000000);
        ctx.fill(px, py, px + pw, py + ph, BG);
        ctx.outline(px, py, pw, ph, BORDER);

        Component title = getTitle();
        ctx.text(font, title, px + 12, py + 8, TITLE, false);
        String x = "✕";
        boolean hovX = closeRect != null && mouseX >= closeRect[0] && mouseX < closeRect[0] + closeRect[2]
                && mouseY >= closeRect[1] && mouseY < closeRect[1] + closeRect[3];
        ctx.text(font, x, px + pw - 15, py + 8, hovX ? TITLE : LABEL, false);

        String[] labels = {
                Component.translatable("pmchat.botstore.tab.market").getString(),
                Component.translatable("pmchat.botstore.tab.submit").getString(),
                Component.translatable("pmchat.botstore.tab.mine").getString(),
        };
        int ty = py + 24;
        for (int i = 0; i < labels.length; i++) {
            boolean sel = tab == i;
            boolean hov = mouseY >= ty && mouseY < ty + 16 && mouseX >= tabRects[i] && mouseX < tabRects[i] + tabW[i];
            ctx.fill(tabRects[i], ty, tabRects[i] + tabW[i], ty + 16, sel ? BTN_HOVER : (hov ? BTN_BG : BG));
            ctx.outline(tabRects[i], ty, tabW[i], 16, sel ? ACCENT : BORDER);
            ctx.text(font, labels[i], tabRects[i] + 7, ty + 4, sel ? TITLE : LABEL, false);
        }

        int fx = px + 12;
        int fw = pw - 24;
        int contentTop = ty + 20;

        if (tab == TAB_MARKET) {
            int listTop = contentTop + 19;
            int listBottom = py + ph - 8;
            ctx.enableScissor(px + 1, listTop, px + pw - 1, listBottom);
            if (market == null) {
                ctx.text(font, Component.translatable("pmchat.bots.loading"), fx, listTop + 4, LABEL, false);
            } else if (market.isEmpty()) {
                ctx.text(font, Component.translatable("pmchat.botstore.empty"), fx, listTop + 4, LABEL, false);
            } else {
                int rowH = 32;
                int y = listTop - scroll;
                for (PmBackend.BotListing l : market) {
                    if (y + rowH >= listTop && y <= listBottom) {
                        ctx.fill(fx, y, fx + fw, y + rowH - 2, BTN_BG);
                        String head = l.name + "  ·  @" + l.owner;
                        ctx.text(font, trim(head, fw - 60), fx + 4, y + 3, VALUE, false);
                        String priceStr = l.price > 0
                                ? Component.translatable("pmchat.botstore.pricetag", l.price).getString()
                                : Component.translatable("pmchat.botstore.freetag").getString();
                        String subLine = priceStr + (l.description != null && !l.description.isBlank()
                                ? "  ·  " + l.description : "");
                        ctx.text(font, trim(subLine, fw - 60), fx + 4, y + 16, LABEL, false);
                    }
                    y += rowH;
                }
            }
            ctx.disableScissor();
        } else if (tab == TAB_SUBMIT) {
            if (submitPrice > 0) {
                ctx.text(font, Component.translatable("pmchat.botstore.feeline", submitPrice),
                        fx, py + ph - 62, LABEL, false);
            }
            if (selectedFile != null) {
                ctx.text(font, Component.translatable("pmchat.botstore.selected", selectedFile.getName()),
                        fx, py + ph - 52, LABEL, false);
            }
        } else {
            int listTop = contentTop;
            int bottom = py + ph - 8;
            if (mine == null) {
                ctx.text(font, Component.translatable("pmchat.bots.loading"), fx, listTop, LABEL, false);
            } else if (mine.isEmpty()) {
                ctx.text(font, Component.translatable("pmchat.botstore.noneofmine"), fx, listTop, LABEL, false);
            } else {
                int rowH = 26;
                int y = listTop;
                for (PmBackend.BotListing l : mine) {
                    if (y + rowH > bottom) break;
                    ctx.text(font, trim(l.name, fw - 70), fx, y, VALUE, false);
                    int statColor = switch (l.status) {
                        case "approved" -> 0xFF8FD8A8;
                        case "rejected" -> 0xFFE07A6A;
                        default -> 0xFFF0C34E;
                    };
                    String statLabel = Component.translatable("pmchat.botstore.status." + l.status).getString();
                    ctx.text(font, statLabel, fx + fw - font.width(statLabel), y, statColor, false);
                    y += rowH;
                }
            }
        }

        if (!status.getString().isEmpty()) {
            ctx.text(font, status, fx, py + ph - 38, statusColor, false);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (closeRect != null && mx >= closeRect[0] && mx < closeRect[0] + closeRect[2]
                && my >= closeRect[1] && my < closeRect[1] + closeRect[3]) {
            onClose();
            return true;
        }
        int ty = py + 24;
        for (int i = 0; i < tabW.length; i++) {
            if (my >= ty && my < ty + 16 && mx >= tabRects[i] && mx < tabRects[i] + tabW[i]) {
                if (tab != i) {
                    tab = i;
                    scroll = 0;
                    status = Component.empty();
                    layout();
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (tab == TAB_MARKET) {
            scroll = Math.max(0, scroll - (int) Math.signum(v) * 24);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, h, v);
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
    public boolean isPauseScreen() {
        return false;
    }
}
