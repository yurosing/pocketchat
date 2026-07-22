package com.pmchat.plugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side companion for the PocketChat client mod.
 *
 * With this plugin installed, PocketChat clients route private messages and media
 * (photos / voice / video) through the server instead of {@code /m} and external
 * file hosts. Ships in two editions that share this code:
 *
 * <ul>
 *   <li><b>PocketChat</b> — free: messaging + media relay.</li>
 *   <li><b>PocketChatPro</b> — unlocks premium client features (e.g. voice
 *       transcription). The edition is baked into the jar via the plugin name, so
 *       it can't be flipped on with a config edit.</li>
 * </ul>
 */
public final class PocketChatPlugin extends JavaPlugin implements Listener {

    /** Plugin-messaging channel shared with the client mod. */
    public static final String CHANNEL = "pmchat:media";

    private MediaChannel channel;

    /** True for the Pro edition — derived from the plugin name in plugin.yml. */
    private boolean isPro() {
        return getName().equalsIgnoreCase("PocketChatPro");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        long retentionHours = getConfig().getLong("retention-hours", 168);
        long maxTotalMb = getConfig().getLong("max-total-mb", 512);
        int maxFileMb = getConfig().getInt("max-file-mb", 25);
        String tellCommand = getConfig().getString("tell-command", "msg");

        MediaStore store = new MediaStore(
                getDataFolder().toPath().resolve("media"), retentionHours, maxTotalMb, getLogger());

        boolean giftsEnabled = getConfig().getBoolean("gifts-enabled", true);
        List<Gift> catalog = loadGiftCatalog();
        GiftStore gifts = new GiftStore(getDataFolder().toPath().resolve("gifts.yml").toFile(), getLogger());

        channel = new MediaChannel(this, store, maxFileMb * 1024 * 1024, tellCommand, isPro(),
                giftsEnabled, catalog, gifts);

        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, channel);
        getServer().getPluginManager().registerEvents(this, this);

        // Periodic retention/size cleanup (pure disk I/O — safe async). First run after
        // one minute, then every 30 minutes.
        getServer().getScheduler().runTaskTimerAsynchronously(this, store::cleanup, 20L * 60, 20L * 60 * 30);

        getLogger().info("PocketChat" + (isPro() ? " Pro" : "") + " enabled (channel '" + CHANNEL + "').");
    }

    /** Reads the gift catalog from config.yml (gifts: id -> {name, icon, price}). */
    private List<Gift> loadGiftCatalog() {
        List<Gift> list = new ArrayList<>();
        ConfigurationSection section = getConfig().getConfigurationSection("gifts");
        if (section == null) return list;
        for (String id : section.getKeys(false)) {
            ConfigurationSection g = section.getConfigurationSection(id);
            if (g == null) continue;
            String name = g.getString("name", id);
            String icon = g.getString("icon", "*");
            double price = g.getDouble("price", 0d);
            list.add(new Gift(id, name, icon, price));
        }
        return list;
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (channel != null) channel.forget(event.getPlayer().getUniqueId());
    }
}
