package com.pmchat.plugin;

/**
 * Wire protocol for the {@code pmchat:media} plugin-messaging channel, shared in
 * spirit with the client mod (the mod keeps an identical copy of these opcodes).
 *
 * Every message is a single plugin-message byte array: [opcode][payload], where
 * the payload is written with {@link java.io.DataOutputStream} in the field order
 * documented per opcode below. Large media is split across many CHUNK messages
 * because a single plugin message is size-limited.
 */
final class Proto {

    private Proto() {
    }

    static final int PROTOCOL_VERSION = 1;

    /** Plugin tier advertised in HELLO_ACK — gates premium client features. */
    static final byte TIER_FREE = 0;
    static final byte TIER_PRO = 1;

    // Client -> Server
    static final byte HELLO = 0x01;          // [int protocolVersion]
    static final byte UPLOAD_BEGIN = 0x10;   // [long transferId][int totalBytes][UTF ext][byte kind]
    static final byte UPLOAD_CHUNK = 0x11;   // [long transferId][int offset][int len][len bytes]
    static final byte UPLOAD_END = 0x12;     // [long transferId]
    static final byte DOWNLOAD_REQ = 0x20;   // [UTF fileId]
    static final byte PM_SEND = 0x30;        // [UTF target][UTF wire][UTF plain]
    static final byte GIFT_LIST_REQ = 0x40;  // [] — request catalog + own balance
    static final byte GIFT_BUY = 0x41;       // [UTF target][UTF giftId]
    static final byte GIFT_INV_REQ = 0x42;   // [UTF player] — request a player's received gifts

    // Server -> Client
    static final byte HELLO_ACK = 0x02;      // [int protocolVersion][int maxFileBytes][int maxChunkBytes][byte tier]
    static final byte UPLOAD_OK = 0x13;      // [long transferId][UTF fileId]
    static final byte UPLOAD_ERR = 0x14;     // [long transferId][UTF reason]
    static final byte DOWNLOAD_BEGIN = 0x21; // [UTF fileId][int totalBytes]
    static final byte DOWNLOAD_CHUNK = 0x22; // [UTF fileId][int offset][int len][len bytes]
    static final byte DOWNLOAD_END = 0x23;   // [UTF fileId]
    static final byte DOWNLOAD_ERR = 0x24;   // [UTF fileId][UTF reason]
    static final byte PM_RECV = 0x31;        // [UTF sender][UTF wire]
    static final byte PM_OFFLINE = 0x32;     // [UTF target]
    static final byte GIFT_CATALOG = 0x43;   // [double balance][int n]{[UTF id][UTF name][UTF icon][double price]}
    static final byte GIFT_RESULT = 0x44;    // [boolean ok][UTF message][double newBalance]
    static final byte GIFT_RECV = 0x45;      // [UTF from][UTF giftName][UTF icon]
    static final byte GIFT_INV = 0x46;       // [UTF player][int n]{[UTF giftName][UTF icon][UTF from]}

    /** Payload bytes per CHUNK message; keeps a whole message well under the plugin-message size limit. */
    static final int CHUNK_BYTES = 24_000;
}
