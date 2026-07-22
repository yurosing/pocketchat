package com.pmchat.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

/**
 * Полный отображаемый ник игрока — как его прислал сервер в списке игроков
 * (таб): вместе с префиксом и суффиксом (роль/клан/донат и т.п.). Роль-должность
 * определяется из него автоматически, игроку ничего вручную выставлять не нужно.
 */
public final class PmNames {

    private PmNames() {
    }

    private static PlayerListEntry entry(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (name == null || mc.getNetworkHandler() == null) return null;
        return mc.getNetworkHandler().getPlayerListEntry(name);
    }

    /** Отформатированный ник с префиксом/суффиксом как Text (или простой ник). */
    public static Text displayText(String name) {
        PlayerListEntry e = entry(name);
        if (e != null) {
            Text dn = e.getDisplayName();
            if (dn != null) {
                String s = dn.getString();
                if (s != null && !s.isBlank()) return dn;
            }
        }
        return Text.literal(name == null ? "" : name);
    }

    /** Строка полного ника (для поиска роли и расчёта ширины). */
    public static String displayString(String name) {
        return displayText(name).getString();
    }
}
