package com.pmchat.screen;

import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmConfig;
import com.pmchat.client.PmPhotoEdit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Правка фото перед отправкой (поворот на 90°, зеркало, рисование от руки —
 * «обвести» что-нибудь красным). Открывается вместо немедленной загрузки, когда
 * игрок выбирает скриншот в пикере или вставляет картинку из буфера (Ctrl+V);
 * по кнопке «Отправить» результат уходит в тот же {@link PmScreen#startUpload}
 * без изменений остального конвейера (хостинг/сервер, кэш, wire-формат).
 */
@Environment(EnvType.CLIENT)
public class PmPhotoEditScreen extends Screen {

    private static final int MAX_UNDO = 15;
    /** Толщина пера в пикселях экрана — переводится в масштаб картинки на лету. */
    private static final float PEN_SCREEN_WIDTH = 5f;
    private static final int PEN_COLOR = 0xFFE5433E; // непрозрачно-красный

    private final PmScreen parent;
    private final Path sourceFile;
    private final PmConfig config = PmChatClient.getConfig();

    private BufferedImage original;
    private BufferedImage current;
    private final Deque<BufferedImage> undoStack = new ArrayDeque<>();
    private boolean loadError;

    private boolean drawMode;
    private boolean dragging;
    private float lastImgX, lastImgY;

    private Identifier textureId;
    private NativeImageBackedTexture texture;
    private boolean textureDirty = true;
    private boolean sending;

    private int BORDER, LABEL, TITLE, BTN_BG, BTN_HOVER, BTN_BORDER, VALUE;

    public PmPhotoEditScreen(PmScreen parent, Path sourceFile) {
        super(Text.translatable("pmchat.photoedit.title"));
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
        clearChildren();

        if (current == null && !loadError) {
            try {
                current = PmPhotoEdit.load(sourceFile);
                original = PmPhotoEdit.copy(current);
                textureId = Identifier.of("pmchat", "photoedit/" + System.nanoTime());
            } catch (Exception e) {
                loadError = true;
            }
            textureDirty = true;
        }

        int toolW = 74, toolH = 18, gap = 6;
        int toolsY = height - 52;
        int totalToolsW = toolW * 5 + gap * 4;
        int tx = (width - totalToolsW) / 2;

        addDrawableChild(tool(tx, toolsY, toolW, toolH, "pmchat.photoedit.rotate", false, this::rotate));
        addDrawableChild(tool(tx + (toolW + gap), toolsY, toolW, toolH, "pmchat.photoedit.mirror", false, this::mirror));
        addDrawableChild(tool(tx + (toolW + gap) * 2, toolsY, toolW, toolH, "pmchat.photoedit.draw", drawMode,
                btn -> {
                    drawMode = !drawMode;
                    init();
                }));
        addDrawableChild(tool(tx + (toolW + gap) * 3, toolsY, toolW, toolH, "pmchat.photoedit.undo", false, this::undo));
        addDrawableChild(tool(tx + (toolW + gap) * 4, toolsY, toolW, toolH, "pmchat.photoedit.reset", false, this::reset));

        int actW = 90, actH = 20, actGap = 12;
        int actY = height - 26;
        int ax = width / 2 - actW - actGap / 2;
        addDrawableChild(FlatButton.centered(textRenderer, ax, actY, actW, actH,
                Text.translatable("pmchat.photoedit.cancel"),
                0xFF5A2A22, 0xFF6E332A, 0xFFA0463A, 0xFFE07A6A, btn -> close()));
        addDrawableChild(FlatButton.centered(textRenderer, width / 2 + actGap / 2, actY, actW, actH,
                Text.translatable("pmchat.photoedit.send"),
                0xFF2E5F46, 0xFF376F52, 0xFF4C8A66, 0xFFCFEEDA, btn -> doSend()));
    }

    private FlatButton tool(int x, int y, int w, int h, String key, boolean active, FlatButton.PressAction action) {
        return FlatButton.centered(textRenderer, x, y, w, h, Text.translatable(key),
                active ? BTN_HOVER : BTN_BG, BTN_HOVER, active ? VALUE : BTN_BORDER,
                active ? 0xFFFFFFFF : LABEL, action);
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

    private void doSend() {
        if (current == null || sending) return;
        sending = true;
        try {
            Path out = PmPhotoEdit.saveTemp(current);
            parent.startUpload(out);
        } catch (Exception ignored) {
            // если сохранить не удалось — просто закрываем без отправки
        }
        close();
    }

    // ---------- превью / текстура ----------

    /** Область показа: под шапкой, над панелью инструментов. */
    private int[] imageRect() {
        if (current == null) return null;
        int top = 24, bottom = height - 60;
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

    private float penWidthInImageSpace(int[] r) {
        if (r == null || current == null) return 6f;
        float scale = current.getWidth() / (float) r[2];
        return Math.max(2f, PEN_SCREEN_WIDTH * scale);
    }

    private void refreshTexture() {
        if (current == null) return;
        try {
            byte[] png = PmPhotoEdit.toPngBytes(current);
            NativeImage img = NativeImage.read(png);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "pmchat-photoedit", img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, tex);
            NativeImageBackedTexture old = texture;
            texture = tex;
            if (old != null) {
                try {
                    old.close();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        textureDirty = false;
    }

    // ---------- ввод ----------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (drawMode && current != null) {
            int[] r = imageRect();
            if (r != null && click.x() >= r[0] && click.x() < r[0] + r[2]
                    && click.y() >= r[1] && click.y() < r[1] + r[3]) {
                pushUndo();
                float[] p = toImageSpace(click.x(), click.y(), r);
                lastImgX = p[0];
                lastImgY = p[1];
                // одиночный клик тоже должен оставить точку, а не только протяжка
                float w = penWidthInImageSpace(r);
                PmPhotoEdit.drawSegment(current, lastImgX, lastImgY, lastImgX + 0.01f, lastImgY, PEN_COLOR, w);
                dragging = true;
                textureDirty = true;
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging && drawMode && current != null) {
            int[] r = imageRect();
            if (r != null) {
                float[] p = toImageSpace(click.x(), click.y(), r);
                PmPhotoEdit.drawSegment(current, lastImgX, lastImgY, p[0], p[1], PEN_COLOR, penWidthInImageSpace(r));
                lastImgX = p[0];
                lastImgY = p[1];
                textureDirty = true;
            }
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    // ---------- рендер ----------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE6000000);

        String titleStr = Text.translatable("pmchat.photoedit.title").getString();
        context.drawText(textRenderer, titleStr, width / 2 - textRenderer.getWidth(titleStr) / 2, 8,
                TITLE, false);

        if (loadError) {
            String err = Text.translatable("pmchat.photoedit.error").getString();
            context.drawText(textRenderer, err, width / 2 - textRenderer.getWidth(err) / 2, height / 2,
                    0xFFE07A6A, false);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        if (current != null) {
            if (textureDirty || texture == null) refreshTexture();
            int[] r = imageRect();
            if (texture != null && r != null) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId, r[0], r[1],
                        0f, 0f, r[2], r[3], current.getWidth(), current.getHeight(),
                        current.getWidth(), current.getHeight());
                context.drawStrokedRectangle(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, BORDER);
            }
            if (drawMode) {
                String hint = Text.translatable("pmchat.photoedit.drawhint").getString();
                context.drawText(textRenderer, hint, width / 2 - textRenderer.getWidth(hint) / 2,
                        height - 74, LABEL, false);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        if (textureId != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
        }
        super.removed();
    }
}
