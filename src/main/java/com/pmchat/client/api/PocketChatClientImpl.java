package com.pmchat.client.api;

import com.pmchat.api.client.Conversation;
import com.pmchat.api.client.ConversationKind;
import com.pmchat.api.client.PmChatMessage;
import com.pmchat.api.client.PocketChatClient;
import com.pmchat.api.client.PocketChatClientApi;
import com.pmchat.api.client.PocketChatListener;
import com.pmchat.api.client.ServerTier;
import com.pmchat.client.PmChatClient;
import com.pmchat.client.PmHistory;
import com.pmchat.client.PmMessage;
import com.pmchat.client.PmServerMedia;
import com.pmchat.client.PmToast;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementation of the public {@link PocketChatClientApi}, installed once during mod
 * init. Everything here delegates to the mod's existing entry points — this class adds
 * no behaviour of its own, it only exposes a stable shape to other mods.
 *
 * <p>All fire methods are static so the call sites scattered through
 * {@link PmChatClient} stay one-liners, and they no-op when the API has not been
 * installed (which never happens in practice, but keeps the mod runnable in tests).
 */
public final class PocketChatClientImpl implements PocketChatClientApi {

    private static final CopyOnWriteArrayList<PocketChatListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile ServerTier lastTier = ServerTier.NONE;

    private PocketChatClientImpl() {
    }

    /** Installs the API. Called once from {@code PmChatClient.onInitializeClient}. */
    public static void install() {
        PocketChatClient.install(new PocketChatClientImpl());
    }

    // ---------- fire points, called from PmChatClient ----------

    /** An incoming message was stored. */
    public static void fireReceived(String conversation, PmMessage msg) {
        if (LISTENERS.isEmpty()) return;
        PmChatMessage view = view(conversation, msg);
        for (PocketChatListener l : LISTENERS) {
            try {
                l.onMessageReceived(view);
            } catch (Exception e) {
                PmChatClient.LOGGER.warn("PocketChat API listener failed in onMessageReceived", e);
            }
        }
    }

    /** An outgoing message was stored and dispatched. */
    public static void fireSent(String conversation, PmMessage msg) {
        if (LISTENERS.isEmpty()) return;
        PmChatMessage view = view(conversation, msg);
        for (PocketChatListener l : LISTENERS) {
            try {
                l.onMessageSent(view);
            } catch (Exception e) {
                PmChatClient.LOGGER.warn("PocketChat API listener failed in onMessageSent", e);
            }
        }
    }

    /**
     * Asks every listener whether an outgoing message may go out.
     *
     * @return false when any listener vetoed it
     */
    public static boolean allowOutgoing(String target, String text) {
        for (PocketChatListener l : LISTENERS) {
            try {
                if (!l.allowOutgoing(target, text)) return false;
            } catch (Exception e) {
                PmChatClient.LOGGER.warn("PocketChat API listener failed in allowOutgoing", e);
            }
        }
        return true;
    }

    /** A gift arrived for the local player. */
    public static void fireGift(String from, String giftName, String icon) {
        for (PocketChatListener l : LISTENERS) {
            try {
                l.onGiftReceived(from, giftName, icon);
            } catch (Exception e) {
                PmChatClient.LOGGER.warn("PocketChat API listener failed in onGiftReceived", e);
            }
        }
    }

    /** The player switched to a conversation in the messenger window. */
    public static void fireConversationOpened(String conversationId) {
        if (conversationId == null || LISTENERS.isEmpty()) return;
        for (PocketChatListener l : LISTENERS) {
            try {
                l.onConversationOpened(conversationId);
            } catch (Exception e) {
                PmChatClient.LOGGER.warn("PocketChat API listener failed in onConversationOpened", e);
            }
        }
    }

    /** The detected server plugin changed. Only fires on an actual change. */
    public static void fireServerTier(ServerTier tier) {
        if (tier == lastTier) return;
        lastTier = tier;
        for (PocketChatListener l : LISTENERS) {
            try {
                l.onServerTierChanged(tier);
            } catch (Exception e) {
                PmChatClient.LOGGER.warn("PocketChat API listener failed in onServerTierChanged", e);
            }
        }
    }

    // ---------- PocketChatClientApi ----------

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(PmChatClient.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public void addListener(PocketChatListener listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    @Override
    public boolean removeListener(PocketChatListener listener) {
        return listener != null && LISTENERS.remove(listener);
    }

    @Override
    public String selfName() {
        return PmChatClient.selfName();
    }

    @Override
    public ServerTier serverTier() {
        return currentTier();
    }

    @Override
    public List<Conversation> conversations() {
        PmHistory history = PmChatClient.getHistory();
        List<Conversation> out = new ArrayList<>();
        for (String name : history.conversationNames()) {
            out.add(new Conversation(name, displayName(name), kindOf(name),
                    history.unreadCount(name), history.lastTime(name)));
        }
        return List.copyOf(out);
    }

    @Override
    public List<PmChatMessage> messages(String conversationId) {
        if (conversationId == null) return List.of();
        List<PmChatMessage> out = new ArrayList<>();
        for (PmMessage m : PmChatClient.getHistory().messages(conversationId)) {
            out.add(view(conversationId, m));
        }
        return List.copyOf(out);
    }

    @Override
    public boolean isBlocked(String player) {
        return PmChatClient.isBlocked(player);
    }

    @Override
    public String knownBalance() {
        String bal = PmChatClient.knownBalance();
        return bal == null ? "" : bal;
    }

    @Override
    public boolean send(String target, String text) {
        if (target == null || text == null || target.isBlank() || text.isBlank()) return false;
        return PmChatClient.sendMessage(target, text) != null;
    }

    @Override
    public void sendGlobal(String text) {
        if (text != null && !text.isBlank()) PmChatClient.sendGlobal(text);
    }

    @Override
    public void open(String conversationId) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> PmChatClient.openMessenger(client, conversationId));
    }

    @Override
    public void toast(String title, String body) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.getToastManager().add(
                new PmToast(title == null ? "" : title, body == null ? "" : body)));
    }

    @Override
    public void setBlocked(String player, boolean blocked) {
        PmChatClient.setBlocked(player, blocked);
    }

    // ---------- mapping helpers ----------

    /** Snapshot of an internal {@link PmMessage} as the immutable public record. */
    private static PmChatMessage view(String conversation, PmMessage m) {
        String sender = m.out ? PmChatClient.selfName()
                : (m.sender != null && !m.sender.isEmpty() ? m.sender : conversation);
        return new PmChatMessage(conversation, sender, m.text, m.time, m.out, m.money,
                m.replyTo, PmHistory.msgHash(m.text));
    }

    /** Maps an internal conversation id to its public kind via the mod's id prefixes. */
    private static ConversationKind kindOf(String id) {
        if (id == null) return ConversationKind.DIRECT;
        if (PmChatClient.GLOBAL.equals(id)) return ConversationKind.GLOBAL;
        if (PmChatClient.SAVED.equals(id)) return ConversationKind.SAVED;
        if (PmChatClient.COREPROTECT.equals(id)) return ConversationKind.COREPROTECT;
        if (id.startsWith(PmChatClient.CHANNEL_PREFIX)) return ConversationKind.CHANNEL;
        if (id.startsWith(PmChatClient.GROUP_PREFIX)) return ConversationKind.GROUP;
        if (id.startsWith(PmChatClient.BCAST_PREFIX)) return ConversationKind.BROADCAST;
        return ConversationKind.DIRECT;
    }

    /** Human-readable name for a thread; service tabs use their own labels. */
    private static String displayName(String id) {
        return switch (kindOf(id)) {
            case GLOBAL -> "Global";
            case SAVED -> "Saved";
            case COREPROTECT -> "CoreProtect";
            case CHANNEL, GROUP, BROADCAST -> {
                int colon = id.indexOf(':');
                yield colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
            }
            case DIRECT -> id;
        };
    }

    /** Reads the current tier straight from the plugin-channel state. */
    private static ServerTier currentTier() {
        PmServerMedia media = PmServerMedia.get();
        if (!media.isAvailable()) return ServerTier.NONE;
        return media.isPro() ? ServerTier.PRO : ServerTier.FREE;
    }

    /** Re-reads the tier and notifies listeners if it changed. Cheap; safe to call often. */
    public static void refreshServerTier() {
        fireServerTier(currentTier());
    }
}
