package com.pmchat.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired for every private message a PocketChat client sends through the server,
 * <b>before</b> it is delivered. Cancel it to drop the message.
 *
 * <p>A PocketChat message carries two representations:
 * <ul>
 *   <li>{@link #getWire()} — the structured encoding the mod understands (it can hold a
 *       voice note, an image reference, a reply, a poll, a reaction…). Delivered when
 *       the recipient runs the mod.</li>
 *   <li>{@link #getPlain()} — a plain-text fallback delivered as an ordinary whisper
 *       when the recipient does <em>not</em> run the mod. It is empty for messages that
 *       have no sensible text form, such as a reaction.</li>
 * </ul>
 * {@link #getDeliveryMode()} tells you which route this message is about to take.
 * Only {@link #getPlain()} is mutable — rewriting the wire form would corrupt
 * structured messages.
 *
 * <pre>{@code
 * @EventHandler(ignoreCancelled = true)
 * public void onPocketChatMessage(PocketChatMessageEvent event) {
 *     if (mutes.isMuted(event.getSender())) {
 *         event.setCancelled(true);
 *         event.getSender().sendMessage("§cYou are muted.");
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class PocketChatMessageEvent extends PocketChatEvent implements Cancellable {

    /** How this message is about to reach its recipient. */
    public enum DeliveryMode {
        /** The recipient runs PocketChat — the structured {@code wire} form is delivered. */
        MOD,
        /** The recipient has no mod — {@link PocketChatMessageEvent#getPlain()} goes out as a whisper. */
        FALLBACK_WHISPER,
        /** The recipient is offline — nothing is delivered and the sender is told so. */
        OFFLINE
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player sender;
    private final String targetName;
    private final Player recipient;
    private final String wire;
    private final DeliveryMode deliveryMode;
    private String plain;
    private boolean cancelled;

    public PocketChatMessageEvent(Player sender, String targetName, Player recipient,
                                  String wire, String plain, DeliveryMode deliveryMode) {
        this.sender = sender;
        this.targetName = targetName;
        this.recipient = recipient;
        this.wire = wire;
        this.plain = plain == null ? "" : plain;
        this.deliveryMode = deliveryMode;
    }

    /**
     * @return the player sending the message
     */
    public Player getSender() {
        return sender;
    }

    /**
     * The recipient's name exactly as the client typed it. Always present, even when the
     * recipient is offline.
     *
     * @return the target name
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * @return the recipient, or {@code null} when they are offline
     */
    public Player getRecipient() {
        return recipient;
    }

    /**
     * The structured PocketChat encoding — voice note, image, reply, poll, reaction and
     * so on. Plain chat messages appear here verbatim. Read-only.
     *
     * @return the wire form
     */
    public String getWire() {
        return wire;
    }

    /**
     * The plain-text fallback, used when the recipient has no mod. Empty for messages
     * with no text form (a reaction, a typing indicator).
     *
     * @return the fallback text, never {@code null}
     */
    public String getPlain() {
        return plain;
    }

    /**
     * Rewrites the plain-text fallback. Has no effect when
     * {@link #getDeliveryMode()} is not {@link DeliveryMode#FALLBACK_WHISPER}.
     *
     * @param plain the new fallback text; {@code null} is treated as empty
     */
    public void setPlain(String plain) {
        this.plain = plain == null ? "" : plain;
    }

    /**
     * @return how this message is about to be delivered
     */
    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancelling drops the message entirely: nothing is delivered and the sender is not
     * told why. Send your own feedback if the player deserves an explanation.
     *
     * @param cancelled whether to drop the message
     */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
