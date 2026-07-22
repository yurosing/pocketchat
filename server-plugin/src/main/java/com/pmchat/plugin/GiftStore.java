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
 * Persists received gifts per player in a small YAML file. Keyed by lowercased
 * player name (simple and stable enough for a cosmetic gift list). Each record
 * is encoded as {@code name\u0001icon\u0001from} in a string list.
 */
final class GiftStore {

    private static final char SEP = '\u0001';
    private static final int MAX_PER_PLAYER = 100;

    private final File file;
    private final Logger log;
    private final YamlConfiguration yaml;

    GiftStore(File file, Logger log) {
        this.file = file;
        this.log = log;
        YamlConfiguration y = new YamlConfiguration();
        if (file.exists()) {
            try {
                y.load(file);
            } catch (Exception e) {
                log.log(Level.WARNING, "Could not load gifts store, starting fresh", e);
            }
        }
        this.yaml = y;
    }

    private static String key(String player) {
        return "gifts." + player.toLowerCase(Locale.ROOT);
    }

    synchronized void add(String player, String giftName, String icon, String from) {
        List<String> list = new ArrayList<>(yaml.getStringList(key(player)));
        list.add(safe(giftName) + SEP + safe(icon) + SEP + safe(from));
        while (list.size() > MAX_PER_PLAYER) list.remove(0);
        yaml.set(key(player), list);
        save();
    }

    /** Returns each received gift as {name, icon, from}. Newest last. */
    synchronized List<String[]> get(String player) {
        List<String[]> out = new ArrayList<>();
        for (String raw : yaml.getStringList(key(player))) {
            String[] parts = raw.split(String.valueOf(SEP), -1);
            String name = parts.length > 0 ? parts[0] : "";
            String icon = parts.length > 1 ? parts[1] : "";
            String from = parts.length > 2 ? parts[2] : "";
            out.add(new String[]{name, icon, from});
        }
        return out;
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not save gifts store", e);
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace(SEP, ' ');
    }
}
