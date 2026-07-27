package com.pmchat.api;

import java.util.List;

/**
 * The live gift catalog. Starts out holding whatever the plugin's {@code config.yml}
 * declares; other plugins may add to it at runtime.
 *
 * <p>Registered gifts are <b>not</b> persisted to {@code config.yml} — register them
 * again in your {@code onEnable()} every start. Clients are told about the catalog when
 * they open the gift shop, so changes take effect immediately.
 *
 * <pre>{@code
 * PocketChat.api().gifts().register(new Gift("tulip", "Тюльпан", "❀", 75));
 * }</pre>
 *
 * @since 1.0.0
 */
public interface GiftRegistry {

    /**
     * Adds a gift, replacing any existing gift with the same id (case-insensitive).
     *
     * @param gift the gift to add
     * @return the gift that was replaced, or {@code null} when the id was new
     */
    Gift register(Gift gift);

    /**
     * Removes a gift by id.
     *
     * @param id gift id, matched case-insensitively
     * @return the removed gift, or {@code null} when there was no such gift
     */
    Gift unregister(String id);

    /**
     * Looks a gift up by id.
     *
     * @param id gift id, matched case-insensitively
     * @return the gift, or {@code null}
     */
    Gift byId(String id);

    /**
     * Every gift currently in the catalog, in catalog order.
     *
     * @return an immutable snapshot
     */
    List<Gift> all();

    /**
     * Whether the gift shop is switched on at all ({@code gifts-enabled} in {@code config.yml}).
     * When it is off, clients see an empty catalog and purchases are refused.
     *
     * @return {@code true} when gifts are enabled
     */
    boolean isEnabled();
}
