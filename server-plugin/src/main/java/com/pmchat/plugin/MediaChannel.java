package com.pmchat.plugin;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
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
 */
final class MediaChannel implements PluginMessageListener {

    private static final int MAX_CONCURRENT_UPLOADS_PER_PLAYER = 3;
    private static final int MAX_CONCURRENT_DOWNLOADS_PER_PLAYER = 4;
    /** How many chunks to push per server tick when streaming a download (paces bandwidth). */
    private static final int CHUNKS_PER_TICK = 6;

    private final Plugin plugin;
    private final MediaStore store;
    private final int maxFileBytes;
    private final String tellCommand;
    private final boolean pro;

    // Gifts (bought with Vault coins)
    private final boolean giftsEnabled;
    private final List<Gift> catalog;
    private final GiftStore gifts;

    /** In-flight uploads, keyed by player then client-chosen transfer id. */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, Upload>> uploads = new ConcurrentHashMap<>();
    /** Active download streams per player, to cap concurrency. */
    private final ConcurrentHashMap<UUID, AtomicInteger> activeDownloads = new ConcurrentHashMap<>();

    MediaChannel(Plugin plugin, MediaStore store, int maxFileBytes, String tellCommand, boolean pro,
                 boolean giftsEnabled, List<Gift> catalog, GiftStore gifts) {
        this.plugin = plugin;
        this.store = store;
        this.maxFileBytes = maxFileBytes;
        this.tellCommand = tellCommand;
        this.pro = pro;
        this.giftsEnabled = giftsEnabled;
        this.catalog = catalog;
        this.gifts = gifts;
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
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PocketChatPlugin.CHANNEL.equals(channel) || message.length < 1) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            byte op = in.readByte();
            switch (op) {
                case Proto.HELLO -> handleHello(player);
                case Proto.UPLOAD_BEGIN -> handleUploadBegin(player, in);
                case Proto.UPLOAD_CHUNK -> handleUploadChunk(player, in);
                case Proto.UPLOAD_END -> handleUploadEnd(player, in);
                case Proto.DOWNLOAD_REQ -> handleDownloadReq(player, in);
                case Proto.PM_SEND -> handlePmSend(player, in);
                case Proto.GIFT_LIST_REQ -> handleGiftList(player);
                case Proto.GIFT_BUY -> handleGiftBuy(player, in);
                case Proto.GIFT_INV_REQ -> handleGiftInv(player, in);
                default -> { /* unknown opcode — ignore for forward-compat */ }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.FINE, "Malformed media message from " + player.getName(), e);
        }
    }

    // ---------- handshake ----------

    private void handleHello(Player player) {
        byte[] out = build(Proto.HELLO_ACK, dos -> {
            dos.writeInt(Proto.PROTOCOL_VERSION);
            dos.writeInt(maxFileBytes);
            dos.writeInt(Proto.CHUNK_BYTES);
            dos.writeByte(pro ? Proto.TIER_PRO : Proto.TIER_FREE);
        });
        send(player, out);
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
            runMain(() -> sendIfOnline(uuid, build(Proto.UPLOAD_OK, dos -> {
                dos.writeLong(transferId);
                dos.writeUTF(fileId);
            })));
        });
    }

    // ---------- download (server -> client), streamed off disk ----------

    private void handleDownloadReq(Player player, DataInputStream in) throws IOException {
        String fileId = in.readUTF();
        UUID uuid = player.getUniqueId();
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
        send(player, build(Proto.DOWNLOAD_BEGIN, dos -> {
            dos.writeUTF(fileId);
            dos.writeInt((int) Math.min(size, Integer.MAX_VALUE));
        }));
        new BukkitRunnable() {
            long offset = 0;
            final byte[] buf = new byte[Proto.CHUNK_BYTES];

            @Override
            public void run() {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null) {
                    finish();
                    return;
                }
                try {
                    for (int i = 0; i < CHUNKS_PER_TICK && offset < size; i++) {
                        int len = (int) Math.min(Proto.CHUNK_BYTES, size - offset);
                        raf.seek(offset);
                        raf.readFully(buf, 0, len);
                        long off = offset;
                        send(p, build(Proto.DOWNLOAD_CHUNK, dos -> {
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
                    send(p, build(Proto.DOWNLOAD_END, dos -> dos.writeUTF(fileId)));
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
        Player recipient = plugin.getServer().getPlayerExact(target);
        if (recipient == null || !recipient.isOnline()) {
            send(sender, build(Proto.PM_OFFLINE, dos -> dos.writeUTF(target)));
            return;
        }
        if (recipient.getListeningPluginChannels().contains(PocketChatPlugin.CHANNEL)) {
            send(recipient, build(Proto.PM_RECV, dos -> {
                dos.writeUTF(sender.getName());
                dos.writeUTF(wire);
            }));
        } else if (!plain.isEmpty()) {
            // Recipient has no mod — deliver as a normal whisper so they still get it.
            sender.performCommand(tellCommand + " " + recipient.getName() + " " + plain);
        }
    }

    // ---------- gifts (Vault) ----------

    /** Vault economy provider, or null when Vault / an economy plugin is absent. */
    private Economy economy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    private double balanceOf(Player player) {
        Economy eco = economy();
        return eco == null ? 0d : eco.getBalance(player);
    }

    private Gift findGift(String id) {
        for (Gift g : catalog) {
            if (g.id().equalsIgnoreCase(id)) return g;
        }
        return null;
    }

    private void handleGiftList(Player player) {
        double bal = balanceOf(player);
        send(player, build(Proto.GIFT_CATALOG, dos -> {
            dos.writeDouble(bal);
            dos.writeInt(giftsEnabled ? catalog.size() : 0);
            if (giftsEnabled) {
                for (Gift g : catalog) {
                    dos.writeUTF(g.id());
                    dos.writeUTF(g.name());
                    dos.writeUTF(g.icon());
                    dos.writeDouble(g.price());
                }
            }
        }));
    }

    private void handleGiftBuy(Player buyer, DataInputStream in) throws IOException {
        String target = in.readUTF();
        String giftId = in.readUTF();
        Gift gift = findGift(giftId);
        if (!giftsEnabled || gift == null) {
            send(buyer, giftResult(false, "Подарок недоступен", balanceOf(buyer)));
            return;
        }
        if (target.equalsIgnoreCase(buyer.getName())) {
            send(buyer, giftResult(false, "Нельзя дарить самому себе", balanceOf(buyer)));
            return;
        }
        Economy eco = economy();
        if (eco == null) {
            send(buyer, giftResult(false, "Экономика (Vault) недоступна", 0d));
            return;
        }
        if (eco.getBalance(buyer) < gift.price()) {
            send(buyer, giftResult(false, "Недостаточно монет", eco.getBalance(buyer)));
            return;
        }
        EconomyResponse resp = eco.withdrawPlayer(buyer, gift.price());
        if (resp == null || !resp.transactionSuccess()) {
            String reason = resp == null || resp.errorMessage == null ? "Ошибка оплаты" : resp.errorMessage;
            send(buyer, giftResult(false, reason, eco.getBalance(buyer)));
            return;
        }
        double newBal = eco.getBalance(buyer);
        gifts.add(target, gift.name(), gift.icon(), buyer.getName());
        send(buyer, giftResult(true, "Подарок отправлен: " + gift.icon() + " " + gift.name(), newBal));

        Player recipient = plugin.getServer().getPlayerExact(target);
        if (recipient != null && recipient.isOnline()) {
            if (recipient.getListeningPluginChannels().contains(PocketChatPlugin.CHANNEL)) {
                send(recipient, build(Proto.GIFT_RECV, dos -> {
                    dos.writeUTF(buyer.getName());
                    dos.writeUTF(gift.name());
                    dos.writeUTF(gift.icon());
                }));
            }
            recipient.sendMessage("§d" + gift.icon() + " §f" + buyer.getName()
                    + " §7подарил вам §f" + gift.name());
        }
    }

    private void handleGiftInv(Player player, DataInputStream in) throws IOException {
        String who = in.readUTF();
        List<String[]> list = gifts.get(who);
        send(player, build(Proto.GIFT_INV, dos -> {
            dos.writeUTF(who);
            dos.writeInt(list.size());
            for (String[] rec : list) {
                dos.writeUTF(rec.length > 0 ? rec[0] : "");
                dos.writeUTF(rec.length > 1 ? rec[1] : "");
                dos.writeUTF(rec.length > 2 ? rec[2] : "");
            }
        }));
    }

    private byte[] giftResult(boolean ok, String message, double newBalance) {
        return build(Proto.GIFT_RESULT, dos -> {
            dos.writeBoolean(ok);
            dos.writeUTF(message == null ? "" : message);
            dos.writeDouble(newBalance);
        });
    }

    // ---------- helpers ----------

    private byte[] uploadErr(long transferId, String reason) {
        return build(Proto.UPLOAD_ERR, dos -> {
            dos.writeLong(transferId);
            dos.writeUTF(reason);
        });
    }

    private byte[] downloadErr(String fileId, String reason) {
        return build(Proto.DOWNLOAD_ERR, dos -> {
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
        player.sendPluginMessage(plugin, PocketChatPlugin.CHANNEL, bytes);
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
