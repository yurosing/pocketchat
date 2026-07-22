package com.pmchat.screen;

/**
 * Общая палитра вторичных окон мода (настройки, фильтры, медиа, профиль).
 * Тем стало больше (3.8): тёмные и светлые. Главное окно (PmScreen) держит свою
 * расширенную палитру, здесь — компактный набор ролей для диалоговых экранов.
 */
public final class PmTheme {

    private PmTheme() {
    }

    /** Всего тем интерфейса. Индекс config.theme приводится по модулю к этому числу. */
    public static final int COUNT = 7;

    /** Ключи локализации названий тем (совпадают по индексу с config.theme). */
    public static final String[] NAME_KEYS = {
            "pmchat.set.theme.dark",     // 0
            "pmchat.set.theme.light",    // 1
            "pmchat.set.theme.slate",    // 2
            "pmchat.set.theme.midnight", // 3
            "pmchat.set.theme.nord",     // 4
            "pmchat.set.theme.rose",     // 5
            "pmchat.set.theme.sand",     // 6
    };

    /** Светлая ли тема (для выбора направления теней/оверлеев). */
    public static boolean isLight(int theme) {
        int t = Math.floorMod(theme, COUNT);
        return t != 0 && t != 3 && t != 4;
    }

    public static String nameKey(int theme) {
        return NAME_KEYS[Math.floorMod(theme, COUNT)];
    }

    // Роли цвета для диалоговых окон
    public int bg, border, title, label, btnBg, btnHover, btnBorder, value;

    /** Палитра диалоговых окон под выбранную тему. */
    public static PmTheme dialog(int theme) {
        PmTheme t = new PmTheme();
        switch (Math.floorMod(theme, COUNT)) {
            case 1 -> { // Cool Light
                t.bg = 0xFFF2F6F9; t.border = 0xFFCDD5DB; t.title = 0xFF19232A; t.label = 0xFF3F5A6E;
                t.btnBg = 0xFFE7ECF0; t.btnHover = 0xFFDDE4E9; t.btnBorder = 0xFFCDD5DB; t.value = 0xFF19232A;
            }
            case 2 -> { // Cool Slate
                t.bg = 0xFFB9C6D0; t.border = 0xFFA0ADB6; t.title = 0xFF0E171E; t.label = 0xFF32424E;
                t.btnBg = 0xFFC2CCD3; t.btnHover = 0xFFCFD8DE; t.btnBorder = 0xFFA7B3BC; t.value = 0xFF0E171E;
            }
            case 3 -> { // Midnight (тёмная индиго)
                t.bg = 0xFF15131F; t.border = 0xFF0A0813; t.title = 0xFFE6E1F2; t.label = 0xFFB0A8D0;
                t.btnBg = 0xFF221F33; t.btnHover = 0xFF1B1828; t.btnBorder = 0xFF373250; t.value = 0xFFE6E1F2;
            }
            case 4 -> { // Nord (тёмная сине-серая)
                t.bg = 0xFF2E3440; t.border = 0xFF21262E; t.title = 0xFFECEFF4; t.label = 0xFF88C0D0;
                t.btnBg = 0xFF3B4252; t.btnHover = 0xFF434C5E; t.btnBorder = 0xFF4C566A; t.value = 0xFFECEFF4;
            }
            case 5 -> { // Rosé Light (тёплая розовая)
                t.bg = 0xFFFBF3F5; t.border = 0xFFE6D2D8; t.title = 0xFF2B1F24; t.label = 0xFF9C5A6E;
                t.btnBg = 0xFFF4E7EB; t.btnHover = 0xFFF0DDE3; t.btnBorder = 0xFFE6D2D8; t.value = 0xFF2B1F24;
            }
            case 6 -> { // Sand Light (тёплая кремовая)
                t.bg = 0xFFF7F3EA; t.border = 0xFFDED6C4; t.title = 0xFF2A251B; t.label = 0xFF8A7A45;
                t.btnBg = 0xFFEFE9DA; t.btnHover = 0xFFEAE2D0; t.btnBorder = 0xFFDED6C4; t.value = 0xFF2A251B;
            }
            default -> { // Cool Dark
                t.bg = 0xFF141C21; t.border = 0xFF0B1116; t.title = 0xFFDFE6EB; t.label = 0xFF9FB6C4;
                t.btnBg = 0xFF20282D; t.btnHover = 0xFF1A2227; t.btnBorder = 0xFF343C42; t.value = 0xFFDFE6EB;
            }
        }
        return t;
    }
}
