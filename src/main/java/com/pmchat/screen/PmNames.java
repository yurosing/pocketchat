package com.pmchat.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Полный отображаемый ник игрока — как его прислал сервер в списке игроков
 * (таб): вместе с префиксом и суффиксом (роль/клан/донат и т.п.). Роль-должность
 * определяется из него автоматически, игроку ничего вручную выставлять не нужно.
 */
public final class PmNames {

    private PmNames() {
    }

    private static PlayerInfo entry(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (name == null || mc.getNetworkHandler() == null) return null;
        return mc.getNetworkHandler().getPlayerListEntry(name);
    }

    /** Отформатированный ник с префиксом/суффиксом как Component (или простой ник). */
    public static Component displayText(String name) {
        PlayerInfo e = entry(name);
        if (e != null) {
            Component dn = e.getDisplayName();
            if (dn != null) {
                String s = dn.getString();
                if (s != null && !s.isBlank()) return dn;
            }
        }
        return Component.literal(name == null ? "" : name);
    }

    /** Строка полного ника (для поиска роли и расчёта ширины). */
    public static String displayString(String name) {
        return displayText(name).getString();
    }

    /** Есть ли игрок сейчас в таб-листе (т.е. можно прочитать его актуальный ник/роль). */
    public static boolean isOnline(String name) {
        return entry(name) != null;
    }
}
