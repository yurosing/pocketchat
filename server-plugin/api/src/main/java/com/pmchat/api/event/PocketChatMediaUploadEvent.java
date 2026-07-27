package com.pmchat.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when a client asks to upload a photo, voice note or video, <b>before</b> a single
 * byte is written to disk. This is the place to enforce permissions, per-rank size limits
 * or file-type rules — PocketChat itself only checks the global {@code max-file-mb}.
 *
 * <p>Cancelling refuses the upload. Set a {@link #setDenyReason(String) reason} and the
 * client shows it to the player; without one they see a generic refusal.
 *
 * <pre>{@code
 * @EventHandler
 * public void onUpload(PocketChatMediaUploadEvent event) {
 *     if (!event.getPlayer().hasPermission("myserver.pocketchat.media")) {
 *         event.setDenyReason("Media sharing is a donator perk");
 *         event.setCancelled(true);
 *     } else if (event.getSizeBytes() > 4 * 1024 * 1024) {
 *         event.setDenyReason("Files are capped at 4 MB here");
 *         event.setCancelled(true);
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class PocketChatMediaUploadEvent extends PocketChatEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String extension;
    private final int sizeBytes;
    private String denyReason;
    private boolean cancelled;

    public PocketChatMediaUploadEvent(Player player, String extension, int sizeBytes) {
        this.player = player;
        this.extension = extension;
        this.sizeBytes = sizeBytes;
    }

    /**
     * @return the player uploading
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * The file extension the client announced, without the dot — {@code png}, {@code ogg},
     * {@code mp4} and so on. Not validated yet at this point, and not trustworthy: it is
     * whatever the client claimed.
     *
     * @return the announced extension
     */
    public String getExtension() {
        return extension;
    }

    /**
     * The announced total size in bytes. PocketChat enforces this against
     * {@code max-file-mb} and aborts the transfer if the client lies about it.
     *
     * @return the announced size in bytes
     */
    public int getSizeBytes() {
        return sizeBytes;
    }

    /**
     * @return the reason shown to the player when the upload is refused, or {@code null}
     */
    public String getDenyReason() {
        return denyReason;
    }

    /**
     * Sets the short reason the client displays when the upload is refused. Only used
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
