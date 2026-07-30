package com.pmchat.api.protocol;

/**
 * The {@code pmchat:media} wire protocol, shared by the PocketChat server plugin and
 * the client mod.
 *
 * <p>Every message is a single plugin-message byte array shaped
 * {@code [opcode byte][payload]}, where the payload is written with
 * {@link java.io.DataOutputStream} in exactly the field order documented on each
 * opcode below ({@code UTF} means {@code writeUTF}/{@code readUTF}). Media is split
 * across many chunk messages because a single plugin message is size-limited.
 *
 * <p>Unknown opcodes are ignored on both sides, so new opcodes can be added without
 * breaking older peers.
 *
 * <p>You only need this class if you are speaking the protocol yourself — for example
 * writing a proxy, a bridge, or a client for another platform. For ordinary
 * server-side integration use {@link com.pmchat.api.PocketChatApi} and the events in
 * {@link com.pmchat.api.event} instead.
 *
 * @since 1.0.0
 */
public final class PocketChatProtocol {

    private PocketChatProtocol() {
    }

    /** The plugin-messaging channel these messages travel on. Same value as {@link com.pmchat.api.PocketChat#CHANNEL}. */
    public static final String CHANNEL = "pmchat:media";

    /** Version of this protocol, exchanged in the handshake. */
    public static final int PROTOCOL_VERSION = 1;

    /** Payload bytes per chunk message; keeps each message well under the size limit. */
    public static final int CHUNK_BYTES = 24_000;

    // ---------- tiers (advertised in HELLO_ACK) ----------

    /** Free edition — see {@link com.pmchat.api.PocketChatTier#FREE}. */
    public static final byte TIER_FREE = 0;
    /** Pro edition — see {@link com.pmchat.api.PocketChatTier#PRO}. */
    public static final byte TIER_PRO = 1;

    // ---------- client -> server ----------

    /** {@code [int protocolVersion]} — handshake, sent once on join. */
    public static final byte HELLO = 0x01;
    /** {@code [long transferId][int totalBytes][UTF ext][byte kind]} — start an upload. */
    public static final byte UPLOAD_BEGIN = 0x10;
    /** {@code [long transferId][int offset][int len][len bytes]} — one slice of an upload. */
    public static final byte UPLOAD_CHUNK = 0x11;
    /** {@code [long transferId]} — upload finished, commit it. */
    public static final byte UPLOAD_END = 0x12;
    /** {@code [UTF fileId]} — ask for a stored file. */
    public static final byte DOWNLOAD_REQ = 0x20;
    /** {@code [UTF target][UTF wire][UTF plain]} — send a private message. */
    public static final byte PM_SEND = 0x30;
    /** <i>(no payload)</i> — ask for the gift catalog and own balance. */
    public static final byte GIFT_LIST_REQ = 0x40;
    /** {@code [UTF target][UTF giftId]} — buy a gift for someone. */
    public static final byte GIFT_BUY = 0x41;
    /** {@code [UTF player]} — ask for a player's received gifts. */
    public static final byte GIFT_INV_REQ = 0x42;
    /** {@code [UTF title][UTF url]} — announce that this player has started streaming. */
    public static final byte STREAM_START = 0x50;
    /** <i>(no payload)</i> — announce that this player has stopped streaming. */
    public static final byte STREAM_STOP = 0x51;
    /** <i>(no payload)</i> — ask for the current list of live streams. */
    public static final byte STREAM_LIST_REQ = 0x52;
    /** {@code [UTF target][double amount]} — donate coins to a live streamer (requires Vault). */
    public static final byte STREAM_DONATE = 0x54;
    /** {@code [double amount][byte side]} — open a coin-flip bet (side: 0 heads, 1 tails). */
    public static final byte COIN_OPEN = 0x60;
    /** <i>(no payload)</i> — cancel your own open coin-flip bet. */
    public static final byte COIN_CANCEL = 0x61;
    /** {@code [UTF opener]} — accept someone's open coin-flip bet (opposite side, same amount). */
    public static final byte COIN_ACCEPT = 0x62;
    /** <i>(no payload)</i> — ask for the current list of open coin-flip bets. */
    public static final byte COIN_LIST_REQ = 0x63;
    /** {@code [UTF target][double amount]} — challenge a player to rock-paper-scissors for a wager. */
    public static final byte RPS_CHALLENGE = 0x64;
    /** {@code [UTF challenger]} — accept an incoming RPS challenge. */
    public static final byte RPS_ACCEPT = 0x65;
    /** {@code [UTF challenger]} — decline an incoming RPS challenge. */
    public static final byte RPS_DECLINE = 0x66;
    /** {@code [UTF opponent][byte choice]} — submit rock(0)/paper(1)/scissors(2) for an active match. */
    public static final byte RPS_CHOICE = 0x67;

    // ---------- server -> client ----------

    /** {@code [int protocolVersion][int maxFileBytes][int maxChunkBytes][byte tier]} — handshake reply. */
    public static final byte HELLO_ACK = 0x02;
    /** {@code [long transferId][UTF fileId]} — upload stored. */
    public static final byte UPLOAD_OK = 0x13;
    /** {@code [long transferId][UTF reason]} — upload refused or failed. */
    public static final byte UPLOAD_ERR = 0x14;
    /** {@code [UTF fileId][int totalBytes]} — a download is starting. */
    public static final byte DOWNLOAD_BEGIN = 0x21;
    /** {@code [UTF fileId][int offset][int len][len bytes]} — one slice of a download. */
    public static final byte DOWNLOAD_CHUNK = 0x22;
    /** {@code [UTF fileId]} — download complete. */
    public static final byte DOWNLOAD_END = 0x23;
    /** {@code [UTF fileId][UTF reason]} — download refused or failed. */
    public static final byte DOWNLOAD_ERR = 0x24;
    /** {@code [UTF sender][UTF wire]} — an incoming private message. */
    public static final byte PM_RECV = 0x31;
    /** {@code [UTF target]} — the recipient of a {@link #PM_SEND} was offline. */
    public static final byte PM_OFFLINE = 0x32;
    /** {@code [double balance][int n]{[UTF id][UTF name][UTF icon][double price]}} — the gift catalog. */
    public static final byte GIFT_CATALOG = 0x43;
    /** {@code [boolean ok][UTF message][double newBalance]} — outcome of a {@link #GIFT_BUY}. */
    public static final byte GIFT_RESULT = 0x44;
    /** {@code [UTF from][UTF giftName][UTF icon]} — somebody sent you a gift. */
    public static final byte GIFT_RECV = 0x45;
    /** {@code [UTF player][int n]{[UTF giftName][UTF icon][UTF from]}} — a player's received gifts. */
    public static final byte GIFT_INV = 0x46;
    /** {@code [int n]{[UTF player][UTF title][UTF url]}} — broadcast of currently live streams. */
    public static final byte STREAM_LIST = 0x53;
    /** {@code [boolean ok][UTF message][double newBalance]} — outcome of a {@link #STREAM_DONATE}. */
    public static final byte STREAM_DONATE_RESULT = 0x55;
    /** {@code [UTF from][double amount]} — somebody donated coins to your stream. */
    public static final byte STREAM_DONATE_RECV = 0x56;
    /** {@code [int n]{[UTF opener][double amount][byte side]}} — currently open coin-flip bets. */
    public static final byte COIN_LIST = 0x68;
    /**
     * {@code [UTF opener][UTF accepter][byte resultSide][UTF winner][double amount][double newBalance]}
     * — a flip resolved; sent individually to each participant ({@code newBalance} is THEIRS.)
     */
    public static final byte COIN_RESULT = 0x69;
    /** {@code [UTF reason]} — your {@link #COIN_OPEN} or {@link #COIN_ACCEPT} was refused. */
    public static final byte COIN_ERR = 0x6A;
    /** {@code [UTF challenger][double amount]} — somebody challenged you to rock-paper-scissors. */
    public static final byte RPS_CHALLENGED = 0x6B;
    /** {@code [UTF opponent][double amount]} — a challenge was accepted; submit your {@link #RPS_CHOICE}. */
    public static final byte RPS_STARTED = 0x6C;
    /** {@code [UTF opponent]} — your challenge was declined, or the opponent went offline. */
    public static final byte RPS_ENDED = 0x6D;
    /**
     * {@code [UTF opponent][byte yourChoice][byte oppChoice][UTF winner][double amount][double newBalance]}
     * — RPS result, sent individually to each participant ({@code newBalance} is THEIRS).
     */
    public static final byte RPS_RESULT = 0x6E;
    /** {@code [UTF reason]} — your {@link #RPS_CHALLENGE} or {@link #RPS_ACCEPT} was refused. */
    public static final byte RPS_ERR = 0x6F;

    /**
     * A human-readable name for an opcode, for logging and debugging.
     *
     * @param opcode any opcode byte
     * @return the constant's name, or {@code "UNKNOWN(0x..)"} for opcodes this version does not know
     */
    public static String name(byte opcode) {
        return switch (opcode) {
            case HELLO -> "HELLO";
            case HELLO_ACK -> "HELLO_ACK";
            case UPLOAD_BEGIN -> "UPLOAD_BEGIN";
            case UPLOAD_CHUNK -> "UPLOAD_CHUNK";
            case UPLOAD_END -> "UPLOAD_END";
            case UPLOAD_OK -> "UPLOAD_OK";
            case UPLOAD_ERR -> "UPLOAD_ERR";
            case DOWNLOAD_REQ -> "DOWNLOAD_REQ";
            case DOWNLOAD_BEGIN -> "DOWNLOAD_BEGIN";
            case DOWNLOAD_CHUNK -> "DOWNLOAD_CHUNK";
            case DOWNLOAD_END -> "DOWNLOAD_END";
            case DOWNLOAD_ERR -> "DOWNLOAD_ERR";
            case PM_SEND -> "PM_SEND";
            case PM_RECV -> "PM_RECV";
            case PM_OFFLINE -> "PM_OFFLINE";
            case GIFT_LIST_REQ -> "GIFT_LIST_REQ";
            case GIFT_BUY -> "GIFT_BUY";
            case GIFT_INV_REQ -> "GIFT_INV_REQ";
            case GIFT_CATALOG -> "GIFT_CATALOG";
            case GIFT_RESULT -> "GIFT_RESULT";
            case GIFT_RECV -> "GIFT_RECV";
            case GIFT_INV -> "GIFT_INV";
            case STREAM_START -> "STREAM_START";
            case STREAM_STOP -> "STREAM_STOP";
            case STREAM_LIST_REQ -> "STREAM_LIST_REQ";
            case STREAM_DONATE -> "STREAM_DONATE";
            case STREAM_LIST -> "STREAM_LIST";
            case STREAM_DONATE_RESULT -> "STREAM_DONATE_RESULT";
            case STREAM_DONATE_RECV -> "STREAM_DONATE_RECV";
            case COIN_OPEN -> "COIN_OPEN";
            case COIN_CANCEL -> "COIN_CANCEL";
            case COIN_ACCEPT -> "COIN_ACCEPT";
            case COIN_LIST_REQ -> "COIN_LIST_REQ";
            case RPS_CHALLENGE -> "RPS_CHALLENGE";
            case RPS_ACCEPT -> "RPS_ACCEPT";
            case RPS_DECLINE -> "RPS_DECLINE";
            case RPS_CHOICE -> "RPS_CHOICE";
            case COIN_LIST -> "COIN_LIST";
            case COIN_RESULT -> "COIN_RESULT";
            case COIN_ERR -> "COIN_ERR";
            case RPS_CHALLENGED -> "RPS_CHALLENGED";
            case RPS_STARTED -> "RPS_STARTED";
            case RPS_ENDED -> "RPS_ENDED";
            case RPS_RESULT -> "RPS_RESULT";
            case RPS_ERR -> "RPS_ERR";
            default -> String.format("UNKNOWN(0x%02X)", opcode);
        };
    }
}
