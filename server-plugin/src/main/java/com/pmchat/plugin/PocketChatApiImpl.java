package com.pmchat.plugin;

import com.pmchat.api.Gift;
import com.pmchat.api.GiftRegistry;
import com.pmchat.api.MediaService;
import com.pmchat.api.PocketChat;
import com.pmchat.api.PocketChatApi;
import com.pmchat.api.PocketChatTier;
import com.pmchat.api.ReceivedGift;
import com.pmchat.api.event.PocketChatEvent;
import com.pmchat.api.event.PocketChatGiftReceiveEvent;
import com.pmchat.api.protocol.PocketChatProtocol;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The plugin's implementation of the public {@link PocketChatApi}, registered with the
 * Bukkit services manager so third-party plugins can find it regardless of which edition
 * (PocketChat / PocketChatPro) is installed.
 *
 * <p>Also the single place events are fired from, so {@link MediaChannel} stays focused
 * on the wire protocol.
 */
final class PocketChatApiImpl implements PocketChatApi, GiftRegistry {

    private final Plugin plugin;
    private final MediaStore store;
    private final GiftStore gifts;
    private final PocketChatTier tier;
    private final boolean giftsEnabled;
    private final int maxFileBytes;
    /** Catalog order is meaningful (it is what the client renders), hence a list. */
    private final CopyOnWriteArrayList<Gift> catalog;
    private MediaChannel channel;

    PocketChatApiImpl(Plugin plugin, MediaStore store, GiftStore gifts, PocketChatTier tier,
                      boolean giftsEnabled, int maxFileBytes, List<Gift> catalog) {
        this.plugin = plugin;
        this.store = store;
        this.gifts = gifts;
        this.tier = tier;
        this.giftsEnabled = giftsEnabled;
        this.maxFileBytes = maxFileBytes;
        this.catalog = new CopyOnWriteArrayList<>(catalog);
    }

    /** Wired after construction — the channel needs the api and the api needs the channel. */
    void bind(MediaChannel channel) {
        this.channel = channel;
    }

    /**
     * Fires an event on the main thread. Every call site is already on the main thread
     * (async media work hops back through {@code MediaChannel.runMain}), so this is a
     * direct dispatch; the guard is here to keep a future async caller honest.
     */
    <E extends PocketChatEvent> E fire(E event) {
        plugin.getServer().getPluginManager().callEvent(event);
        return event;
    }

    // ---------- PocketChatApi ----------

    @Override
    public PocketChatTier tier() {
        return tier;
    }

    @Override
    public String version() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public int protocolVersion() {
        return PocketChatProtocol.PROTOCOL_VERSION;
    }

    @Override
    public boolean hasClient(Player player) {
        return player != null && player.isOnline()
                && player.getListeningPluginChannels().contains(PocketChat.CHANNEL);
    }

    @Override
    public Collection<Player> clients() {
        List<Player> out = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (hasClient(p)) out.add(p);
        }
        return List.copyOf(out);
    }

    @Override
    public boolean sendMessage(Player from, String target, String text) {
        if (from == null || target == null || text == null || channel == null) return false;
        return channel.routePm(from, target, text, text);
    }

    @Override
    public boolean sendSystemMessage(Player to, String fromLabel, String text) {
        if (to == null || channel == null || !hasClient(to)) return false;
        channel.pushPm(to, fromLabel == null ? "Server" : fromLabel, text == null ? "" : text);
        return true;
    }

    @Override
    public GiftRegistry gifts() {
        return this;
    }

    @Override
    public List<ReceivedGift> giftsOf(String player) {
        return player == null ? List.of() : gifts.received(player);
    }

    @Override
    public void giveGift(String player, Gift gift, String from) {
        if (player == null || gift == null) return;
        String sender = from == null ? plugin.getName() : from;
        gifts.add(player, gift.name(), gift.icon(), sender);
        if (channel != null) channel.announceGift(player, gift, sender, 0d);
    }

    @Override
    public double balanceOf(OfflinePlayer player) {
        Economy eco = economy();
        return eco == null || player == null ? 0d : eco.getBalance(player);
    }

    @Override
    public boolean hasEconomy() {
        return economy() != null;
    }

    @Override
    public MediaService media() {
        return new MediaServiceImpl(store, maxFileBytes);
    }

    /** Vault economy provider, or null when Vault / an economy plugin is absent. */
    Economy economy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    // ---------- GiftRegistry ----------

    @Override
    public Gift register(Gift gift) {
        if (gift == null) return null;
        Gift previous = unregister(gift.id());
        catalog.add(gift);
        return previous;
    }

    @Override
    public Gift unregister(String id) {
        if (id == null) return null;
        String needle = id.toLowerCase(Locale.ROOT);
        for (Gift g : catalog) {
            if (g.normalisedId().equals(needle)) {
                catalog.remove(g);
                return g;
            }
        }
        return null;
    }

    @Override
    public Gift byId(String id) {
        if (id == null) return null;
        String needle = id.toLowerCase(Locale.ROOT);
        for (Gift g : catalog) {
            if (g.normalisedId().equals(needle)) return g;
        }
        return null;
    }

    @Override
    public List<Gift> all() {
        return List.copyOf(catalog);
    }

    @Override
    public boolean isEnabled() {
        return giftsEnabled;
    }

    /**
     * Convenience for {@link PocketChatGiftReceiveEvent}: builds PocketChat's default
     * announcement line for a gift.
     */
    static String defaultAnnouncement(Gift gift, String from) {
        return "§d" + gift.icon() + " §f" + from + " §7подарил вам §f" + gift.name();
    }
}
