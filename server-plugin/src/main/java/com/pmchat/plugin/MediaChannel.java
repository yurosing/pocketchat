package com.pmchat.plugin;

import com.pmchat.api.Gift;
import com.pmchat.api.PocketChat;
import com.pmchat.api.ReceivedGift;
import com.pmchat.api.event.PocketChatClientConnectEvent;
import com.pmchat.api.event.PocketChatGiftPurchaseEvent;
import com.pmchat.api.event.PocketChatGiftReceiveEvent;
import com.pmchat.api.event.PocketChatMediaDownloadEvent;
import com.pmchat.api.event.PocketChatMediaStoredEvent;
import com.pmchat.api.event.PocketChatMediaUploadEvent;
import com.pmchat.api.event.PocketChatMessageEvent;
import com.pmchat.api.event.PocketChatMessageOfflineEvent;
import com.pmchat.api.protocol.PocketChatProtocol;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Handles the media channel: reassembles chunked uploads into a temp file on disk
 * (never the whole file in RAM), streams stored media back to clients off disk a
 * few chunks per tick, and routes private messages. Incoming messages arrive on the
 * main server thread; the heavier work (commit/move, resolve) is offloaded async.
 *
 * <p>Every externally interesting step fires one of the events in
 * {@code com.pmchat.api.event}. Async legs hop back to the main thread through
 * {@link #runMain(Runnable)} before firing, so listeners always run on the main thread.
 */
final class MediaChannel implements PluginMessageListener {

    private static final int MAX_CONCURRENT_UPLOADS_PER_PLAYER = 3;
    private static final int MAX_CONCURRENT_DOWNLOADS_PER_PLAYER = 4;
    /** How many chunks to push per server tick when streaming a download (paces bandwidth). */
    private static final int CHUNKS_PER_TICK = 6;

    private final Plugin plugin;
    private final PocketChatApiImpl api;
    private final MediaStore store;
    private final int maxFileBytes;
    private final String tellCommand;
    private final boolean pro;

    // Gifts (bought with Vault coins)
    private final boolean giftsEnabled;
    private final GiftStore gifts;

    // Streams (announcement + Vault donations)
    private final StreamManager streams;

    /** In-flight uploads, keyed by player then client-chosen transfer id. */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, Upload>> uploads = new ConcurrentHashMap<>();
    /** Active download streams per player, to cap concurrency. */
    private final ConcurrentHashMap<UUID, AtomicInteger> activeDownloads = new ConcurrentHashMap<>();

    MediaChannel(Plugin plugin, PocketChatApiImpl api, MediaStore store, int maxFileBytes,
                 String tellCommand, boolean pro, boolean giftsEnabled, GiftStore gifts,
                 StreamManager streams) {
        this.plugin = plugin;
        this.api = api;
        this.store = store;
        this.maxFileBytes = maxFileBytes;
        this.tellCommand = tellCommand;
        this.pro = pro;
        this.giftsEnabled = giftsEnabled;
        this.gifts = gifts;
        this.streams = streams;
    }

    private static final class Upload {
        final Path temp;
        final RandomAccessFile raf;
        final String ext;
        final long total;
        long received;

        Upload(Path temp, RandomAccessFile raf, String ext, long total) {
            this.temp = temp;
            this.raf = raf;
            this.ext = ext;
            this.total = total;
        }

        void closeQuiet() {
            try {
                raf.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Drop and clean up any in-flight uploads for a player who left. */
    void forget(UUID player) {
        ConcurrentHashMap<Long, Upload> mine = uploads.remove(player);
        if (mine != null) {
            for (Upload up : mine.values()) {
                up.closeQuiet();
                store.deleteQuiet(up.temp);
            }
        }
        activeDownloads.remove(player);
        if (streams.stop(player)) broadcastStreams();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PocketChat.CHANNEL.equals(channel) || message.length < 1) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            byte op = in.readByte();
            switch (op) {
                case PocketChatProtocol.HELLO -> handleHello(player, in);
                case PocketChatProtocol.UPLOAD_BEGIN -> handleUploadBegin(player, in);
                case PocketChatProtocol.UPLOAD_CHUNK -> handleUploadChunk(player, in);
                case PocketChatProtocol.UPLOAD_END -> handleUploadEnd(player, in);
                case PocketChatProtocol.DOWNLOAD_REQ -> handleDownloadReq(player, in);
                case PocketChatProtocol.PM_SEND -> handlePmSend(player, in);
                case PocketChatProtocol.GIFT_LIST_REQ -> handleGiftList(player);
                case PocketChatProtocol.GIFT_BUY -> handleGiftBuy(player, in);
                case PocketChatProtocol.GIFT_INV_REQ -> handleGiftInv(player, in);
                case PocketChatProtocol.STREAM_START -> handleStreamStart(player, in);
                case PocketChatProtocol.STREAM_STOP -> handleStreamStop(player);
                case PocketChatProtocol.STREAM_LIST_REQ -> send(player, streamList());
                case PocketChatProtocol.STREAM_DONATE -> handleStreamDonate(player, in);
                default -> { /* unknown opcode — ignore for forward-compat */ }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.FINE, "Malformed media message from " + player.getName(), e);
        }
    }

    // ---------- handshake ----------

    private void handleHello(Player player, DataInputStream in) throws IOException {
        int clientVersion = in.available() >= 4 ? in.readInt() : 0;
        byte[] out = build(PocketChatProtocol.HELLO_ACK, dos -> {
            dos.writeInt(PocketChatProtocol.PROTOCOL_VERSION);
            dos.writeInt(maxFileBytes);
            dos.writeInt(PocketChatProtocol.CHUNK_BYTES);
            dos.writeByte(pro ? PocketChatProtocol.TIER_PRO : PocketChatProtocol.TIER_FREE);
        });
        send(player, out);
        api.fire(new PocketChatClientConnectEvent(player, clientVersion, api.tier()));
    }

    // ---------- upload (client -> server), streamed to a temp file ----------

    private void handleUploadBegin(Player player, DataInputStream in) throws IOException {
        long transferId = in.readLong();
        int total = in.readInt();
        String ext = in.readUTF();
        in.readByte(); // kind — reserved, not used yet
        if (total <= 0 || total > maxFileBytes) {
            send(player, uploadErr(transferId, "too large"));
            return;
        }
        PocketChatMediaUploadEvent event =
                api.fire(new PocketChatMediaUploadEvent(player, ext, total));
        if (event.isCancelled()) {
            String reason = event.getDenyReason();
            send(player, uploadErr(transferId, reason == null || reason.isBlank() ? "denied" : reason));
            return;
        }
        ConcurrentHashMap<Long, Upload> mine = uploads.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        if (mine.size() >= MAX_CONCURRENT_UPLOADS_PER_PLAYER) {
            send(player, uploadErr(transferId, "too many uploads"));
            return;
        }
        try {
            Path temp = store.newTemp();
            RandomAccessFile raf = new RandomAccessFile(temp.toFile(), "rw");
            mine.put(transferId, new Upload(temp, raf, ext, total));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not open temp upload: " + e);
            send(player, uploadErr(transferId, "server error"));
        }
    }

    private void handleUploadChunk(Player player, DataInputStream in) throws IOException {
        long transferId = in.readLong();
        int offset = in.readInt();
        int len = in.readInt();
        byte[] chunk = new byte[Math.max(0, len)];
        in.readFully(chunk);
        ConcurrentHashMap<Long, Upload> mine = uploads.get(player.getUniqueId());
        Upload up = mine == null ? null : mine.get(transferId);
        if (up == null) return;
        if (offset < 0 || len < 0 || (long) offset + len > up.total) {
            mine.remove(transferId);
            up.closeQuiet();
            store.deleteQuiet(up.temp);
            send(player, uploadErr(transferId, "bad chunk"));
            return;
        }
        try {
            up.raf.seek(offset);
            up.raf.write(chunk, 0, len);
            up.received += len;
        } catch (IOException e) {
            mine.remove(transferId);
            up.closeQuiet();
            store.deleteQuiet(up.temp);
            send(player, uploadErr(transferId, "write failed"));
        }
    }

    private void handleUploadEnd(Player player, DataInputStream in) throws IOException {
        long transferId = in.readLong();
        ConcurrentHashMap<Long, Upload> mine = uploads.get(player.getUniqueId());
        Upload up = mine == null ? null : mine.remove(transferId);
        if (up == null) {
            send(player, uploadErr(transferId, "unknown transfer"));
            return;
        }
        up.closeQuiet();
        if (up.received != up.total) {
            store.deleteQuiet(up.temp);
            send(player, uploadErr(transferId, "incomplete"));
            return;
        }
        UUID uuid = player.getUniqueId();
        long size = up.total;
        runAsync(() -> {
            String fileId;
            try {
                fileId = store.commit(up.temp, up.ext);
            } catch (IOException e) {
                plugin.getLogger().warning("Media commit failed: " + e);
                store.deleteQuiet(up.temp);
                runMain(() -> sendIfOnline(uuid, uploadErr(transferId, "store failed")));
                return;
            }
            runMain(() -> {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null) {
                    String ext = fileId.substring(fileId.lastIndexOf('.') + 1);
                    api.fire(new PocketChatMediaStoredEvent(p, fileId, ext, size));
                }
                sendIfOnline(uuid, build(PocketChatProtocol.UPLOAD_OK, dos -> {
                    dos.writeLong(transferId);
                    dos.writeUTF(fileId);
                }));
            });
        });
    }

    // ---------- download (server -> client), streamed off disk ----------

    private void handleDownloadReq(Player player, DataInputStream in) throws IOException {
        String fileId = in.readUTF();
        UUID uuid = player.getUniqueId();
        PocketChatMediaDownloadEvent event =
                api.fire(new PocketChatMediaDownloadEvent(player, fileId));
        if (event.isCancelled()) {
            String reason = event.getDenyReason();
            send(player, downloadErr(fileId, reason == null || reason.isBlank() ? "denied" : reason));
            return;
        }
        AtomicInteger active = activeDownloads.computeIfAbsent(uuid, k -> new AtomicInteger());
        if (active.get() >= MAX_CONCURRENT_DOWNLOADS_PER_PLAYER) {
            send(player, downloadErr(fileId, "too many downloads"));
            return;
        }
        runAsync(() -> {
            Path path = store.resolve(fileId);
            long size = -1;
            if (path != null) {
                try {
                    size = Files.size(path);
                } catch (IOException ignored) {
                    size = -1;
                }
            }
            if (path == null || size < 0) {
                runMain(() -> sendIfOnline(uuid, downloadErr(fileId, "not found")));
                return;
            }
            long finalSize = size;
            Path finalPath = path;
            runMain(() -> streamDownload(uuid, fileId, finalPath, finalSize, active));
        });
    }

    /** Opens the file and pushes it a few chunks per tick on the main thread, then closes. */
    private void streamDownload(UUID uuid, String fileId, Path path, long size, AtomicInteger active) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) return;
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(path.toFile(), "r");
        } catch (IOException e) {
            send(player, downloadErr(fileId, "open failed"));
            return;
        }
        active.incrementAndGet();
        send(player, build(PocketChatProtocol.DOWNLOAD_BEGIN, dos -> {
            dos.writeUTF(fileId);
            dos.writeInt((int) Math.min(size, Integer.MAX_VALUE));
        }));
        new BukkitRunnable() {
            long offset = 0;
            final byte[] buf = new byte[PocketChatProtocol.CHUNK_BYTES];

            @Override
            public void run() {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null) {
                    finish();
                    return;
                }
                try {
                    for (int i = 0; i < CHUNKS_PER_TICK && offset < size; i++) {
                        int len = (int) Math.min(PocketChatProtocol.CHUNK_BYTES, size - offset);
                        raf.seek(offset);
                        raf.readFully(buf, 0, len);
                        long off = offset;
                        send(p, build(PocketChatProtocol.DOWNLOAD_CHUNK, dos -> {
                            dos.writeUTF(fileId);
                            dos.writeInt((int) off);
                            dos.writeInt(len);
                            dos.write(buf, 0, len);
                        }));
                        offset += len;
                    }
                } catch (IOException e) {
                    send(p, downloadErr(fileId, "read failed"));
                    finish();
                    return;
                }
                if (offset >= size) {
                    send(p, build(PocketChatProtocol.DOWNLOAD_END, dos -> dos.writeUTF(fileId)));
                    finish();
                }
            }

            private void finish() {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
                active.decrementAndGet();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---------- private-message routing ----------

    private void handlePmSend(Player sender, DataInputStream in) throws IOException {
        String target = in.readUTF();
        String wire = in.readUTF();
        String plain = in.readUTF();
        routePm(sender, target, wire, plain);
    }

    /**
     * Delivers one private message, firing {@link PocketChatMessageEvent} first. Shared by
     * the wire handler and {@link com.pmchat.api.PocketChatApi#sendMessage}.
     *
     * @return true when the message reached the recipient
     */
    boolean routePm(Player sender, String target, String wire, String plain) {
        Player recipient = plugin.getServer().getPlayerExact(target);
        boolean online = recipient != null && recipient.isOnline();
        boolean hasMod = online && recipient.getListeningPluginChannels().contains(PocketChat.CHANNEL);

        PocketChatMessageEvent.DeliveryMode mode = !online
                ? PocketChatMessageEvent.DeliveryMode.OFFLINE
                : hasMod ? PocketChatMessageEvent.DeliveryMode.MOD
                : PocketChatMessageEvent.DeliveryMode.FALLBACK_WHISPER;

        PocketChatMessageEvent event = api.fire(new PocketChatMessageEvent(
                sender, target, online ? recipient : null, wire, plain, mode));
        if (event.isCancelled()) return false;

        if (!online) {
            PocketChatMessageOfflineEvent offline =
                    api.fire(new PocketChatMessageOfflineEvent(sender, target, wire, event.getPlain()));
            if (!offline.isHandled()) {
                send(sender, build(PocketChatProtocol.PM_OFFLINE, dos -> dos.writeUTF(target)));
            }
            return false;
        }
        if (hasMod) {
            pushPm(recipient, sender.getName(), wire);
        } else if (!event.getPlain().isEmpty()) {
            // Recipient has no mod — deliver as a normal whisper so they still get it.
            sender.performCommand(tellCommand + " " + recipient.getName() + " " + event.getPlain());
        }
        return true;
    }

    /** Pushes a message straight into a modded client's chat thread. */
    void pushPm(Player recipient, String senderName, String wire) {
        send(recipient, build(PocketChatProtocol.PM_RECV, dos -> {
            dos.writeUTF(senderName);
            dos.writeUTF(wire);
        }));
    }

    // ---------- gifts (Vault) ----------

    private double balanceOf(Player player) {
        Economy eco = api.economy();
        return eco == null ? 0d : eco.getBalance(player);
    }

    private void handleGiftList(Player player) {
        double bal = balanceOf(player);
        List<Gift> catalog = giftsEnabled ? api.all() : List.of();
        send(player, build(PocketChatProtocol.GIFT_CATALOG, dos -> {
            dos.writeDouble(bal);
            dos.writeInt(catalog.size());
            for (Gift g : catalog) {
                dos.writeUTF(g.id());
                dos.writeUTF(g.name());
                dos.writeUTF(g.icon());
                dos.writeDouble(g.price());
            }
        }));
    }

    private void handleGiftBuy(Player buyer, DataInputStream in) throws IOException {
        String target = in.readUTF();
        String giftId = in.readUTF();
        Gift gift = api.byId(giftId);
        if (!giftsEnabled || gift == null) {
            send(buyer, giftResult(false, "Подарок недоступен", balanceOf(buyer)));
            return;
        }
        if (target.equalsIgnoreCase(buyer.getName())) {
            send(buyer, giftResult(false, "Нельзя дарить самому себе", balanceOf(buyer)));
            return;
        }

        PocketChatGiftPurchaseEvent purchase =
                api.fire(new PocketChatGiftPurchaseEvent(buyer, target, gift, gift.price()));
        if (purchase.isCancelled()) {
            String why = purchase.getFailureMessage();
            send(buyer, giftResult(false, why == null || why.isBlank() ? "Покупка отклонена" : why,
                    balanceOf(buyer)));
            return;
        }
        double price = purchase.getPrice();

        Economy eco = api.economy();
        if (eco == null) {
            send(buyer, giftResult(false, "Экономика (Vault) недоступна", 0d));
            return;
        }
        if (eco.getBalance(buyer) < price) {
            send(buyer, giftResult(false, "Недостаточно монет", eco.getBalance(buyer)));
            return;
        }
        if (price > 0d) {
            EconomyResponse resp = eco.withdrawPlayer(buyer, price);
            if (resp == null || !resp.transactionSuccess()) {
                String reason = resp == null || resp.errorMessage == null ? "Ошибка оплаты" : resp.errorMessage;
                send(buyer, giftResult(false, reason, eco.getBalance(buyer)));
                return;
            }
        }
        double newBal = eco.getBalance(buyer);
        gifts.add(target, gift.name(), gift.icon(), buyer.getName());
        send(buyer, giftResult(true, "Подарок отправлен: " + gift.icon() + " " + gift.name(), newBal));
        announceGift(target, gift, buyer.getName(), price);
    }

    /**
     * Notifies the recipient of a granted gift and fires {@link PocketChatGiftReceiveEvent},
     * which may rewrite or suppress the chat line. Shared by purchases and
     * {@link com.pmchat.api.PocketChatApi#giveGift}.
     */
    void announceGift(String targetName, Gift gift, String from, double pricePaid) {
        Player recipient = plugin.getServer().getPlayerExact(targetName);
        PocketChatGiftReceiveEvent event = api.fire(new PocketChatGiftReceiveEvent(
                targetName, recipient, gift, from, pricePaid,
                PocketChatApiImpl.defaultAnnouncement(gift, from)));
        if (recipient == null || !recipient.isOnline()) return;
        if (recipient.getListeningPluginChannels().contains(PocketChat.CHANNEL)) {
            send(recipient, build(PocketChatProtocol.GIFT_RECV, dos -> {
                dos.writeUTF(from);
                dos.writeUTF(gift.name());
                dos.writeUTF(gift.icon());
            }));
        }
        String line = event.getAnnouncement();
        if (line != null && !line.isEmpty()) recipient.sendMessage(line);
    }

    private void handleGiftInv(Player player, DataInputStream in) throws IOException {
        String who = in.readUTF();
        List<ReceivedGift> list = gifts.received(who);
        send(player, build(PocketChatProtocol.GIFT_INV, dos -> {
            dos.writeUTF(who);
            dos.writeInt(list.size());
            for (ReceivedGift rec : list) {
                dos.writeUTF(rec.name());
                dos.writeUTF(rec.icon());
                dos.writeUTF(rec.from());
            }
        }));
    }

    private byte[] giftResult(boolean ok, String message, double newBalance) {
        return build(PocketChatProtocol.GIFT_RESULT, dos -> {
            dos.writeBoolean(ok);
            dos.writeUTF(message == null ? "" : message);
            dos.writeDouble(newBalance);
        });
    }

    // ---------- streams (announcement + Vault donations) ----------

    private static final int MAX_STREAM_TEXT = 96;

    private void handleStreamStart(Player player, DataInputStream in) throws IOException {
        String title = clip(in.readUTF(), MAX_STREAM_TEXT);
        String url = clip(in.readUTF(), MAX_STREAM_TEXT);
        streams.start(player.getUniqueId(), player.getName(), title, url);
        broadcastStreams();
    }

    private void handleStreamStop(Player player) {
        if (streams.stop(player.getUniqueId())) broadcastStreams();
    }

    private byte[] streamList() {
        List<StreamManager.Live> list = streams.list();
        return build(PocketChatProtocol.STREAM_LIST, dos -> {
            dos.writeInt(list.size());
            for (StreamManager.Live l : list) {
                dos.writeUTF(l.player());
                dos.writeUTF(l.title());
                dos.writeUTF(l.url());
            }
        });
    }

    private void broadcastStreams() {
        byte[] msg = streamList();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getListeningPluginChannels().contains(PocketChat.CHANNEL)) send(p, msg);
        }
    }

    private void handleStreamDonate(Player donor, DataInputStream in) throws IOException {
        String target = in.readUTF();
        double amount = in.readDouble();
        if (target.equalsIgnoreCase(donor.getName())) {
            send(donor, streamDonateResult(false, "Нельзя донатить самому себе", balanceOf(donor)));
            return;
        }
        if (!(amount > 0d) || !Double.isFinite(amount)) {
            send(donor, streamDonateResult(false, "Некорректная сумма", balanceOf(donor)));
            return;
        }
        if (!streams.isLive(target)) {
            send(donor, streamDonateResult(false, "Игрок сейчас не стримит", balanceOf(donor)));
            return;
        }
        Economy eco = api.economy();
        if (eco == null) {
            send(donor, streamDonateResult(false, "Экономика (Vault) недоступна", 0d));
            return;
        }
        if (eco.getBalance(donor) < amount) {
            send(donor, streamDonateResult(false, "Недостаточно монет", eco.getBalance(donor)));
            return;
        }
        EconomyResponse withdraw = eco.withdrawPlayer(donor, amount);
        if (withdraw == null || !withdraw.transactionSuccess()) {
            String reason = withdraw == null || withdraw.errorMessage == null ? "Ошибка оплаты" : withdraw.errorMessage;
            send(donor, streamDonateResult(false, reason, eco.getBalance(donor)));
            return;
        }
        Player recipient = plugin.getServer().getPlayerExact(target);
        if (recipient != null) {
            eco.depositPlayer(recipient, amount);
        } else {
            // Стример вышел между проверкой и переводом — возвращаем деньги донатеру.
            eco.depositPlayer(donor, amount);
            send(donor, streamDonateResult(false, "Игрок вышел, донат отменён", eco.getBalance(donor)));
            return;
        }
        send(donor, streamDonateResult(true, "Донат отправлен: " + fmtCoins(amount), eco.getBalance(donor)));
        if (recipient.getListeningPluginChannels().contains(PocketChat.CHANNEL)) {
            send(recipient, build(PocketChatProtocol.STREAM_DONATE_RECV, dos -> {
                dos.writeUTF(donor.getName());
                dos.writeDouble(amount);
            }));
        }
        recipient.sendMessage(donor.getName() + " задонатил(а) вам " + fmtCoins(amount) + " на стриме!");
    }

    private byte[] streamDonateResult(boolean ok, String message, double newBalance) {
        return build(PocketChatProtocol.STREAM_DONATE_RESULT, dos -> {
            dos.writeBoolean(ok);
            dos.writeUTF(message == null ? "" : message);
            dos.writeDouble(newBalance);
        });
    }

    private static String fmtCoins(double d) {
        long l = (long) d;
        return d == l ? Long.toString(l) : String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ---------- helpers ----------

    private byte[] uploadErr(long transferId, String reason) {
        return build(PocketChatProtocol.UPLOAD_ERR, dos -> {
            dos.writeLong(transferId);
            dos.writeUTF(reason);
        });
    }

    private byte[] downloadErr(String fileId, String reason) {
        return build(PocketChatProtocol.DOWNLOAD_ERR, dos -> {
            dos.writeUTF(fileId);
            dos.writeUTF(reason);
        });
    }

    private interface Writer {
        void write(DataOutputStream dos) throws IOException;
    }

    private byte[] build(byte opcode, Writer body) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeByte(opcode);
            body.write(dos);
        } catch (IOException e) {
            throw new RuntimeException(e); // in-memory stream: never happens
        }
        return bos.toByteArray();
    }

    private void send(Player player, byte[] bytes) {
        player.sendPluginMessage(plugin, PocketChat.CHANNEL, bytes);
    }

    private void sendIfOnline(UUID uuid, byte[] bytes) {
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) send(p, bytes);
    }

    private void runAsync(Runnable r) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, r);
    }

    private void runMain(Runnable r) {
        plugin.getServer().getScheduler().runTask(plugin, r);
    }
}
