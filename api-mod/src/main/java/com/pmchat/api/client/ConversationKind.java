package com.pmchat.api.client;

/**
 * What kind of thread a conversation is. PocketChat keeps several thread types side by
 * side in one list, and they behave differently — a direct chat has a real player on the
 * other end, a channel is a one-way feed, and the service tabs have no counterpart at all.
 *
 * @since 1.0.0
 */
public enum ConversationKind {

    /** A private chat with one player. The conversation id is that player's name. */
    DIRECT,

    /** The server's global chat, mirrored into PocketChat. */
    GLOBAL,

    /** A broadcast channel — a one-way feed with posts, views and subscribers. */
    CHANNEL,

    /** A group chat with several members. */
    GROUP,

    /** A public broadcast the player owns or follows. */
    BROADCAST,

    /** The player's own "saved messages" notes tab. */
    SAVED,

    /** The CoreProtect log feed, for staff. */
    COREPROTECT;

    /**
     * Whether this thread has a real player on the other side, so
     * {@link PocketChatClientApi#send(String, String)} makes sense for it.
     *
     * @return {@code true} only for {@link #DIRECT}
     */
    public boolean isDirect() {
        return this == DIRECT;
    }
}
