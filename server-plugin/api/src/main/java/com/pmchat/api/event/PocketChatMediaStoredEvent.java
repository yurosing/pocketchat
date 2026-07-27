package com.pmchat.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired once an upload has been fully received and committed to the media store, right
 * before the uploader is handed its file id.
 *
 * <p>Useful for auditing, mirroring media to external storage, or running content checks.
 * Read the bytes with {@link com.pmchat.api.MediaService}, and remember to do it off the
 * main thread.
 *
 * <pre>{@code
 * @EventHandler
 * public void onStored(PocketChatMediaStoredEvent event) {
 *     getLogger().info(event.getPlayer().getName() + " uploaded " + event.getFileId()
 *             + " (" + event.getSizeBytes() + " bytes)");
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class PocketChatMediaStoredEvent extends PocketChatEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String fileId;
    private final String extension;
    private final long sizeBytes;

    public PocketChatMediaStoredEvent(Player player, String fileId, String extension, long sizeBytes) {
        this.player = player;
        this.fileId = fileId;
        this.extension = extension;
        this.sizeBytes = sizeBytes;
    }

    /**
     * @return the player who uploaded the file
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * The id the file is stored under, e.g. {@code "a1B2c3D4e5F6g7H8.png"}. Also the
     * capability token the recipient will use to download it — do not leak it publicly.
     *
     * @return the file id
     */
    public String getFileId() {
        return fileId;
    }

    /**
     * @return the stored extension, normalised and lowercased ({@code bin} when the
     *         client announced something unusable)
     */
    public String getExtension() {
        return extension;
    }

    /**
     * @return the size on disk in bytes
     */
    public long getSizeBytes() {
        return sizeBytes;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
