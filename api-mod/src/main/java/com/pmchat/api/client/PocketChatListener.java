package com.pmchat.api.client;

/**
 * Callbacks for what happens inside PocketChat. Every method has a default
 * implementation, so override only what you care about.
 *
 * <p>Register with {@link PocketChatClientApi#addListener(PocketChatListener)}. All
 * callbacks fire on the Minecraft client thread, so it is safe to touch the game from
 * them — and equally important to keep them quick.
 *
 * <pre>{@code
 * PocketChatClient.get().addListener(new PocketChatListener() {
 *     @Override
 *     public void onMessageReceived(PmChatMessage message) {
 *         if (message.isPlainText() && message.text().contains("help")) {
 *             PocketChatClient.get().toast("Ping", message.sender() + " needs you");
 *         }
 *     }
 *
 *     @Override
 *     public boolean allowOutgoing(String target, String text) {
 *         return !text.startsWith("!");   // swallow command-ish lines
 *     }
 * });
 * }</pre>
 *
 * @since 1.0.0
 */
public interface PocketChatListener {

    /**
     * A message arrived — from a private chat, a group, a channel or the global feed.
     *
     * @param message the message, already stored in history
     */
    default void onMessageReceived(PmChatMessage message) {
    }

    /**
     * The local player sent a message. Fired after it has been stored and dispatched.
     *
     * @param message the message
     */
    default void onMessageSent(PmChatMessage message) {
    }

    /**
     * Vetoes an outgoing message before it is sent or stored. Return {@code false} and
     * the message is silently dropped — tell the player yourself if they should know.
     *
     * <p>Every registered listener is asked; one {@code false} is enough to drop it.
     *
     * @param target the conversation the message is going to
     * @param text   the message body
     * @return {@code true} to allow the message
     */
    default boolean allowOutgoing(String target, String text) {
        return true;
    }

    /**
     * Somebody sent the local player a gift.
     *
     * @param from     the sender's name
     * @param giftName display name of the gift
     * @param icon     the gift's symbol
     */
    default void onGiftReceived(String from, String giftName, String icon) {
    }

    /**
     * The player opened a conversation in the PocketChat window.
     *
     * @param conversationId id of the thread that was opened
     */
    default void onConversationOpened(String conversationId) {
    }

    /**
     * The detected server plugin changed — on joining a server, on leaving one, or when
     * the handshake completes.
     *
     * @param tier what the current server offers
     */
    default void onServerTierChanged(ServerTier tier) {
    }
}
