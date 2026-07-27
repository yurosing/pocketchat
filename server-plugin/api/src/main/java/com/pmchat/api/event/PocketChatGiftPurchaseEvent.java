package com.pmchat.api.event;

import com.pmchat.api.Gift;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player tries to buy a gift, <b>before</b> any money is withdrawn.
 *
 * <p>PocketChat has already checked that gifts are enabled, that the gift exists, and
 * that the buyer is not gifting themselves — but it has not yet touched Vault. That
 * makes this the place to apply discounts, rank restrictions or purchase limits.
 *
 * <p>Adjust {@link #setPrice(double)} for a discount (or a surcharge), or cancel with a
 * {@link #setFailureMessage(String) message} the buyer will see.
 *
 * <pre>{@code
 * @EventHandler
 * public void onBuy(PocketChatGiftPurchaseEvent event) {
 *     if (event.getBuyer().hasPermission("myserver.vip")) {
 *         event.setPrice(event.getPrice() * 0.5);   // half price for VIPs
 *     }
 *     if (dailyLimitReached(event.getBuyer())) {
 *         event.setFailureMessage("Daily gift limit reached");
 *         event.setCancelled(true);
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class PocketChatGiftPurchaseEvent extends PocketChatEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player buyer;
    private final String targetName;
    private final Gift gift;
    private double price;
    private String failureMessage;
    private boolean cancelled;

    public PocketChatGiftPurchaseEvent(Player buyer, String targetName, Gift gift, double price) {
        this.buyer = buyer;
        this.targetName = targetName;
        this.gift = gift;
        this.price = price;
    }

    /**
     * @return the player paying for the gift
     */
    public Player getBuyer() {
        return buyer;
    }

    /**
     * @return the recipient's name as typed; they may be offline
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * @return the gift being bought, at its catalog price
     */
    public Gift getGift() {
        return gift;
    }

    /**
     * The price about to be withdrawn. Starts at the catalog price and reflects any
     * changes earlier listeners made.
     *
     * @return the effective price
     */
    public double getPrice() {
        return price;
    }

    /**
     * Overrides what the buyer pays. Negative values are clamped to {@code 0}, which
     * makes the gift free.
     *
     * @param price the new price
     */
    public void setPrice(double price) {
        this.price = Math.max(0d, price);
    }

    /**
     * @return the message shown when the purchase is refused, or {@code null}
     */
    public String getFailureMessage() {
        return failureMessage;
    }

    /**
     * Sets the message the buyer's client shows when the purchase is refused. Only used
     * when the event ends up cancelled.
     *
     * @param failureMessage a short explanation, or {@code null} for a generic one
     */
    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancelling refuses the purchase. Nothing is withdrawn and no gift is granted.
     *
     * @param cancelled whether to refuse the purchase
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
