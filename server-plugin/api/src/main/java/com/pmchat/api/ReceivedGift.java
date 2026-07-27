package com.pmchat.api;

/**
 * One gift a player has received, as stored in the plugin's gift list.
 *
 * @param name display name of the gift at the time it was given
 * @param icon symbol drawn next to the name
 * @param from name of the player who sent it
 * @since 1.0.0
 */
public record ReceivedGift(String name, String icon, String from) {

    public ReceivedGift {
        name = name == null ? "" : name;
        icon = icon == null ? "" : icon;
        from = from == null ? "" : from;
    }
}
