package com.pmchat.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired when a PocketChat message could not be delivered because the recipient is
 * offline. By default the sender's client is told so and the message is dropped.
 *
 * <p>Mark the event {@link #setHandled(boolean) handled} to take ownership of delivery —
 * store it in a mailbox, forward it across a proxy, push it to Discord — and PocketChat
 * will skip its "player is offline" notice.
 *
 * <pre>{@code
 * @EventHandler
 * public void onOffline(PocketChatMessageOfflineEvent event) {
 *     mailbox.store(event.getTargetName(), event.getSender().getName(), event.getPlain());
 *     event.setHandled(true);
 *     event.getSender().sendMessage("§7Saved — they'll get it when they log in.");
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class PocketChatMessageOfflineEvent extends PocketChatEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player sender;
    private final String targetName;
    private final String wire;
    private final String plain;
    private boolean handled;

    public PocketChatMessageOfflineEvent(Player sender, String targetName, String wire, String plain) {
        this.sender = sender;
        this.targetName = targetName;
        this.wire = wire;
        this.plain = plain == null ? "" : plain;
    }

    /**
     * @return the player who tried to send the message
     */
    public Player getSender() {
        return sender;
    }

    /**
     * @return the offline recipient's name, exactly as typed
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * @return the structured PocketChat encoding of the message
     */
    public String getWire() {
        return wire;
    }

    /**
     * @return the plain-text form, empty when the message has none
     */
    public String getPlain() {
        return plain;
    }

    /**
     * @return {@code true} when a listener has taken over delivery
     */
    public boolean isHandled() {
        return handled;
    }

    /**
     * Marks the message as delivered by someone else. PocketChat then suppresses the
     * "recipient is offline" notice it would otherwise send back to the sender.
     *
     * @param handled whether delivery has been taken over
     */
    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
