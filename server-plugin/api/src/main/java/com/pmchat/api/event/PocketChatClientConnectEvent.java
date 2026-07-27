package com.pmchat.api.event;

import com.pmchat.api.PocketChatTier;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player's PocketChat client completes its handshake with the server —
 * that is, the moment you know this player is running the mod.
 *
 * <p>This is later than {@code PlayerJoinEvent}: the client sends its handshake once
 * the play connection is up, so a join listener asking
 * {@link com.pmchat.api.PocketChatApi#hasClient(Player)} may still get {@code false}.
 * Use this event instead of polling.
 *
 * <pre>{@code
 * @EventHandler
 * public void onPocketChat(PocketChatClientConnectEvent event) {
 *     event.getPlayer().sendMessage("PocketChat detected — enjoy the fancy chat!");
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class PocketChatClientConnectEvent extends PocketChatEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int clientProtocolVersion;
    private final PocketChatTier tier;

    public PocketChatClientConnectEvent(Player player, int clientProtocolVersion, PocketChatTier tier) {
        this.player = player;
        this.clientProtocolVersion = clientProtocolVersion;
        this.tier = tier;
    }

    /**
     * @return the player whose client just connected
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * The protocol version the <em>client</em> announced. Compare with
     * {@link com.pmchat.api.protocol.PocketChatProtocol#PROTOCOL_VERSION} to spot
     * outdated mods.
     *
     * @return the client's protocol version
     */
    public int getClientProtocolVersion() {
        return clientProtocolVersion;
    }

    /**
     * @return the tier the server advertised back to this client
     */
    public PocketChatTier getTier() {
        return tier;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
