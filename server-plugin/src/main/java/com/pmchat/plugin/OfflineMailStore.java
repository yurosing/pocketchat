package com.pmchat.plugin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mailboxes PMs sent to a player who was offline, so they can be flushed back the
 * moment that player's PocketChat client re-connects (see {@code MediaChannel.handleHello}).
 * Keyed by lowercased player name, same convention as {@link GiftStore}; persisted so a
 * server restart doesn't lose mail still waiting for delivery.
 */
final class OfflineMailStore {

    private static final char SEP = '\u0001';
    private static final int MAX_PER_PLAYER = 200;

    record Mail(String sender, String wire) {
    }

    private final File file;
    private final Logger log;
    private final YamlConfiguration yaml;

    OfflineMailStore(File file, Logger log) {
        this.file = file;
        this.log = log;
        YamlConfiguration y = new YamlConfiguration();
        if (file.exists()) {
            try {
                y.load(file);
            } catch (Exception e) {
                log.log(Level.WARNING, "Could not load offline mail store, starting fresh", e);
            }
        }
        this.yaml = y;
    }

    private static String key(String player) {
        return "mail." + player.toLowerCase(Locale.ROOT);
    }

    /** Queues one PM for later delivery to {@code target}. */
    synchronized void queue(String target, String sender, String wire) {
        List<String> list = new ArrayList<>(yaml.getStringList(key(target)));
        list.add(safe(sender) + SEP + safe(wire));
        while (list.size() > MAX_PER_PLAYER) list.remove(0);
        yaml.set(key(target), list);
        save();
    }

    /** Removes and returns all mail waiting for {@code target}, oldest first. */
    synchronized List<Mail> drain(String target) {
        String k = key(target);
        List<String> raw = yaml.getStringList(k);
        if (raw.isEmpty()) return List.of();
        List<Mail> out = new ArrayList<>(raw.size());
        for (String r : raw) {
            String[] parts = r.split(String.valueOf(SEP), -1);
            if (parts.length < 2) continue;
            out.add(new Mail(parts[0], parts[1]));
        }
        yaml.set(k, null);
        save();
        return out;
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not save offline mail store", e);
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace(SEP, ' ');
    }
}
