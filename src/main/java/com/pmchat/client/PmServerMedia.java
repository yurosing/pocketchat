package com.pmchat.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client side of the {@code pmchat:media} channel: when the server runs the
 * PocketChatMedia plugin, this relays photos/voice/video THROUGH the server
 * instead of external file hosts (host code {@code "s"}). Falls back silently —
 * callers only route here when {@link #isAvailable()} is true.
 *
 * Threading: media byte payloads are enqueued from background upload/download
 * threads and actually sent on the client thread by {@link #tick()}, which paces
 * them a few per tick so a multi-megabyte transfer doesn't flood the connection.
 * Incoming messages are handled on the client thread by {@link #handle(byte[])}.
 */
public final class PmServerMedia {

    // Opcodes — must match the server plugin's Proto.
    private static final byte HELLO = 0x01;
    private static final byte HELLO_ACK = 0x02;
    private static final byte UPLOAD_BEGIN = 0x10;
    private static final byte UPLOAD_CHUNK = 0x11;
    private static final byte UPLOAD_END = 0x12;
    private static final byte UPLOAD_OK = 0x13;
    private static final byte UPLOAD_ERR = 0x14;
    private static final byte DOWNLOAD_REQ = 0x20;
    private static final byte DOWNLOAD_BEGIN = 0x21;
    private static final byte DOWNLOAD_CHUNK = 0x22;
    private static final byte DOWNLOAD_END = 0x23;
    private static final byte DOWNLOAD_ERR = 0x24;
    private static final byte PM_SEND = 0x30;
    private static final byte PM_RECV = 0x31;
    private static final byte PM_OFFLINE = 0x32;
    private static final byte GIFT_LIST_REQ = 0x40;
    private static final byte GIFT_BUY = 0x41;
    private static final byte GIFT_INV_REQ = 0x42;
    private static final byte GIFT_CATALOG = 0x43;
    private static final byte GIFT_RESULT = 0x44;
    private static final byte GIFT_RECV = 0x45;
    private static final byte GIFT_INV = 0x46;
    private static final byte STREAM_START = 0x50;
    private static final byte STREAM_STOP = 0x51;
    private static final byte STREAM_LIST_REQ = 0x52;
    private static final byte STREAM_LIST = 0x53;
    private static final byte STREAM_DONATE = 0x54;
    private static final byte STREAM_DONATE_RESULT = 0x55;
    private static final byte STREAM_DONATE_RECV = 0x56;
    private static final byte COIN_OPEN = 0x60;
    private static final byte COIN_CANCEL = 0x61;
    private static final byte COIN_ACCEPT = 0x62;
    private static final byte COIN_LIST_REQ = 0x63;
    private static final byte RPS_CHALLENGE = 0x64;
    private static final byte RPS_ACCEPT = 0x65;
    private static final byte RPS_DECLINE = 0x66;
    private static final byte RPS_CHOICE = 0x67;
    private static final byte COIN_LIST = 0x68;
    private static final byte COIN_RESULT = 0x69;
    private static final byte COIN_ERR = 0x6A;
    private static final byte RPS_CHALLENGED = 0x6B;
    private static final byte RPS_STARTED = 0x6C;
    private static final byte RPS_ENDED = 0x6D;
    private static final byte RPS_RESULT = 0x6E;
    private static final byte RPS_ERR = 0x6F;

    private static final int DEFAULT_CHUNK_BYTES = 24_000;
    private static final int MESSAGES_PER_TICK = 8;
    private static final long PENDING_TIMEOUT_MS = 90_000;
    /** Sanity cap on a server-declared download size, so a rogue server can't OOM the client. */
    private static final int MAX_DOWNLOAD_BYTES = 64 * 1024 * 1024;

    private static final PmServerMedia INSTANCE = new PmServerMedia();

    public static PmServerMedia get() {
        return INSTANCE;
    }

    private static final int TIER_FREE = 0;
    private static final int TIER_PRO = 1;

    private volatile boolean serverHasPlugin = false;
    private volatile int tier = TIER_FREE;
    private volatile int maxFileBytes = 25 * 1024 * 1024;
    private volatile int chunkBytes = DEFAULT_CHUNK_BYTES;

    private final Queue<byte[]> outbound = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Long, Pending<String>> uploads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Download> downloads = new ConcurrentHashMap<>();

    // ---------- Gifts (Vault) ----------

    /** Каталожный подарок: id, название, иконка, цена в монетах. */
    public record Gift(String id, String name, String icon, double price) {
    }

    /** Полученный подарок в профиле: название, иконка, от кого. */
    public record ReceivedGift(String name, String icon, String from) {
    }

    private volatile java.util.List<Gift> catalog = java.util.List.of();
    private volatile double selfBalance = 0d;
    private volatile boolean hasBalance = false;
    private final ConcurrentHashMap<String, java.util.List<ReceivedGift>> inventories = new ConcurrentHashMap<>();
    private volatile String lastResultMsg = null;
    private volatile boolean lastResultOk = false;
    private volatile long lastResultAt = 0L;
    private final java.util.concurrent.atomic.AtomicInteger giftVersion = new java.util.concurrent.atomic.AtomicInteger();

    // ---------- Streams (announcement + Vault donations) ----------

    /** Один активный стрим: игрок, название, ссылка (Twitch/YouTube/...). */
    public record LiveStream(String player, String title, String url) {
    }

    private volatile java.util.List<LiveStream> liveStreams = java.util.List.of();
    private volatile boolean selfStreaming = false;
    private volatile String lastDonateMsg = null;
    private volatile boolean lastDonateOk = false;
    private volatile long lastDonateAt = 0L;
    private final java.util.concurrent.atomic.AtomicInteger streamVersion = new java.util.concurrent.atomic.AtomicInteger();

    // ---------- Мини-игры: подброс монетки + камень-ножницы-бумага (Vault) ----------

    /** Открытая ставка на орла/решку: автор, сумма, сторона (0 орёл, 1 решка). */
    public record CoinBet(String opener, double amount, int side) {
    }

    /** Итог сыгранного подброса. */
    public record CoinResult(String opener, String accepter, int resultSide, String winner, double amount) {
    }

    /** Итог сыгранного раунда КНБ: соперник, твой выбор, его выбор, победитель ("" — ничья), сумма. */
    public record RpsResult(String opponent, int yourChoice, int oppChoice, String winner, double amount) {
    }

    /** Входящий вызов на КНБ от другого игрока, ждущий ответа. */
    public record RpsChallenge(String challenger, double amount) {
    }

    private volatile java.util.List<CoinBet> coinBets = java.util.List.of();
    private volatile CoinResult lastCoinResult;
    private volatile String lastGameErr;
    private volatile long lastGameErrAt;
    private final java.util.concurrent.atomic.AtomicInteger coinVersion = new java.util.concurrent.atomic.AtomicInteger();

    /** Входящий вызов на КНБ, ждущий ответа ({@code null} — нет вызова). */
    private volatile RpsChallenge rpsIncoming;
    /** Активный матч, ждущий выбора ({@code null} — нет активного матча). */
    private volatile String rpsOpponent;
    private volatile double rpsAmount;
    private volatile boolean rpsChoiceSent;
    private volatile RpsResult lastRpsResult;
    private final java.util.concurrent.atomic.AtomicInteger rpsVersion = new java.util.concurrent.atomic.AtomicInteger();

    private PmServerMedia() {
    }

    private static final class Pending<T> {
        final CompletableFuture<T> future = new CompletableFuture<>();
        final long createdAt = System.currentTimeMillis();
    }

    private static final class Download {
        final CompletableFuture<byte[]> future = new CompletableFuture<>();
        final long createdAt = System.currentTimeMillis();
        byte[] buf;
        int received;
    }

    /** True once the server has advertised the pmchat:media channel this session. */
    public boolean isAvailable() {
        return serverHasPlugin;
    }

    /** True when the server runs the Pro edition (premium client features unlocked). */
    public boolean isPro() {
        return serverHasPlugin && tier == TIER_PRO;
    }

    public int maxFileBytes() {
        return maxFileBytes;
    }

    /** Marks the plugin present (server advertised the channel) and handshakes. */
    private void onServerHasPlugin() {
        if (serverHasPlugin) return;
        serverHasPlugin = true;
        enqueue(build(HELLO, dos -> dos.writeInt(1)));
    }

    // ---------- gift API ----------

    /** Запрос каталога подарков + своего баланса. */
    public void requestGifts() {
        if (serverHasPlugin) enqueue(build(GIFT_LIST_REQ, dos -> {
        }));
    }

    /** Запрос списка полученных подарков указанного игрока (для его профиля). */
    public void requestGiftInventory(String player) {
        if (serverHasPlugin && player != null && !player.isBlank()) {
            enqueue(build(GIFT_INV_REQ, dos -> dos.writeUTF(player)));
        }
    }

    /** Купить подарок {@code giftId} и отправить игроку {@code target}. */
    public void buyGift(String target, String giftId) {
        if (serverHasPlugin && target != null && giftId != null) {
            enqueue(build(GIFT_BUY, dos -> {
                dos.writeUTF(target);
                dos.writeUTF(giftId);
            }));
        }
    }

    public java.util.List<Gift> giftCatalog() {
        return catalog;
    }

    public boolean hasBalance() {
        return hasBalance;
    }

    public double selfBalance() {
        return selfBalance;
    }

    public java.util.List<ReceivedGift> giftsFor(String player) {
        if (player == null) return java.util.List.of();
        return inventories.getOrDefault(player.toLowerCase(java.util.Locale.ROOT), java.util.List.of());
    }

    /** Счётчик изменений состояния подарков — экран профиля перечитывает по нему. */
    public int giftVersion() {
        return giftVersion.get();
    }

    public String lastResultMsg() {
        return lastResultMsg;
    }

    public boolean lastResultOk() {
        return lastResultOk;
    }

    public long lastResultAt() {
        return lastResultAt;
    }

    // ---------- streams API ----------

    /** Список сейчас идущих стримов (обновляется сервером push'ом). */
    public java.util.List<LiveStream> liveStreams() {
        return liveStreams;
    }

    public boolean isSelfStreaming() {
        return selfStreaming;
    }

    /** Счётчик изменений списка стримов/доната — экран стримов перечитывает по нему. */
    public int streamVersion() {
        return streamVersion.get();
    }

    public String lastDonateMsg() {
        return lastDonateMsg;
    }

    public boolean lastDonateOk() {
        return lastDonateOk;
    }

    public long lastDonateAt() {
        return lastDonateAt;
    }

    /** Объявить, что этот игрок начал стримить. Требует серверный плагин. */
    public void startStream(String title, String url) {
        if (!serverHasPlugin) return;
        selfStreaming = true;
        enqueue(build(STREAM_START, dos -> {
            dos.writeUTF(title == null ? "" : title);
            dos.writeUTF(url == null ? "" : url);
        }));
    }

    public void stopStream() {
        if (!serverHasPlugin) return;
        selfStreaming = false;
        enqueue(build(STREAM_STOP, dos -> {
        }));
    }

    public void requestStreams() {
        if (serverHasPlugin) enqueue(build(STREAM_LIST_REQ, dos -> {
        }));
    }

    /** Задонатить {@code amount} монет стримеру {@code target} (должен сейчас стримить). */
    public void donate(String target, double amount) {
        if (!serverHasPlugin || target == null || target.isBlank()) return;
        enqueue(build(STREAM_DONATE, dos -> {
            dos.writeUTF(target);
            dos.writeDouble(amount);
        }));
    }

    private static String formatCoins(double d) {
        long l = (long) d;
        return d == l ? Long.toString(l) : String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    // ---------- coin flip API ----------

    public java.util.List<CoinBet> coinBets() {
        return coinBets;
    }

    public CoinResult lastCoinResult() {
        return lastCoinResult;
    }

    public String lastGameErr() {
        return lastGameErr;
    }

    public long lastGameErrAt() {
        return lastGameErrAt;
    }

    /** Счётчик изменений списка ставок/результата — экран игр перечитывает по нему. */
    public int coinVersion() {
        return coinVersion.get();
    }

    /** Открыть ставку (0 — орёл, 1 — решка) на сумму {@code amount}. Списывает монеты сразу. */
    public void coinOpen(double amount, int side) {
        if (!serverHasPlugin) return;
        enqueue(build(COIN_OPEN, dos -> {
            dos.writeDouble(amount);
            dos.writeByte(side & 1);
        }));
    }

    public void coinCancel() {
        if (serverHasPlugin) enqueue(build(COIN_CANCEL, dos -> {
        }));
    }

    public void coinAccept(String opener) {
        if (serverHasPlugin && opener != null) enqueue(build(COIN_ACCEPT, dos -> dos.writeUTF(opener)));
    }

    public void requestCoinBets() {
        if (serverHasPlugin) enqueue(build(COIN_LIST_REQ, dos -> {
        }));
    }

    // ---------- rock-paper-scissors API ----------

    public RpsChallenge rpsIncoming() {
        return rpsIncoming;
    }

    /** Соперник в активном матче ({@code null} — матча нет). */
    public String rpsOpponent() {
        return rpsOpponent;
    }

    public double rpsAmount() {
        return rpsAmount;
    }

    public boolean rpsChoiceSent() {
        return rpsChoiceSent;
    }

    public RpsResult lastRpsResult() {
        return lastRpsResult;
    }

    public int rpsVersion() {
        return rpsVersion.get();
    }

    public void rpsChallenge(String target, double amount) {
        if (serverHasPlugin && target != null) enqueue(build(RPS_CHALLENGE, dos -> {
            dos.writeUTF(target);
            dos.writeDouble(amount);
        }));
    }

    public void rpsAccept(String challenger) {
        if (!serverHasPlugin || challenger == null) return;
        rpsIncoming = null;
        enqueue(build(RPS_ACCEPT, dos -> dos.writeUTF(challenger)));
    }

    public void rpsDecline(String challenger) {
        if (!serverHasPlugin || challenger == null) return;
        rpsIncoming = null;
        enqueue(build(RPS_DECLINE, dos -> dos.writeUTF(challenger)));
    }

    /** Отправить выбор (0 камень/1 бумага/2 ножницы) в активном матче. */
    public void rpsChoose(int choice) {
        if (!serverHasPlugin || rpsOpponent == null || rpsChoiceSent) return;
        String opponent = rpsOpponent;
        rpsChoiceSent = true;
        enqueue(build(RPS_CHOICE, dos -> {
            dos.writeUTF(opponent);
            dos.writeByte(choice & 3);
        }));
    }

    /** Called on disconnect — drop state and fail anything in flight. */
    public void reset() {
        serverHasPlugin = false;
        tier = TIER_FREE;
        catalog = java.util.List.of();
        inventories.clear();
        hasBalance = false;
        selfBalance = 0d;
        liveStreams = java.util.List.of();
        selfStreaming = false;
        coinBets = java.util.List.of();
        lastCoinResult = null;
        lastGameErr = null;
        rpsIncoming = null;
        rpsOpponent = null;
        rpsAmount = 0d;
        rpsChoiceSent = false;
        lastRpsResult = null;
        outbound.clear();
        uploads.values().forEach(p -> p.future.completeExceptionally(new IllegalStateException("disconnected")));
        uploads.clear();
        downloads.values().forEach(d -> d.future.completeExceptionally(new IllegalStateException("disconnected")));
        downloads.clear();
        // Left the server — API listeners see the tier drop back to NONE.
        com.pmchat.client.api.PocketChatClientImpl.refreshServerTier();
    }

    // ---------- public transfer API ----------

    /** Uploads bytes through the server; completes with the file id ({@code <id>.<ext>}). */
    public CompletableFuture<String> upload(byte[] data, String ext) {
        if (!serverHasPlugin) {
            return CompletableFuture.failedFuture(new IllegalStateException("server media unavailable"));
        }
        if (data.length > maxFileBytes) {
            return CompletableFuture.failedFuture(new IllegalStateException("file too large for server relay"));
        }
        long transferId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        Pending<String> pending = new Pending<>();
        uploads.put(transferId, pending);

        String safeExt = ext == null || ext.isBlank() ? "bin" : ext;
        enqueue(build(UPLOAD_BEGIN, dos -> {
            dos.writeLong(transferId);
            dos.writeInt(data.length);
            dos.writeUTF(safeExt);
            dos.writeByte(0); // kind — reserved
        }));
        int cs = Math.max(1024, Math.min(chunkBytes, DEFAULT_CHUNK_BYTES));
        for (int off = 0; off < data.length; off += cs) {
            int len = Math.min(cs, data.length - off);
            int offset = off;
            enqueue(build(UPLOAD_CHUNK, dos -> {
                dos.writeLong(transferId);
                dos.writeInt(offset);
                dos.writeInt(len);
                dos.write(data, offset, len);
            }));
        }
        enqueue(build(UPLOAD_END, dos -> dos.writeLong(transferId)));
        return pending.future;
    }

    /**
     * Routes a private-message wire string to {@code target} through the server
     * plugin (no {@code /m}, nothing in game chat). {@code plain} is the readable
     * fallback the server whispers if the recipient has no mod (empty = don't).
     */
    public void sendPm(String target, String wire, String plain) {
        if (!serverHasPlugin) return;
        enqueue(build(PM_SEND, dos -> {
            dos.writeUTF(target);
            dos.writeUTF(wire == null ? "" : wire);
            dos.writeUTF(plain == null ? "" : plain);
        }));
    }

    /** Downloads a file previously stored on the server, by its file id. */
    public CompletableFuture<byte[]> download(String fileId) {
        if (!serverHasPlugin) {
            return CompletableFuture.failedFuture(new IllegalStateException("server media unavailable"));
        }
        Download existing = downloads.get(fileId);
        if (existing != null) return existing.future;
        Download dl = new Download();
        downloads.put(fileId, dl);
        enqueue(build(DOWNLOAD_REQ, dos -> dos.writeUTF(fileId)));
        return dl.future;
    }

    // ---------- client tick: drain outbound + expire stale ----------

    public void tick() {
        // Обнаружение плагина: сервер объявляет канал pmchat:media (canSend=true),
        // только если на нём стоит PocketChatMedia. canSend можно звать лишь при
        // активном соединении, иначе оно бросает исключение.
        boolean connected = MinecraftClient.getInstance().getNetworkHandler() != null;
        boolean canSend = connected && ClientPlayNetworking.canSend(MediaPayload.ID);
        if (!serverHasPlugin) {
            if (canSend) {
                onServerHasPlugin();
            } else {
                return; // плагина нет/ещё не готово — очереди пусты, делать нечего
            }
        }
        if (canSend) {
            for (int i = 0; i < MESSAGES_PER_TICK; i++) {
                byte[] msg = outbound.poll();
                if (msg == null) break;
                ClientPlayNetworking.send(new MediaPayload(msg));
            }
        }
        long now = System.currentTimeMillis();
        uploads.values().removeIf(p -> {
            if (now - p.createdAt > PENDING_TIMEOUT_MS) {
                p.future.completeExceptionally(new IllegalStateException("upload timed out"));
                return true;
            }
            return false;
        });
        downloads.values().removeIf(d -> {
            if (now - d.createdAt > PENDING_TIMEOUT_MS) {
                d.future.completeExceptionally(new IllegalStateException("download timed out"));
                return true;
            }
            return false;
        });
    }

    // ---------- incoming (client thread) ----------

    public void handle(byte[] message) {
        if (message.length < 1) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            byte op = in.readByte();
            switch (op) {
                case HELLO_ACK -> {
                    in.readInt(); // protocol version
                    maxFileBytes = in.readInt();
                    chunkBytes = in.readInt();
                    // tier byte is optional for forward/backward compatibility
                    tier = in.available() > 0 ? in.readByte() : TIER_FREE;
                    // Handshake done — the tier is only known now, so tell API listeners.
                    com.pmchat.client.api.PocketChatClientImpl.refreshServerTier();
                }
                case UPLOAD_OK -> {
                    long id = in.readLong();
                    String fileId = in.readUTF();
                    Pending<String> p = uploads.remove(id);
                    if (p != null) p.future.complete(fileId);
                }
                case UPLOAD_ERR -> {
                    long id = in.readLong();
                    String reason = in.readUTF();
                    Pending<String> p = uploads.remove(id);
                    if (p != null) p.future.completeExceptionally(new IllegalStateException(reason));
                }
                case DOWNLOAD_BEGIN -> {
                    String fileId = in.readUTF();
                    int total = in.readInt();
                    Download d = downloads.get(fileId);
                    if (d != null) {
                        if (total < 0 || total > MAX_DOWNLOAD_BYTES) {
                            downloads.remove(fileId);
                            d.future.completeExceptionally(new IllegalStateException("download too large"));
                        } else {
                            d.buf = new byte[total];
                            d.received = 0;
                        }
                    }
                }
                case DOWNLOAD_CHUNK -> {
                    String fileId = in.readUTF();
                    int offset = in.readInt();
                    int len = in.readInt();
                    byte[] chunk = new byte[Math.max(0, len)];
                    in.readFully(chunk);
                    Download d = downloads.get(fileId);
                    if (d != null && d.buf != null && offset >= 0 && (long) offset + len <= d.buf.length) {
                        System.arraycopy(chunk, 0, d.buf, offset, len);
                        d.received += len;
                    }
                }
                case DOWNLOAD_END -> {
                    String fileId = in.readUTF();
                    Download d = downloads.remove(fileId);
                    if (d != null) {
                        if (d.buf != null && d.received == d.buf.length) {
                            d.future.complete(d.buf);
                        } else {
                            d.future.completeExceptionally(new IllegalStateException("incomplete download"));
                        }
                    }
                }
                case DOWNLOAD_ERR -> {
                    String fileId = in.readUTF();
                    String reason = in.readUTF();
                    Download d = downloads.remove(fileId);
                    if (d != null) d.future.completeExceptionally(new IllegalStateException(reason));
                }
                case PM_RECV -> {
                    String sender = in.readUTF();
                    String wire = in.readUTF();
                    PmChatClient.deliverServerPm(sender, wire);
                }
                case PM_OFFLINE -> {
                    String target = in.readUTF();
                    PmChatClient.notifyPmOffline(target);
                }
                case GIFT_CATALOG -> {
                    double bal = in.readDouble();
                    int n = in.readInt();
                    java.util.List<Gift> list = new java.util.ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        String id = in.readUTF();
                        String name = in.readUTF();
                        String icon = in.readUTF();
                        double price = in.readDouble();
                        list.add(new Gift(id, name, icon, price));
                    }
                    catalog = list;
                    selfBalance = bal;
                    hasBalance = true;
                    PmChatClient.setKnownBalance(formatCoins(bal));
                    giftVersion.incrementAndGet();
                }
                case GIFT_RESULT -> {
                    boolean ok = in.readBoolean();
                    String msg = in.readUTF();
                    double nb = in.readDouble();
                    selfBalance = nb;
                    hasBalance = true;
                    PmChatClient.setKnownBalance(formatCoins(nb));
                    lastResultOk = ok;
                    lastResultMsg = msg;
                    lastResultAt = System.currentTimeMillis();
                    giftVersion.incrementAndGet();
                }
                case GIFT_RECV -> {
                    String from = in.readUTF();
                    String name = in.readUTF();
                    String icon = in.readUTF();
                    PmChatClient.giftToast(from, name, icon);
                    requestGiftInventory(PmChatClient.selfName());
                    giftVersion.incrementAndGet();
                }
                case GIFT_INV -> {
                    String who = in.readUTF();
                    int n = in.readInt();
                    java.util.List<ReceivedGift> list = new java.util.ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        String name = in.readUTF();
                        String icon = in.readUTF();
                        String frm = in.readUTF();
                        list.add(new ReceivedGift(name, icon, frm));
                    }
                    inventories.put(who.toLowerCase(java.util.Locale.ROOT), list);
                    giftVersion.incrementAndGet();
                }
                case STREAM_LIST -> {
                    int n = in.readInt();
                    java.util.List<LiveStream> list = new java.util.ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        String player = in.readUTF();
                        String title = in.readUTF();
                        String url = in.readUTF();
                        list.add(new LiveStream(player, title, url));
                    }
                    liveStreams = list;
                    selfStreaming = list.stream().anyMatch(l -> l.player().equalsIgnoreCase(PmChatClient.selfName()));
                    streamVersion.incrementAndGet();
                }
                case STREAM_DONATE_RESULT -> {
                    boolean ok = in.readBoolean();
                    String msg = in.readUTF();
                    double nb = in.readDouble();
                    selfBalance = nb;
                    hasBalance = true;
                    PmChatClient.setKnownBalance(formatCoins(nb));
                    lastDonateOk = ok;
                    lastDonateMsg = msg;
                    lastDonateAt = System.currentTimeMillis();
                    streamVersion.incrementAndGet();
                }
                case STREAM_DONATE_RECV -> {
                    String from = in.readUTF();
                    double amount = in.readDouble();
                    PmChatClient.giftToast(from, "+" + formatCoins(amount), "$");
                    streamVersion.incrementAndGet();
                }
                case COIN_LIST -> {
                    int n = in.readInt();
                    java.util.List<CoinBet> list = new java.util.ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        String opener = in.readUTF();
                        double amount = in.readDouble();
                        int side = in.readByte();
                        list.add(new CoinBet(opener, amount, side));
                    }
                    coinBets = list;
                    coinVersion.incrementAndGet();
                }
                case COIN_RESULT -> {
                    String opener = in.readUTF();
                    String accepter = in.readUTF();
                    int resultSide = in.readByte();
                    String winner = in.readUTF();
                    double amount = in.readDouble();
                    double nb = in.readDouble();
                    lastCoinResult = new CoinResult(opener, accepter, resultSide, winner, amount);
                    selfBalance = nb;
                    hasBalance = true;
                    PmChatClient.setKnownBalance(formatCoins(nb));
                    coinVersion.incrementAndGet();
                }
                case COIN_ERR -> {
                    lastGameErr = in.readUTF();
                    lastGameErrAt = System.currentTimeMillis();
                    coinVersion.incrementAndGet();
                }
                case RPS_CHALLENGED -> {
                    String challenger = in.readUTF();
                    double amount = in.readDouble();
                    rpsIncoming = new RpsChallenge(challenger, amount);
                    rpsVersion.incrementAndGet();
                }
                case RPS_STARTED -> {
                    rpsOpponent = in.readUTF();
                    rpsAmount = in.readDouble();
                    rpsChoiceSent = false;
                    rpsVersion.incrementAndGet();
                }
                case RPS_ENDED -> {
                    in.readUTF(); // opponent — экран и так закрывает активный матч/вызов
                    rpsIncoming = null;
                    rpsOpponent = null;
                    rpsChoiceSent = false;
                    rpsVersion.incrementAndGet();
                }
                case RPS_RESULT -> {
                    String opponent = in.readUTF();
                    int yourChoice = in.readByte();
                    int oppChoice = in.readByte();
                    String winner = in.readUTF();
                    double amount = in.readDouble();
                    double nb = in.readDouble();
                    lastRpsResult = new RpsResult(opponent, yourChoice, oppChoice, winner, amount);
                    selfBalance = nb;
                    hasBalance = true;
                    PmChatClient.setKnownBalance(formatCoins(nb));
                    rpsOpponent = null;
                    rpsChoiceSent = false;
                    rpsVersion.incrementAndGet();
                }
                case RPS_ERR -> {
                    lastGameErr = in.readUTF();
                    lastGameErrAt = System.currentTimeMillis();
                    rpsOpponent = null;
                    rpsChoiceSent = false;
                    rpsVersion.incrementAndGet();
                }
                default -> { /* unknown — ignore */ }
            }
        } catch (IOException e) {
            PmChatClient.LOGGER.debug("Bad server-media message: {}", e.toString());
        }
    }

    // ---------- framing helpers ----------

    private interface Writer {
        void write(DataOutputStream dos) throws IOException;
    }

    private byte[] build(byte opcode, Writer body) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeByte(opcode);
            body.write(dos);
        } catch (IOException e) {
            throw new RuntimeException(e); // in-memory stream — cannot fail
        }
        return bos.toByteArray();
    }

    private void enqueue(byte[] msg) {
        outbound.add(msg);
    }
}
