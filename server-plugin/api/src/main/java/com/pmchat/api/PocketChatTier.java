package com.pmchat.api;

/**
 * Which edition of the PocketChat plugin is running. The tier is baked into the jar
 * (it is derived from the plugin name), so it cannot be flipped with a config edit.
 *
 * @since 1.0.0
 */
public enum PocketChatTier {

    /** {@code PocketChat} — private-message routing, media relay and gifts. */
    FREE,

    /** {@code PocketChatPro} — everything in {@link #FREE} plus premium client features. */
    PRO;

    /**
     * @return {@code true} for {@link #PRO}
     */
    public boolean isPro() {
        return this == PRO;
    }
}
