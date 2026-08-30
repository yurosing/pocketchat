package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmPhotoEdit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Правка фото перед отправкой (поворот на 90°, зеркало, рисование от руки с
 * выбором цвета и толщины пера, обрезка кадра, текст прямо на фото в любом
 * месте по клику — как в Телеграме). Открывается вместо немедленной загрузки,
 * когда игрок выбирает
 * скриншот в пикере или вставляет картинку из буфера (Ctrl+V); по кнопке
 * «Отправить» результат уходит в тот же {@link PmScreen#startUpload} без
 * изменений остального конвейера (хостинг/сервер, кэш, wire-формат).
 */
@Environment(EnvType.CLIENT)
public class PmPhotoEditScreen extends Screen {

    private static final int MAX_UNDO = 15;
    private static final float PEN_SCREEN_WIDTH_BASE = 3f;

    /** Палитра пера — цвет по умолчанию первый (тот же красный, что и раньше). */
    private static final int[] PEN_PALETTE = {
            0xFFE5433E, 0xFFE8973A, 0xFFEBD23E, 0xFF56C26A,
            0xFF3ECBD8, 0xFF3E7EE8, 0xFF9A5AE0, 0xFFE85AC0,
            0xFFFFFFFF, 0xFF1A1A1A,
    };
    /** Множители толщины пера (экранные пиксели). */
    private static final float[] PEN_WIDTHS = {2.5f, 4.5f, 7.5f, 12f};

    private final PmScreen parent;
    private final Path sourceFile;
    private final PmConfig config = PmChatClient.getConfig();

    private BufferedImage original;
    private BufferedImage current;
    private final Deque<BufferedImage> undoStack = new ArrayDeque<>();
    private boolean loadError;

    private boolean drawMode;
    private boolean cropMode;
    private boolean textMode;
    private boolean dragging;
    private float lastImgX, lastImgY;
    private int penColorIdx = 0;
    private int penWidthIdx = 1;

    // Обрезка: прямоугольник выделения в экранных координатах (x1,y1)-(x2,y2)
    private boolean cropDragging;
    private double cropX1, cropY1, cropX2, cropY2;
    private boolean cropHasSelection;

    // Текст прямо на фото: точка клика (координаты картинки), -1 — не задана
    private EditBox captionField;
    private float textAnchorX = -1f, textAnchorY = -1f;

    private Identifier textureId;
    private DynamicTexture texture;
    private NativeImage nativeImage;   // переиспользуемый буфер — пересоздаём только при смене размера
    private int texW, texH;
    private boolean textureDirty = true;
    private boolean sending;

    private int BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    public PmPhotoEditScreen(PmScreen parent, Path sourceFile) {
        super(Component.translatable("pmchat.photoedit.title"));
        this.parent = parent;
        this.sourceFile = sourceFile;
    }

    private void applyTheme() {
        PmTheme t = PmTheme.dialog(config.theme);
        BORDER = t.border; LABEL = t.label; TITLE = t.title;
        BTN_BG = t.btnBg; BTN_HOVER = t.btnHover; BTN_BORDER = t.btnBorder; VALUE = t.value;
    }

    @Override
    protected void init() {
        applyTheme();
        String captionInput = captionField != null ? captionField.getValue() : "";
        clearWidgets();

        if (current == null && !loadError) {
            try {
                current = PmPhotoEdit.load(sourceFile);
                original = PmPhotoEdit.copy(current);
                textureId = Identifier.fromNamespaceAndPath("pmchat", "photoedit/" + System.nanoTime());
            } catch (Exception e) {
                loadError = true;
            }
            textureDirty = true;
        }

        int toolW = 52, toolH = 18, gap = 4;
        String[] toolKeys = {"pmchat.photoedit.rotate", "pmchat.photoedit.mirror", "pmchat.photoedit.draw",
                "pmchat.photoedit.crop", "pmchat.photoedit.text", "pmchat.photoedit.undo", "pmchat.photoedit.reset"};
        boolean[] toolActive = {false, false, drawMode, cropMode, textMode, false, false};
        FlatButton.PressAction[] toolActions = {this::rotate, this::mirror,
                btn -> {
                    drawMode = !drawMode;
                    cropMode = false;
                    textMode = false;
                    init();
                },
                btn -> {
                    cropMode = !cropMode;
                    drawMode = false;
                    textMode = false;
                    cropHasSelection = false;
                    init();
                },
                btn -> {
                    textMode = !textMode;
                    drawMode = false;
                    cropMode = false;
                    textAnchorX = textAnchorY = -1f;
                    init();
                },
                this::undo, this::reset};

        int toolsY = height - 52;
        int totalToolsW = toolW * toolKeys.length + gap * (toolKeys.length - 1);
        int tx = (width - totalToolsW) / 2;
        for (int i = 0; i < toolKeys.length; i++) {
            addRenderableWidget(tool(tx + i * (toolW + gap), toolsY, toolW, toolH, toolKeys[i], toolActive[i], toolActions[i]));
        }

        // Палитра цвета — общая для пера и текста на фото
        if (drawMode || textMode) {
            int swSize = 14, swGap = 3;
            int rowY = toolsY - 20;
            int totalW = PEN_PALETTE.length * (swSize + swGap) - swGap;
            int sx = (width - totalW) / 2;
            for (int i = 0; i < PEN_PALETTE.length; i++) {
                int idx = i;
                int x = sx + i * (swSize + swGap);
                addRenderableWidget(swatch(x, rowY, swSize, PEN_PALETTE[i], idx == penColorIdx, () -> penColorIdx = idx));
            }
        }

        // Толщина пера — только в режиме рисования
        if (drawMode) {
            int swGap = 3;
            int wRowY = toolsY - 38;
            int wTotalW = PEN_WIDTHS.length * (36 + swGap) - swGap;
            int wx = (width - wTotalW) / 2;
            for (int i = 0; i < PEN_WIDTHS.length; i++) {
                int idx = i;
                addRenderableWidget(sizeButton(wx + i * (36 + swGap), wRowY, 36, 16, i, idx == penWidthIdx,
                        () -> penWidthIdx = idx));
            }
        }

        // Подтверждение обрезки — появляется, когда выделен прямоугольник
        if (cropMode && cropHasSelection) {
            int by = toolsY - 22;
            addRenderableWidget(FlatButton.centered(font, width / 2 - 90, by, 84, 18,
                    Component.translatable("pmchat.photoedit.applycrop"),
                    0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> applyCrop()));
            addRenderableWidget(FlatButton.centered(font, width / 2 + 6, by, 84, 18,
                    Component.translatable("pmchat.photoedit.cancel"),
                    0xFF5A2A22, 0xFF6E332A, 0xFFA0463A, 0xFFE07A6A, btn -> {
                        cropHasSelection = false;
                        init();
                    }));
        }

        // Текст прямо на фото: кликните по фото, наберите текст, выберите цвет выше
        if (textMode) {
            int fy = toolsY - 42;
            captionField = new EditBox(font, width / 2 - 140, fy, 220, 16,
                    Component.translatable("pmchat.photoedit.captionhint"));
            captionField.setMaxLength(200);
            captionField.setValue(captionInput);
            captionField.setSuggestion(captionInput.isEmpty()
                    ? Component.translatable("pmchat.photoedit.captionhint").getString() : "");
            addRenderableWidget(captionField);
            addRenderableWidget(FlatButton.centered(font, width / 2 + 86, fy - 1, 60, 18,
                    Component.translatable("pmchat.photoedit.applytext"),
                    0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> applyCaption()));
        }

        int actW = 90, actH = 20, actGap = 12;
        int actY = height - 26;
        int ax = width / 2 - actW - actGap / 2;
        addRenderableWidget(FlatButton.centered(font, ax, actY, actW, actH,
                Component.translatable("pmchat.photoedit.cancel"),
                0xFF5A2A22, 0xFF6E332A, 0xFFA0463A, 0xFFE07A6A, btn -> onClose()));
        addRenderableWidget(FlatButton.centered(font, width / 2 + actGap / 2, actY, actW, actH,
                Component.translatable("pmchat.photoedit.send"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> doSend()));
    }

    private FlatButton tool(int x, int y, int w, int h, String key, boolean active, FlatButton.PressAction action) {
        return FlatButton.centered(font, x, y, w, h, Component.translatable(key),
                active ? BTN_HOVER : BTN_BG, BTN_HOVER, active ? VALUE : BTN_BORDER,
                active ? 0xFFFFFFFF : LABEL, action);
    }

    private FlatButton swatch(int x, int y, int size, int color, boolean active, Runnable pick) {
        return FlatButton.centered(font, x, y, size, size, Component.literal(active ? "●" : ""),
                color, color, active ? 0xFFFFFFFF : BTN_BORDER, 0xFFFFFFFF, btn -> {
                    pick.run();
                    init();
                });
    }

    private FlatButton sizeButton(int x, int y, int w, int h, int i, boolean active, Runnable pick) {
        String dot = "●".repeat(i + 1);
        return FlatButton.centered(font, x, y, w, h, Component.literal(dot),
                active ? BTN_HOVER : BTN_BG, BTN_HOVER, active ? VALUE : BTN_BORDER,
                active ? 0xFFFFFFFF : LABEL, btn -> {
                    pick.run();
                    init();
                });
    }

    // ---------- операции ----------

    private void pushUndo() {
        if (current == null) return;
        undoStack.push(PmPhotoEdit.copy(current));
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
    }

    private void rotate(FlatButton btn) {
        if (current == null) return;
        pushUndo();
        current = PmPhotoEdit.rotate90(current);
        textureDirty = true;
    }

    private void mirror(FlatButton btn) {
        if (current == null) return;
        pushUndo();
        current = PmPhotoEdit.flipHorizontal(current);
        textureDirty = true;
    }

    private void undo(FlatButton btn) {
        if (undoStack.isEmpty()) return;
        current = undoStack.pop();
        textureDirty = true;
    }

    private void reset(FlatButton btn) {
        if (original == null) return;
        pushUndo();
        current = PmPhotoEdit.copy(original);
        textureDirty = true;
    }

    private void applyCrop() {
        if (current == null || !cropHasSelection) return;
        int[] r = imageRect();
        if (r == null) return;
        double x1 = Math.min(cropX1, cropX2), x2 = Math.max(cropX1, cropX2);
        double y1 = Math.min(cropY1, cropY2), y2 = Math.max(cropY1, cropY2);
        float[] p1 = toImageSpace(x1, y1, r);
        float[] p2 = toImageSpace(x2, y2, r);
        int cx = Math.round(Math.min(p1[0], p2[0]));
        int cy = Math.round(Math.min(p1[1], p2[1]));
        int cw = Math.round(Math.abs(p2[0] - p1[0]));
        int ch = Math.round(Math.abs(p2[1] - p1[1]));
        if (cw < 4 || ch < 4) {
            cropHasSelection = false;
            init();
            return;
        }
        pushUndo();
        current = PmPhotoEdit.crop(current, cx, cy, cw, ch);
        textureDirty = true;
        cropHasSelection = false;
        cropMode = false;
        init();
    }

    private void applyCaption() {
        if (current == null || captionField == null) return;
        String text = captionField.getValue().trim();
        if (text.isEmpty()) return;
        // Без клика по фото — по умолчанию ставим текст в верхний левый угол.
        float ax = textAnchorX >= 0 ? textAnchorX : current.getWidth() * 0.05f;
        float ay = textAnchorY >= 0 ? textAnchorY : current.getHeight() * 0.05f;
        pushUndo();
        int color = PEN_PALETTE[Math.floorMod(penColorIdx, PEN_PALETTE.length)];
        PmPhotoEdit.stampText(current, ax, ay, text, color);
        textureDirty = true;
        textMode = false;
        textAnchorX = textAnchorY = -1f;
        init();
    }

    private void doSend() {
        if (current == null || sending) return;
        sending = true;
        try {
            Path out = PmPhotoEdit.saveTemp(current);
            parent.startUpload(out);
        } catch (Exception ignored) {
            // если сохранить не удалось — просто закрываем без отправки
        }
        onClose();
    }

    // ---------- превью / текстура ----------

    /** Область показа: под шапкой, над панелью инструментов. */
    private int[] imageRect() {
        if (current == null) return null;
        // Важно: граница не должна зависеть от cropHasSelection — иначе картинка
        // сдвинется/пересчитает масштаб ПРЯМО ВО ВРЕМЯ протяжки (клик уже
        // выставляет cropHasSelection=true), и координаты выделения разъедутся
        // с тем, что покажет imageRect() в момент применения обрезки.
        int top = 24;
        int bottom = height - (drawMode ? 114 : (textMode ? 120 : (cropMode ? 84 : 60)));
        int areaW = Math.max(1, width - 40), areaH = Math.max(1, bottom - top);
        float scale = Math.min(areaW / (float) current.getWidth(), areaH / (float) current.getHeight());
        scale = Math.min(scale, 4f);
        int w = Math.max(1, Math.round(current.getWidth() * scale));
        int h = Math.max(1, Math.round(current.getHeight() * scale));
        int x = (width - w) / 2;
        int y = top + (areaH - h) / 2;
        return new int[]{x, y, w, h};
    }

    private float[] toImageSpace(double screenX, double screenY, int[] r) {
        float ix = (float) ((screenX - r[0]) / (double) r[2] * current.getWidth());
        float iy = (float) ((screenY - r[1]) / (double) r[3] * current.getHeight());
        return new float[]{ix, iy};
    }

    private float[] toScreenSpace(float imgX, float imgY, int[] r) {
        float sx = r[0] + imgX / current.getWidth() * r[2];
        float sy = r[1] + imgY / current.getHeight() * r[3];
        return new float[]{sx, sy};
    }

    private float penWidthInImageSpace(int[] r) {
        if (r == null || current == null) return 6f;
        float scale = current.getWidth() / (float) r[2];
        float screenWidth = PEN_WIDTHS[Math.floorMod(penWidthIdx, PEN_WIDTHS.length)];
        return Math.max(2f, screenWidth * scale);
    }

    /**
     * Заливает текущий кадр в текстуру НАПРЯМУЮ (без PNG-кодирования/декодирования
     * на каждый кадр рисования — раньше это душило FPS во время протяжки пером).
     * Буфер и GL-текстуру пересоздаём только при смене размера картинки (поворот,
     * обрезка); во время рисования просто перезаписываем те же пиксели и зовём
     * upload() — тот же приём, что и в {@code PmVlc} для видео-кадров.
     */
    private void refreshTexture() {
        if (current == null) return;
        try {
            int w = current.getWidth(), h = current.getHeight();
            if (nativeImage == null || texW != w || texH != h) {
                NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
                DynamicTexture tex = new DynamicTexture(() -> "pmchat-photoedit", img);
                Minecraft.getInstance().getTextureManager().register(textureId, tex);
                DynamicTexture old = texture;
                texture = tex;
                nativeImage = img;
                texW = w;
                texH = h;
                if (old != null) {
                    try {
                        old.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            int[] pixels = current.getRGB(0, 0, w, h, null, 0, w);
            int i = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    nativeImage.setPixel(x, y, pixels[i++]);
                }
            }
            texture.upload();
        } catch (Exception ignored) {
        }
        textureDirty = false;
    }

    // ---------- ввод ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int[] r = imageRect();
        if (r != null && click.x() >= r[0] && click.x() < r[0] + r[2]
                && click.y() >= r[1] && click.y() < r[1] + r[3]) {
            if (drawMode && current != null) {
                pushUndo();
                float[] p = toImageSpace(click.x(), click.y(), r);
                lastImgX = p[0];
                lastImgY = p[1];
                float w = penWidthInImageSpace(r);
                int color = PEN_PALETTE[Math.floorMod(penColorIdx, PEN_PALETTE.length)];
                PmPhotoEdit.drawSegment(current, lastImgX, lastImgY, lastImgX + 0.01f, lastImgY, color, w);
                dragging = true;
                textureDirty = true;
                return true;
            }
            if (cropMode) {
                cropDragging = true;
                cropHasSelection = true;
                cropX1 = cropX2 = click.x();
                cropY1 = cropY2 = click.y();
                return true;
            }
            if (textMode) {
                float[] p = toImageSpace(click.x(), click.y(), r);
                textAnchorX = p[0];
                textAnchorY = p[1];
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (dragging && drawMode && current != null) {
            int[] r = imageRect();
            if (r != null) {
                float[] p = toImageSpace(click.x(), click.y(), r);
                int color = PEN_PALETTE[Math.floorMod(penColorIdx, PEN_PALETTE.length)];
                PmPhotoEdit.drawSegment(current, lastImgX, lastImgY, p[0], p[1], color, penWidthInImageSpace(r));
                lastImgX = p[0];
                lastImgY = p[1];
                textureDirty = true;
            }
            return true;
        }
        if (cropDragging && cropMode) {
            cropX2 = click.x();
            cropY2 = click.y();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        dragging = false;
        if (cropDragging) {
            cropDragging = false;
            // Слишком маленькое выделение (случайный клик без протяжки) — не
            // показываем «Применить» на невидимом прямоугольнике: тогда клик по
            // нему ничего бы не обрезал и выглядело бы как «ничего не работает».
            if (Math.abs(cropX2 - cropX1) < 6 || Math.abs(cropY2 - cropY1) < 6) {
                cropHasSelection = false;
            }
            init(); // показать кнопки «Применить/Отмена», если выделение достаточное
        }
        return super.mouseReleased(click);
    }

    // ---------- рендер ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE6000000);

        String titleStr = Component.translatable("pmchat.photoedit.title").getString();
        context.text(font, titleStr, width / 2 - font.width(titleStr) / 2, 8,
                TITLE, false);

        if (loadError) {
            String err = Component.translatable("pmchat.photoedit.error").getString();
            context.text(font, err, width / 2 - font.width(err) / 2, height / 2,
                    0xFFE07A6A, false);
            super.extractRenderState(context, mouseX, mouseY, delta);
            return;
        }

        if (current != null) {
            if (textureDirty || texture == null) refreshTexture();
            int[] r = imageRect();
            if (texture != null && r != null) {
                context.blit(RenderPipelines.GUI_TEXTURED, textureId, r[0], r[1],
                        0f, 0f, r[2], r[3], current.getWidth(), current.getHeight(),
                        current.getWidth(), current.getHeight());
                context.outline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, BORDER);
            }
            if (drawMode) {
                String hint = Component.translatable("pmchat.photoedit.drawhint").getString();
                context.text(font, hint, width / 2 - font.width(hint) / 2,
                        height - 92, LABEL, false);
            }
            if (cropMode && (cropDragging || cropHasSelection)) {
                int x1 = (int) Math.min(cropX1, cropX2), x2 = (int) Math.max(cropX1, cropX2);
                int y1 = (int) Math.min(cropY1, cropY2), y2 = (int) Math.max(cropY1, cropY2);
                context.fill(x1, y1, x2, y2, 0x33FFFFFF);
                context.outline(x1, y1, x2 - x1, y2 - y1, 0xFFFFFFFF);
            } else if (cropMode) {
                String hint = Component.translatable("pmchat.photoedit.crophint").getString();
                context.text(font, hint, width / 2 - font.width(hint) / 2,
                        height - 92, LABEL, false);
            }
            if (textMode && r != null) {
                if (textAnchorX >= 0) {
                    float[] s = toScreenSpace(textAnchorX, textAnchorY, r);
                    int color = PEN_PALETTE[Math.floorMod(penColorIdx, PEN_PALETTE.length)];
                    context.text(font, "+", (int) s[0] - 2, (int) s[1] - 4, color, false);
                    String preview = captionField != null ? captionField.getValue() : "";
                    if (!preview.isBlank()) {
                        context.text(font, preview, (int) s[0] + 6, (int) s[1] - 4, color, true);
                    }
                } else {
                    String hint = Component.translatable("pmchat.photoedit.texthint").getString();
                    context.text(font, hint, width / 2 - font.width(hint) / 2,
                            height - 108, LABEL, false);
                }
            }
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void removed() {
        if (textureId != null) {
            Minecraft.getInstance().getTextureManager().release(textureId);
        }
        super.removed();
    }
}
