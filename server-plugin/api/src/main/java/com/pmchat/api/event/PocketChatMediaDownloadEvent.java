package com.pmchat.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when a client asks to download a stored media file, before the server starts
 * streaming it.
 *
 * <p>PocketChat's only access check is the file id itself: ids are random 16-character
 * tokens shared solely with the sender and the recipient. Listen here if you want real
 * authorisation on top of that — a per-player allow-list, a rate limit, or a check
 * against your own record of who was sent what.
 *
 * <pre>{@code
 * @EventHandler
 * public void onDownload(PocketChatMediaDownloadEvent event) {
 *     if (!ledger.wasSentTo(event.getPlayer().getUniqueId(), event.getFileId())) {
 *         event.setDenyReason("not yours");
 *         event.setCancelled(true);
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class PocketChatMediaDownloadEvent extends PocketChatEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String fileId;
    private String denyReason;
    private boolean cancelled;

    public PocketChatMediaDownloadEvent(Player player, String fileId) {
        this.player = player;
        this.fileId = fileId;
    }

    /**
     * @return the player requesting the file
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * The requested file id. Not yet validated — it may well not exist.
     *
     * @return the file id
     */
    public String getFileId() {
        return fileId;
    }

    /**
     * @return the reason shown when the download is refused, or {@code null}
     */
    public String getDenyReason() {
        return denyReason;
    }

    /**
     * Sets the short reason the client displays when the download is refused. Only used
     * when the event ends up cancelled.
     *
     * @param denyReason a short reason, or {@code null} for a generic refusal
     */
    public void setDenyReason(String denyReason) {
        this.denyReason = denyReason;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

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
