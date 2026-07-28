package com.pmchat.plugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory registry of players currently "live" (streaming on an external
 * service such as Twitch/YouTube — the plugin only tracks the announcement,
 * it does not touch any video). Purely transient: nothing is persisted, and
 * a stream is dropped the moment the player disconnects.
 */
final class StreamManager {

    /** One active stream announcement. */
    record Live(String player, String title, String url) {
    }

    private final Map<UUID, Live> active = new LinkedHashMap<>();

    synchronized void start(UUID uuid, String player, String title, String url) {
        active.put(uuid, new Live(player, title, url));
    }

    synchronized boolean stop(UUID uuid) {
        return active.remove(uuid) != null;
    }

    synchronized boolean isLive(String player) {
        return active.values().stream().anyMatch(l -> l.player().equalsIgnoreCase(player));
    }

    synchronized List<Live> list() {
        return List.copyOf(active.values());
    }
}
