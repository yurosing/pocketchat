package com.pmchat.api.event;

import org.bukkit.event.Event;

/**
 * Marker base class for every PocketChat event, so listeners can catch them as a group
 * and {@code instanceof} them cheaply.
 *
 * <p>All PocketChat events fire on the main server thread, even the ones triggered by
 * work that started asynchronously (media disk I/O hands back to the main thread before
 * the event fires). Listeners may therefore touch the Bukkit API freely.
 *
 * <p>Each concrete subclass carries its own {@code HandlerList}, as Bukkit requires, so
 * registering for one event never delivers another.
 *
 * @since 1.0.0
 */
public abstract class PocketChatEvent extends Event {

    protected PocketChatEvent() {
        super(false);
    }
}
