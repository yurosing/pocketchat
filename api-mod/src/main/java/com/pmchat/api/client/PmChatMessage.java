package com.pmchat.api.client;

/**
 * One message in a PocketChat conversation — an immutable snapshot of what the mod has
 * stored. Editing it does nothing; use {@link PocketChatClientApi} to act.
 *
 * @param conversation  id of the thread the message belongs to (see
 *                      {@link Conversation#id()})
 * @param sender        who wrote it. For outgoing messages this is the local player
 * @param text          the message body. For structured messages (voice, images, polls)
 *                      this is PocketChat's wire encoding rather than readable text —
 *                      check {@link #isPlainText()} before showing it to a human
 * @param time          when it was sent, in milliseconds since the epoch
 * @param outgoing      {@code true} when the local player sent it
 * @param money         amount attached, for money transfers; {@code 0} otherwise
 * @param replyToHash   {@link #hash()} of the message this replies to, or {@code null}
 * @param hash          short stable id of this message within its conversation, used for
 *                      replies, reactions, pins and edits
 * @since 1.0.0
 */
public record PmChatMessage(
        String conversation,
        String sender,
        String text,
        long time,
        boolean outgoing,
        long money,
        String replyToHash,
        String hash) {

    public PmChatMessage {
        text = text == null ? "" : text;
    }

    /**
     * Whether {@link #text()} is ordinary readable text rather than a structured
     * PocketChat payload (voice note, image, poll, reaction, service marker).
     *
     * <p>Structured payloads all start with the {@code "pmc "} marker.
     *
     * @return {@code true} when the text can be shown as-is
     */
    public boolean isPlainText() {
        return !text.startsWith("pmc ");
    }

    /**
     * @return {@code true} when this message carries a money transfer
     */
    public boolean isMoney() {
        return money != 0L;
    }

    /**
     * @return {@code true} when this message quotes another one
     */
    public boolean isReply() {
        return replyToHash != null && !replyToHash.isEmpty();
    }
}
