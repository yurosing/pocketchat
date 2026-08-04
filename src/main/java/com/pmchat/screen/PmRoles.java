package com.pmchat.screen;

import com.pmchat.client.PmConfig;

/**
 * Роли-должности игроков (4.5): кружок-префикс перед ником, цвет и подпись.
 * Код своей роли хранится в конфиге ({@code profileRole}). Роль другого игрока
 * определяется автоматически из его серверного ника ({@link #detect}), пока он
 * в сети — {@link PmConfig#playerRoles} держит последнее известное значение, чтобы
 * значок не пропадал, когда игрок выходит и его больше нет в таб-листе.
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

    /**
     * Роль игрока {@code name}: пока он в таб-листе — определяется заново из его
     * серверного ника и кэшируется; офлайн — берётся последнее закэшированное
     * значение, чтобы значок роли не пропадал, когда игрок выходит с сервера.
     */
    public static String resolve(PmConfig config, String name) {
        if (PmNames.isOnline(name)) {
            String code = detect(PmNames.displayString(name));
            if (config != null) config.cacheRole(name, code);
            return code;
        }
        return config != null ? config.roleOf(name) : "";
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

    /** Итоговая должность игрока для отрисовки: значок + цвет + название. */
    public static final class Effective {
        public final String icon;
        public final int color;
        public final String label;
        /** true — ни назначенной админом, ни встроенной по нику должности нет. */
        public final boolean none;

        Effective(String icon, int color, String label, boolean none) {
            this.icon = icon;
            this.color = color;
            this.label = label;
            this.none = none;
        }
    }

    /**
     * Роль игрока для отрисовки: если админ вручную назначил должность в
     * админ-панели (см. {@link com.pmchat.client.PmBackend#roleOf}), она
     * полностью перекрывает встроенную (C/H/M/E/D, автоопределяемую по нику).
     * Без назначенной должности поведение как раньше — {@link #resolve}.
     */
    public static Effective effective(PmConfig config, String name) {
        com.pmchat.client.PmBackend.RoleDef assigned = com.pmchat.client.PmBackend.isConfigured()
                ? com.pmchat.client.PmBackend.roleOf(name) : null;
        if (assigned != null) {
            return new Effective(assigned.prefix, assigned.color, assigned.name, false);
        }
        String code = resolve(config, name);
        return new Effective(icon(code), color(code),
                net.minecraft.text.Text.translatable(nameKey(code)).getString(), code.isEmpty());
    }
}
