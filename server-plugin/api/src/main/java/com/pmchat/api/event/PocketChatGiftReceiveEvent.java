package com.pmchat.api.event;

import com.pmchat.api.Gift;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired once a gift has been granted — after payment succeeded for a purchase, or
 * immediately for {@link com.pmchat.api.PocketChatApi#giveGift}.
 *
 * <p>The gift is already stored at this point; the event cannot undo it. What you can
 * change is the chat line the recipient sees: {@link #setAnnouncement(String)} replaces
 * PocketChat's default line, and passing {@code null} suppresses it entirely (handy if
 * you want to send your own formatted message, or a title, or a sound).
 *
 * <pre>{@code
 * @EventHandler
 * public void onGift(PocketChatGiftReceiveEvent event) {
 *     event.setAnnouncement(null);                       // silence the default line
 *     Player to = event.getRecipient();
 *     if (to != null) {
 *         to.showTitle(Title.title(
 *                 Component.text(event.getGift().icon()),
 *                 Component.text("A gift from " + event.getFrom())));
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class PocketChatGiftReceiveEvent extends PocketChatEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String recipientName;
    private final Player recipient;
    private final Gift gift;
    private final String from;
    private final double pricePaid;
    private String announcement;

    public PocketChatGiftReceiveEvent(String recipientName, Player recipient, Gift gift,
                                      String from, double pricePaid, String announcement) {
        this.recipientName = recipientName;
        this.recipient = recipient;
        this.gift = gift;
        this.from = from;
        this.pricePaid = pricePaid;
        this.announcement = announcement;
    }

    /**
     * @return the recipient's name; gifts are stored by name, so this works for offline players
     */
    public String getRecipientName() {
        return recipientName;
    }

    /**
     * @return the recipient, or {@code null} when they are offline
     */
    public Player getRecipient() {
        return recipient;
    }

    /**
     * @return the gift that was granted
     */
    public Gift getGift() {
        return gift;
    }

    /**
     * Who the gift is credited to. For {@link com.pmchat.api.PocketChatApi#giveGift} this
     * is whatever the calling plugin passed, so it need not be a real player.
     *
     * @return the sender's name or label
     */
    public String getFrom() {
        return from;
    }

    /**
     * What the buyer actually paid, after any adjustment made in
     * {@link PocketChatGiftPurchaseEvent}. Always {@code 0} for gifts granted through the API.
     *
     * @return the amount withdrawn
     */
    public double getPricePaid() {
        return pricePaid;
    }

    /**
     * @return the chat line the recipient will see, or {@code null} when it is suppressed
     */
    public String getAnnouncement() {
        return announcement;
    }

    /**
     * Replaces the chat line the recipient sees. Legacy {@code §} colour codes are
     * supported. Pass {@code null} to send nothing at all.
     *
     * @param announcement the new line, or {@code null} to suppress it
     */
    public void setAnnouncement(String announcement) {
        this.announcement = announcement;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
