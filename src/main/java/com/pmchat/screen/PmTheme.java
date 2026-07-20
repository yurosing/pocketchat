package com.pmchat.screen;

/**
 * Общая палитра вторичных окон мода (настройки, фильтры, медиа) — редизайн
 * «Cool»: Dark (0), Light (1), Slate (2). Главное окно (PmScreen) держит свою
 * расширенную палитру, здесь — компактный набор ролей для диалоговых экранов.
 */
public final class PmTheme {

    private PmTheme() {
    }

    // Роли цвета для диалоговых окон
    public int bg, border, title, label, btnBg, btnHover, btnBorder, value;

    /** Палитра диалоговых окон под выбранную тему. */
    public static PmTheme dialog(int theme) {
        PmTheme t = new PmTheme();
        if (theme == 1) {
            // Cool Light (1A)
            t.bg = 0xFFF2F6F9;
            t.border = 0xFFCDD5DB;
            t.title = 0xFF19232A;
            t.label = 0xFF3F5A6E;
            t.btnBg = 0xFFE7ECF0;
            t.btnHover = 0xFFDDE4E9;
            t.btnBorder = 0xFFCDD5DB;
            t.value = 0xFF19232A;
        } else if (theme == 2) {
            // Cool Slate (1C)
            t.bg = 0xFFB9C6D0;
            t.border = 0xFFA0ADB6;
            t.title = 0xFF0E171E;
            t.label = 0xFF32424E;
            t.btnBg = 0xFFC2CCD3;
            t.btnHover = 0xFFCFD8DE;
            t.btnBorder = 0xFFA7B3BC;
            t.value = 0xFF0E171E;
        } else {
            // Cool Dark (1B)
            t.bg = 0xFF141C21;
            t.border = 0xFF0B1116;
            t.title = 0xFFDFE6EB;
            t.label = 0xFF9FB6C4;
            t.btnBg = 0xFF20282D;
            t.btnHover = 0xFF1A2227;
            t.btnBorder = 0xFF343C42;
            t.value = 0xFFDFE6EB;
        }
        return t;
    }
}
