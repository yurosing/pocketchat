package com.pmchat.client;

/** Общие палитры интерфейса для экрана чата и экрана настроек. */
public final class PmPalettes {

    /** Цвета своих сообщений. */
    public static final int[] OUT = {
            0xFF2E5F46, 0xFF2E4F6E, 0xFF5A3E7A, 0xFF7A452E,
            0xFF6E2E3E, 0xFF2E6E66, 0xFF3A4750, 0xFF7A2E5A
    };
    public static final String[] OUT_NAMES = {
            "green", "blue", "purple", "orange", "red", "teal", "graphite", "pink"
    };

    /** Цвета входящих сообщений (есть тёмные — не режут глаза ночью). */
    public static final int[] IN = {
            0xFFF2F2F2, 0xFFDDDDDD, 0xFF2E3A42, 0xFF26323E,
            0xFF24352B, 0xFFEFE6D4, 0xFF2E2A3E, 0xFF1A1F24
    };
    public static final String[] IN_NAMES = {
            "white", "light-gray", "dark-gray", "slate", "dark-green", "cream", "dark-purple", "black"
    };

    /** Цвета ников (радуга по хэшу или единый выбранный). */
    public static final int[] NAMES = {
            0xFFE8A0A0, 0xFFA0D8E8, 0xFFB8E8A0, 0xFFE8D8A0,
            0xFFD0A8E8, 0xFFA0E8C8, 0xFFEDF3F0, 0xFFF0C34E
    };

    /** Цвета бейджа непрочитанных. */
    public static final int[] BADGE = {
            0xFF4C8A66, 0xFFC0453A, 0xFF3A6FB0, 0xFFC08A2D, 0xFF7A5AB0, 0xFF5A6A74
    };

    private PmPalettes() {
    }

    /** Читаемый цвет текста поверх данного фона (по яркости). */
    public static int textOn(int bg) {
        int r = (bg >> 16) & 0xFF;
        int g = (bg >> 8) & 0xFF;
        int b = bg & 0xFF;
        double luma = 0.299 * r + 0.587 * g + 0.114 * b;
        return luma < 128 ? 0xFFE8EEF2 : 0xFF222222;
    }
}
