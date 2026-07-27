package com.pmchat.api.client;

/**
 * One thread in the PocketChat conversation list.
 *
 * @param id              stable identifier. For a {@link ConversationKind#DIRECT} chat
 *                        this is the other player's name; the other kinds use internal
 *                        prefixed ids, so pass this value back verbatim rather than
 *                        building one yourself
 * @param displayName     the name shown in the list
 * @param kind            what sort of thread this is
 * @param unread          number of unread messages
 * @param lastMessageTime timestamp of the newest message, in milliseconds since the
 *                        epoch, or {@code 0} when the thread is empty
 * @since 1.0.0
 */
public record Conversation(
        String id,
        String displayName,
        ConversationKind kind,
        int unread,
        long lastMessageTime) {

    /**
     * @return {@code true} when the thread has unread messages
     */
    public boolean hasUnread() {
        return unread > 0;
    }
}
