package com.pmchat.api.client;

/**
 * What the PocketChat server plugin on the current server offers, as detected during the
 * client's handshake.
 *
 * @since 1.0.0
 */
public enum ServerTier {

    /** No plugin. Messages go through {@code /m} and media through external file hosts. */
    NONE,

    /** The free plugin: private messages and media are relayed by the server. */
    FREE,

    /** The Pro plugin: everything in {@link #FREE} plus premium client features. */
    PRO;

    /**
     * @return {@code true} when a plugin of any edition is present
     */
    public boolean hasPlugin() {
        return this != NONE;
    }
}
