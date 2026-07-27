package com.pmchat.api.client;

import java.util.List;

/**
 * The PocketChat client API — everything another Fabric mod can read from, or do to,
 * the messenger running on the local player's client.
 *
 * <p>Obtain it with {@link PocketChatClient#get()}. Unless noted otherwise, call these
 * from the Minecraft client thread.
 *
 * @since 1.0.0
 */
public interface PocketChatClientApi {

    /**
     * @return the PocketChat mod version, e.g. {@code "1.10.1"}
     */
    String modVersion();

    // ---------- listeners ----------

    /**
     * Registers a listener. Adding the same instance twice has no extra effect.
     *
     * @param listener the listener to add
     */
    void addListener(PocketChatListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     * @return {@code true} when it was registered
     */
    boolean removeListener(PocketChatListener listener);

    // ---------- state ----------

    /**
     * The local player's name as PocketChat knows it.
     *
     * @return the player name, or an empty string before joining a world
     */
    String selfName();

    /**
     * What the current server's PocketChat plugin offers.
     *
     * @return the tier, {@link ServerTier#NONE} when there is no plugin or no server
     */
    ServerTier serverTier();

    /**
     * Every conversation in the list, newest activity first.
     *
     * @return an immutable snapshot
     */
    List<Conversation> conversations();

    /**
     * The messages of one conversation, oldest first.
     *
     * @param conversationId a {@link Conversation#id()}
     * @return an immutable snapshot, empty when the thread is unknown
     */
    List<PmChatMessage> messages(String conversationId);

    /**
     * Whether the local player has blocked someone.
     *
     * @param player a player name
     * @return {@code true} when blocked
     */
    boolean isBlocked(String player);

    /**
     * The player's last known server balance as a display string, e.g. {@code "1 250"}.
     * Only available with the server plugin and a Vault economy.
     *
     * @return the balance text, or an empty string when unknown
     */
    String knownBalance();

    // ---------- actions ----------

    /**
     * Sends a message to a conversation, exactly as if the player had typed it. Goes
     * through the server plugin when one is present, and falls back to {@code /m}
     * otherwise.
     *
     * <p>Registered listeners get a say through
     * {@link PocketChatListener#allowOutgoing(String, String)}.
     *
     * @param target a {@link Conversation#id()} — a player name for a direct chat
     * @param text   the message body
     * @return {@code true} when the message was sent
     */
    boolean send(String target, String text);

    /**
     * Sends a message to the server's global chat.
     *
     * @param text the message body
     */
    void sendGlobal(String text);

    /**
     * Opens the PocketChat window, on a specific conversation when one is given.
     *
     * @param conversationId a {@link Conversation#id()}, or {@code null} for the list
     */
    void open(String conversationId);

    /**
     * Shows a PocketChat toast in the corner of the screen.
     *
     * @param title short heading
     * @param body  body text
     */
    void toast(String title, String body);

    /**
     * Blocks or unblocks a player. Blocked players' messages are hidden and, without a
     * server plugin, the block is mirrored to the server's own ignore command.
     *
     * @param player  a player name
     * @param blocked whether to block them
     */
    void setBlocked(String player, boolean blocked);
}
