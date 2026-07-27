package com.pmchat.api;

import java.util.Locale;
import java.util.Objects;

/**
 * A purchasable gift in the PocketChat gift shop.
 *
 * <p>Gifts come from the plugin's {@code config.yml} ({@code gifts:} section) and from
 * plugins that add their own through {@link GiftRegistry#register(Gift)}. Buying one
 * withdraws {@link #price()} through Vault and appends it to the recipient's gift list,
 * which shows up in their in-game PocketChat profile.
 *
 * @param id    stable identifier, matched case-insensitively (e.g. {@code "rose"})
 * @param name  display name shown to players (e.g. {@code "Роза"})
 * @param icon  a short symbol drawn next to the name (e.g. {@code "❀"})
 * @param price cost in Vault currency; {@code 0} makes the gift free
 * @since 1.0.0
 */
public record Gift(String id, String name, String icon, double price) {

    public Gift {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("gift id must not be blank");
        if (price < 0) throw new IllegalArgumentException("gift price must not be negative: " + price);
        name = name == null || name.isBlank() ? id : name;
        icon = icon == null || icon.isBlank() ? "*" : icon;
    }

    /**
     * The id lowercased — gift ids are compared case-insensitively.
     *
     * @return the normalised id
     */
    public String normalisedId() {
        return id.toLowerCase(Locale.ROOT);
    }

    /**
     * A copy of this gift at a different price. Useful from
     * {@link com.pmchat.api.event.PocketChatGiftPurchaseEvent} for per-rank discounts.
     *
     * @param newPrice the new price
     * @return a new {@code Gift}
     */
    public Gift withPrice(double newPrice) {
        return new Gift(id, name, icon, newPrice);
    }
}
