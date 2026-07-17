package com.pmchat.client;

import com.pmchat.screen.PmIcons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * NEW (5.3): персистентный медиаплеер. Владеет текущим воспроизведением
 * (видео с YouTube или плейлист локальных mp3) НЕЗАВИСИМО от экрана чата —
 * поэтому музыка/видео продолжают играть и рисоваться в углу, даже когда
 * мессенджер закрыт. Управление — через встроенное окошко (когда открыт
 * какой-то экран) или горячие клавиши и меню {@code PmMediaScreen}.
 *
 * Рисование самого кадра/окошка вынесено сюда, чтобы одинаково работать и
 * поверх HUD (когда экран закрыт), и внутри {@link com.pmchat.screen.PmScreen}.
 */
public final class PmMedia {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("pmchat-media");
    private static final PmMedia INSTANCE = new PmMedia();

    public static PmMedia get() {
        return INSTANCE;
    }

    /** Поддерживаемые расширения плейлистов. */
    private static final List<String> AUDIO_EXT = Arrays.asList(".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac");

    private PmVlc.Session session;
    private String title = "";
    private String sourceUrl;            // для видео — исходная ссылка (кнопка «в браузере»)
    private boolean music;               // true — играет плейлист mp3
    private boolean minimized;           // окошко свёрнуто (видео); музыка всегда «в окошке»
    private int volume = 100;

    // Плейлист
    private final List<File> playlist = new ArrayList<>();
    private int trackIndex = -1;
    private String playlistName = "";

    // Временные файлы (видео с YouTube) — удалить при остановке
    private File videoFile, audioFile;

    // Прямоугольники окошка (для кликов); [x,y,w,h]
    private int[] winRect, closeRect, expandRect, prevRect, playRect, nextRect;

    private PmMedia() {
    }

    // ---------- запуск ----------

    /** Запустить видео (готовый VLC-сеанс от yt-dlp). Вызывать на клиентском потоке. */
    public void startVideo(PmVlc.Session s, File videoFile, File audioFile, String sourceUrl, String title) {
        stop();
        this.session = s;
        this.videoFile = videoFile;
        this.audioFile = audioFile;
        this.sourceUrl = sourceUrl;
        this.title = title != null ? title : "";
        this.music = false;
        this.minimized = false;
        applyVolume();
    }

    /**
     * Собрать плейлист из папки (все аудиофайлы, по алфавиту) и начать играть.
     * Возвращает число найденных треков (0 — папка пуста/не аудио).
     */
    public int startMusicFolder(File folder) {
        List<File> found = new ArrayList<>();
        collectAudio(folder, found, 0);
        found.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        if (found.isEmpty()) return 0;
        stop();
        playlist.addAll(found);
        playlistName = folder.getName();
        music = true;
        minimized = true;
        playIndex(0);
        return found.size();
    }

    /** Играть плейлист из готового списка файлов (напр. один выбранный трек и его соседи). */
    public void startMusicList(List<File> files, int startIndex, String name) {
        if (files == null || files.isEmpty()) return;
        stop();
        playlist.addAll(files);
        playlistName = name != null ? name : "";
        music = true;
        minimized = true;
        playIndex(Math.max(0, Math.min(startIndex, files.size() - 1)));
    }

    private void collectAudio(File dir, List<File> out, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 3) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectAudio(f, out, depth + 1);
            } else if (isAudio(f)) {
                out.add(f);
            }
        }
    }

    public static boolean isAudio(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        for (String e : AUDIO_EXT) if (n.endsWith(e)) return true;
        return false;
    }

    private void playIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;
        // ВАЖНО: инициализирует фабрику VLC. Без этого вызова factory == null
        // и открытие сессии падает NPE — музыка «молча» не играла.
        if (!PmVlc.isAvailable()) {
            LOGGER.warn("Cannot play music: VLC not available");
            return;
        }
        releaseSession();
        trackIndex = index;
        File track = playlist.get(index);
        title = stripExt(track.getName());
        try {
            // file-URI (ASCII, percent-encoded) вместо плоского пути — надёжно
            // для кириллицы/пробелов/скобок при системной кодировке Cp1251.
            // ВАЖНО: именно тройной слэш file:///C:/... — Java's toURI() даёт
            // file:/C:/... (один слэш), а VLC принимает это за относительный путь.
            session = PmVlc.open("file://" + track.toURI().getRawPath());
            applyVolume();
            LOGGER.info("Playing track {}/{}: {}", index + 1, playlist.size(), track.getName());
        } catch (Exception e) {
            LOGGER.warn("Failed to play track {}: {}", track.getAbsolutePath(), e.toString());
            session = null;
        }
    }

    // ---------- управление ----------

    public void togglePause() {
        if (session != null) session.togglePause();
    }

    public void next() {
        if (!music || playlist.isEmpty()) return;
        playIndex((trackIndex + 1) % playlist.size());
    }

    public void prev() {
        if (!music || playlist.isEmpty()) return;
        // В первые 3 секунды — предыдущий трек, иначе в начало текущего
        if (session != null && session.timeMs() > 3000) {
            session.seekFraction(0f);
        } else {
            playIndex((trackIndex - 1 + playlist.size()) % playlist.size());
        }
    }

    public void setVolume(int percent) {
        volume = Math.max(0, Math.min(150, percent));
        applyVolume();
    }

    public int getVolume() {
        return volume;
    }

    private void applyVolume() {
        if (session != null) session.setVolume(volume);
    }

    public void setMinimized(boolean m) {
        minimized = m;
    }

    public boolean isMinimized() {
        return minimized;
    }

    /** Авто-переход к следующему треку, когда текущий доиграл. Звать каждый клиентский тик. */
    public void tick() {
        // Только для музыки: доиграл трек — включаем следующий. Видео по концу
        // оставляем как есть (окно с последним кадром, юзер закроет сам).
        if (music && session != null && session.isFinished() && !playlist.isEmpty()) {
            next();
        }
    }

    public void stop() {
        releaseSession();
        // Временные файлы видео освобождаются VLC не сразу — чуть погодя.
        final File vf = videoFile, af = audioFile;
        videoFile = null;
        audioFile = null;
        if (vf != null || af != null) {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                PmYtDlp.cleanup(vf);
                PmYtDlp.cleanup(af);
            }, "pmchat-media-cleanup");
            t.setDaemon(true);
            t.start();
        }
        playlist.clear();
        trackIndex = -1;
        playlistName = "";
        title = "";
        sourceUrl = null;
        music = false;
        minimized = false;
    }

    private void releaseSession() {
        if (session != null) {
            try {
                session.release();
            } catch (Exception ignored) {
            }
            session = null;
        }
    }

    // ---------- геттеры ----------

    public boolean hasActive() {
        return session != null;
    }

    public PmVlc.Session session() {
        return session;
    }

    public boolean isMusic() {
        return music;
    }

    public String title() {
        return title;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public String playlistName() {
        return playlistName;
    }

    public int trackIndex() {
        return trackIndex;
    }

    public int playlistSize() {
        return playlist.size();
    }

    // ---------- окошко в углу ----------

    /**
     * Рисует свёрнутое окошко в правом нижнем углу. {@code interactive} — реагировать
     * на наведение (внутри экрана) или просто показать (поверх HUD). Возвращает
     * прямоугольник окна, чтобы вызывающий знал занятую область.
     */
    public int[] renderMini(DrawContext ctx, int mouseX, int mouseY, boolean interactive) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (session != null) session.tick();

        int mw = 232, mediaH = music ? 0 : 122, barH = 34;
        int mh = (music ? 46 : mediaH) + barH;
        int x0 = sw - mw - 8;
        int y0 = sh - mh - 8;
        winRect = new int[]{x0, y0, mw, mh};

        // Фон + рамка
        ctx.fill(x0 - 2, y0 - 2, x0 + mw + 2, y0 + mh + 2, 0xF00B120F);
        ctx.drawStrokedRectangle(x0 - 2, y0 - 2, mw + 4, mh + 4, 0xFF2E5C48);

        if (music) {
            renderMusicArt(ctx, tr, x0, y0, mw, 46);
        } else {
            renderVideoArt(ctx, tr, x0, y0, mw, mediaH);
        }

        renderControlBar(ctx, tr, x0, y0 + (music ? 46 : mediaH), mw, barH, mouseX, mouseY, interactive);

        // Кнопки развернуть/закрыть (правый верх)
        int b = 15;
        int cx = x0 + mw - b - 3, cy = y0 + 3;
        int ex = cx - b - 3;
        closeRect = new int[]{cx, cy, b, b};
        expandRect = music ? null : new int[]{ex, cy, b, b};
        boolean hovC = interactive && inRect(mouseX, mouseY, closeRect);
        ctx.fill(cx, cy, cx + b, cy + b, hovC ? 0xE06E2A22 : 0x99101A16);
        PmIcons.draw(ctx, PmIcons.CLEAR, cx, cy, b, b, hovC ? 0xFFE07A6A : 0xFFCFE0DA);
        if (expandRect != null) {
            boolean hovE = interactive && inRect(mouseX, mouseY, expandRect);
            ctx.fill(ex, cy, ex + b, cy + b, hovE ? 0xE02A4A5C : 0x99101A16);
            PmIcons.draw(ctx, PmIcons.EXPAND, ex, cy, b, b, hovE ? 0xFFEDF3F0 : 0xFFCFE0DA);
        }
        return winRect;
    }

    private void renderVideoArt(DrawContext ctx, TextRenderer tr, int x0, int y0, int mw, int h) {
        PmVlc.Session s = session;
        if (s != null && s.width() > 0 && s.height() > 0) {
            int vw = s.width(), vh = s.height();
            float scale = Math.min((float) mw / vw, (float) h / vh);
            int w = Math.max(1, Math.round(vw * scale));
            int hh = Math.max(1, Math.round(vh * scale));
            int ix = x0 + (mw - w) / 2, iy = y0 + (h - hh) / 2;
            ctx.fill(x0, y0, x0 + mw, y0 + h, 0xFF050907);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, s.textureId(), ix, iy, 0f, 0f, w, hh, vw, vh, vw, vh);
        } else {
            ctx.fill(x0, y0, x0 + mw, y0 + h, 0xFF0B120F);
            Text st = Text.translatable("pmchat.video.decoding");
            ctx.drawText(tr, st, x0 + (mw - tr.getWidth(st)) / 2, y0 + h / 2 - 4, 0xFFB8C6CE, false);
        }
        if (s != null && !s.isPlaying()) {
            PmIcons.draw(ctx, PmIcons.PLAY, x0 + mw / 2 - 8, y0 + h / 2 - 8, 16, 16, 0xCCEDF3F0);
        }
    }

    private void renderMusicArt(DrawContext ctx, TextRenderer tr, int x0, int y0, int mw, int h) {
        ctx.fillGradient(x0, y0, x0 + mw, y0 + h, 0xFF14322A, 0xFF0B120F);
        // «Обложка» — квадрат с ноткой и мягкими эквалайзер-полосками
        int art = h - 10;
        int ax = x0 + 6, ay = y0 + 5;
        ctx.fill(ax, ay, ax + art, ay + art, 0xFF1C3D33);
        ctx.drawStrokedRectangle(ax, ay, art, art, 0xFF2E5C48);
        PmIcons.draw(ctx, PmIcons.NOTE, ax, ay, art, art, 0xFF8FD8A8);
        // Бегущие полоски-эквалайзер (декоративно, если играет)
        boolean playing = session != null && session.isPlaying();
        long t = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            int bx = ax + art + 6 + i * 5;
            int bh = playing ? (int) (4 + (Math.sin(t / 180.0 + i) + 1) * 5) : 4;
            ctx.fill(bx, ay + art - bh, bx + 3, ay + art, 0xFF4C8A66);
        }
        // Название трека + «плейлист (n/m)»
        int tx = ax + art + 30;
        String name = trim(tr, title, mw - (tx - x0) - 8);
        ctx.drawText(tr, name, tx, y0 + 8, 0xFFEDF3F0, false);
        String sub = playlistName + "  " + (trackIndex + 1) + "/" + playlist.size();
        ctx.drawText(tr, trim(tr, sub, mw - (tx - x0) - 8), tx, y0 + 22, 0xFF7FA694, false);
    }

    private void renderControlBar(DrawContext ctx, TextRenderer tr, int x0, int y0, int mw, int h,
                                  int mouseX, int mouseY, boolean interactive) {
        ctx.fill(x0, y0, x0 + mw, y0 + h, 0xFF101A16);
        PmVlc.Session s = session;

        // Прогресс-полоска сверху бара
        float pos = s != null ? Math.max(0f, Math.min(1f, s.positionFraction())) : 0f;
        ctx.fill(x0, y0, x0 + mw, y0 + 2, 0xFF23352E);
        ctx.fill(x0, y0, x0 + Math.round(mw * pos), y0 + 2, 0xFF6FBF8B);

        int by = y0 + 6, bsz = 20;
        int cxc = x0 + mw / 2;
        // prev / play / next по центру (prev/next только для музыки)
        playRect = new int[]{cxc - bsz / 2, by, bsz, bsz};
        prevRect = music ? new int[]{cxc - bsz / 2 - 6 - bsz, by, bsz, bsz} : null;
        nextRect = music ? new int[]{cxc + bsz / 2 + 6, by, bsz, bsz} : null;

        drawBtn(ctx, prevRect, PmIcons.PREV, mouseX, mouseY, interactive);
        boolean playing = s != null && s.isPlaying();
        drawBtnIcon(ctx, playRect, playing ? PmIcons.PAUSE : PmIcons.PLAY, mouseX, mouseY, interactive, 0xFFEDF3F0);
        drawBtn(ctx, nextRect, PmIcons.NEXT, mouseX, mouseY, interactive);

        // Время справа
        if (s != null) {
            String time = fmt(s.timeMs()) + "/" + fmt(s.lengthMs());
            ctx.drawText(tr, time, x0 + mw - tr.getWidth(time) - 6, by + 6, 0xFF7FA694, false);
        }
        // Подсказка про клавиши слева (только поверх HUD)
        if (!interactive) {
            ctx.drawText(tr, "▶", x0 + 6, by + 6, playing ? 0xFF6FBF8B : 0xFF7FA694, false);
        }
    }

    private void drawBtn(DrawContext ctx, int[] r, String[] icon, int mx, int my, boolean interactive) {
        if (r == null) return;
        drawBtnIcon(ctx, r, icon, mx, my, interactive, 0xFFCFE0DA);
    }

    private void drawBtnIcon(DrawContext ctx, int[] r, String[] icon, int mx, int my, boolean interactive, int col) {
        if (r == null) return;
        boolean hov = interactive && inRect(mx, my, r);
        ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hov ? 0xFF2A4A5C : 0xFF16241E);
        PmIcons.draw(ctx, icon, r[0], r[1], r[2], r[3], hov ? 0xFFEDF3F0 : col);
    }

    /**
     * Клик по свёрнутому окошку. Возвращает {@code true}, если попал по окну
     * (обработали), {@code false} — мимо (клик отдать дальше).
     */
    public boolean handleMiniClick(int mx, int my) {
        if (inRect(mx, my, closeRect)) {
            stop();
            return true;
        }
        if (inRect(mx, my, expandRect)) {
            minimized = false;
            return true;
        }
        if (inRect(mx, my, prevRect)) {
            prev();
            return true;
        }
        if (inRect(mx, my, nextRect)) {
            next();
            return true;
        }
        if (inRect(mx, my, playRect)) {
            togglePause();
            return true;
        }
        return inRect(mx, my, winRect); // клик по телу окна — поглощаем, но ничего
    }

    // ---------- утилиты ----------

    private static boolean inRect(int mx, int my, int[] r) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String trim(TextRenderer tr, String s, int maxW) {
        if (tr.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && tr.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static String fmt(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        return String.format("%d:%02d", total / 60, total % 60);
    }

    /** Папка с музыкой пользователя: config/pmchat-music. */
    public static File musicDir() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "config/pmchat-music");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }
}
