package com.pmchat.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * The PocketChat server API — everything a plugin can <em>do</em> to PocketChat.
 * For reacting to what PocketChat does, listen to the events in
 * {@link com.pmchat.api.event}.
 *
 * <p>Obtain it with {@link PocketChat#api()}. The instance is registered while the
 * plugin is enabled and unregistered on disable; do not cache it across reloads.
 *
 * <p>Unless a method says otherwise, call these from the main server thread.
 *
 * @since 1.0.0
 */
public interface PocketChatApi {

    /**
     * @return the running edition
     */
    PocketChatTier tier();

    /**
     * @return the plugin version, e.g. {@code "2.0.0"}
     */
    String version();

    /**
     * The wire protocol version this server speaks. Compare against
     * {@link com.pmchat.api.protocol.PocketChatProtocol#PROTOCOL_VERSION}.
     *
     * @return the protocol version
     */
    int protocolVersion();

    // ---------- clients ----------

    /**
     * Whether a player is running the PocketChat client mod. Players without it still
     * receive private messages, just as ordinary whispers.
     *
     * @param player the player to check
     * @return {@code true} when the player's client is listening on the PocketChat channel
     */
    boolean hasClient(Player player);

    /**
     * Every online player currently running the client mod.
     *
     * @return an immutable snapshot
     */
    Collection<Player> clients();

    // ---------- messaging ----------

    /**
     * Sends a private message as if {@code from} had typed it in PocketChat.
     *
     * <p>Delivery matches the mod's own behaviour: if the recipient runs PocketChat the
     * message lands in their chat thread, otherwise it is delivered as a normal whisper.
     * This fires {@link com.pmchat.api.event.PocketChatMessageEvent}, so other plugins
     * (and your own listeners) can still cancel it.
     *
     * @param from   the sender
     * @param target the recipient's name, matched exactly
     * @param text   the message body
     * @return {@code true} when the message was delivered; {@code false} when the recipient
     *         is offline or a listener cancelled it
     */
    boolean sendMessage(Player from, String target, String text);

    /**
     * Delivers a message into a player's PocketChat thread without a real sender —
     * useful for bots, relays and server announcements.
     *
     * <p>The message shows up as coming from {@code fromLabel}. If the player is not
     * running the mod, nothing is delivered and this returns {@code false}.
     *
     * @param to        the recipient
     * @param fromLabel the name to show as the sender
     * @param text      the message body
     * @return {@code true} when the message was pushed to the player's client
     */
    boolean sendSystemMessage(Player to, String fromLabel, String text);

    // ---------- gifts ----------

    /**
     * @return the live gift catalog
     */
    GiftRegistry gifts();

    /**
     * The gifts a player has received, oldest first.
     *
     * @param player player name, matched case-insensitively
     * @return an immutable snapshot, empty when the player has none
     */
    List<ReceivedGift> giftsOf(String player);

    /**
     * Grants a gift to a player <b>free of charge</b> — no Vault withdrawal, no balance
     * check. Use this for rewards, events and crates.
     *
     * <p>Fires {@link com.pmchat.api.event.PocketChatGiftReceiveEvent}. The recipient is
     * notified in chat (and in their client, if they run the mod) when online.
     *
     * @param player player name, matched case-insensitively
     * @param gift   the gift to grant
     * @param from   the name to credit as the sender, e.g. your plugin or an event name
     */
    void giveGift(String player, Gift gift, String from);

    /**
     * A player's Vault balance, as PocketChat reads it for the gift shop.
     *
     * @param player the player
     * @return the balance, or {@code 0} when Vault or an economy plugin is missing
     */
    double balanceOf(OfflinePlayer player);

    /**
     * Whether a Vault economy provider is available. Without one, the gift shop is
     * unusable and balances read as {@code 0}.
     *
     * @return {@code true} when an economy is hooked up
     */
    boolean hasEconomy();

    // ---------- media ----------

    /**
     * @return the server's media store
     */
    MediaService media();
}
