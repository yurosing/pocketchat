package com.pmchat.screen;

/**
 * Роли-должности игроков (4.5): кружок-префикс перед ником, цвет и подпись.
 * Коды хранятся в конфиге (своя роль profileRole, чужие — playerRoles).
 * Назначаются вручную («настраивать самому»).
 */
public final class PmRoles {

    private PmRoles() {
    }

    /** Порядок перебора в UI (пусто = без роли идёт первым). */
    public static final String[] CODES = {"", "C", "H", "M", "E", "D"};

    /**
     * Автоопределение должности из полного ника (префикс/суффикс с сервера).
     * Сначала по кружкам-иконкам (Ⓒ/Ⓓ/Ⓔ/Ⓜ/Ⓗ), затем по ключевым словам.
     * Игрок ничего не выставляет вручную — роль берётся из ника как есть.
     */
    public static String detect(String display) {
        if (display == null || display.isEmpty()) return "";
        if (display.contains("Ⓒ")) return "C";
        if (display.contains("Ⓓ")) return "D";
        if (display.contains("Ⓔ")) return "E";
        if (display.contains("Ⓜ")) return "M";
        if (display.contains("Ⓗ")) return "H";
        String l = display.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("develop") || l.contains("разраб") || l.contains("девелоп")) return "D";
        if (l.contains("event") || l.contains("ивент")) return "E";
        if (l.contains("moder") || l.contains("модер")) return "M";
        if (l.contains("helper") || l.contains("хелпер") || l.contains("помощ")) return "H";
        if (l.contains("content") || l.contains("контент") || l.contains("maker") || l.contains("мейкер")) return "C";
        return "";
    }

    /** Кружок-иконка роли (пусто, если роли нет). */
    public static String icon(String code) {
        return switch (norm(code)) {
            case "C" -> "Ⓒ";
            case "H" -> "Ⓗ";
            case "M" -> "Ⓜ";
            case "E" -> "Ⓔ";
            case "D" -> "Ⓓ";
            default -> "";
        };
    }

    /** Цвет роли (для иконки и названия). */
    public static int color(String code) {
        return switch (norm(code)) {
            case "C" -> 0xFFE0A0E0;
            case "H" -> 0xFF6FBF8B;
            case "M" -> 0xFF5AA0E0;
            case "E" -> 0xFFE0B040;
            case "D" -> 0xFFE0574C;
            default -> 0xFF9AA4AC;
        };
    }

    /** Ключ локализации названия должности. */
    public static String nameKey(String code) {
        return switch (norm(code)) {
            case "C" -> "pmchat.role.content";
            case "H" -> "pmchat.role.helper";
            case "M" -> "pmchat.role.mod";
            case "E" -> "pmchat.role.event";
            case "D" -> "pmchat.role.dev";
            default -> "pmchat.role.none";
        };
    }

    /** Следующий код при переборе в настройках роли. */
    public static String next(String code) {
        String cur = norm(code);
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(cur)) return CODES[(i + 1) % CODES.length];
        }
        return CODES[0];
    }

    private static String norm(String code) {
        return code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
