# The `pmchat:media` protocol

The mod and the plugin talk over a single plugin-messaging channel —
`pmchat:media`.

::: tip You probably do not need this page
For ordinary integration there is the [plugin API](/en/api/plugin) and the
[mod API](/en/api/mod). The raw protocol only matters if you are writing a
**proxy, a bridge, or a client for another platform**.
:::

The opcode constants are published in `PocketChatProtocol` (artifact
`pocketchat-api-plugin`, package `com.pmchat.api.protocol`) — take them from
there instead of copying the numbers by hand.

## Message format

Every message is one byte array shaped `[opcode][payload]`. The payload is
written with `DataOutputStream` in exactly the field order given in the tables
below. `UTF` means `writeUTF` / `readUTF`.

```java
ByteArrayOutputStream bos = new ByteArrayOutputStream();
try (DataOutputStream out = new DataOutputStream(bos)) {
    out.writeByte(PocketChatProtocol.PM_SEND);
    out.writeUTF("Steve");         // target
    out.writeUTF("hello");         // wire
    out.writeUTF("hello");         // plain
}
player.sendPluginMessage(plugin, PocketChat.CHANNEL, bos.toByteArray());
```

| Constant | Value |
|---|---|
| `PROTOCOL_VERSION` | `1` |
| `CHUNK_BYTES` | `24000` — payload bytes per chunk |
| `TIER_FREE` / `TIER_PRO` | `0` / `1` |

::: tip Unknown opcodes are ignored
Both sides silently skip an opcode they do not know, so new messages can be added
without breaking older mods and plugins.
:::

## Client → server

| Opcode | Byte | Payload |
|---|---|---|
| `HELLO` | `0x01` | `[int protocolVersion]` |
| `UPLOAD_BEGIN` | `0x10` | `[long transferId][int totalBytes][UTF ext][byte kind]` |
| `UPLOAD_CHUNK` | `0x11` | `[long transferId][int offset][int len][len bytes]` |
| `UPLOAD_END` | `0x12` | `[long transferId]` |
| `DOWNLOAD_REQ` | `0x20` | `[UTF fileId]` |
| `PM_SEND` | `0x30` | `[UTF target][UTF wire][UTF plain]` |
| `GIFT_LIST_REQ` | `0x40` | *(empty)* — catalog and own balance |
| `GIFT_BUY` | `0x41` | `[UTF target][UTF giftId]` |
| `GIFT_INV_REQ` | `0x42` | `[UTF player]` |
| `STREAM_START` | `0x50` | `[UTF title][UTF url]` — announce that this player started streaming |
| `STREAM_STOP` | `0x51` | *(empty)* — the stream ended |
| `STREAM_LIST_REQ` | `0x52` | *(empty)* — ask for the current list of live streams |
| `STREAM_DONATE` | `0x54` | `[UTF target][double amount]` — donate coins to a streamer |

## Server → client

| Opcode | Byte | Payload |
|---|---|---|
| `HELLO_ACK` | `0x02` | `[int protocolVersion][int maxFileBytes][int maxChunkBytes][byte tier]` |
| `UPLOAD_OK` | `0x13` | `[long transferId][UTF fileId]` |
| `UPLOAD_ERR` | `0x14` | `[long transferId][UTF reason]` |
| `DOWNLOAD_BEGIN` | `0x21` | `[UTF fileId][int totalBytes]` |
| `DOWNLOAD_CHUNK` | `0x22` | `[UTF fileId][int offset][int len][len bytes]` |
| `DOWNLOAD_END` | `0x23` | `[UTF fileId]` |
| `DOWNLOAD_ERR` | `0x24` | `[UTF fileId][UTF reason]` |
| `PM_RECV` | `0x31` | `[UTF sender][UTF wire]` |
| `PM_OFFLINE` | `0x32` | `[UTF target]` |
| `GIFT_CATALOG` | `0x43` | `[double balance][int n]` × `{[UTF id][UTF name][UTF icon][double price]}` |
| `GIFT_RESULT` | `0x44` | `[boolean ok][UTF message][double newBalance]` |
| `GIFT_RECV` | `0x45` | `[UTF from][UTF giftName][UTF icon]` |
| `GIFT_INV` | `0x46` | `[UTF player][int n]` × `{[UTF giftName][UTF icon][UTF from]}` |
| `STREAM_LIST` | `0x53` | `[int n]` × `{[UTF player][UTF title][UTF url]}` — currently live streams |
| `STREAM_DONATE_RESULT` | `0x55` | `[boolean ok][UTF message][double newBalance]` |
| `STREAM_DONATE_RECV` | `0x56` | `[UTF from][double amount]` |

## How an exchange goes

### Handshake

The client sends `HELLO` as soon as the server advertises the channel. The
`HELLO_ACK` reply carries the file limit, the chunk size and the **plugin
edition** — which is how the client decides whether to show premium features.

### File transfer

Files do not fit in a single plugin message, so they are split into chunks of
`CHUNK_BYTES`:

```
client → UPLOAD_BEGIN (transferId, size, extension)
client → UPLOAD_CHUNK × N   (offset grows)
client → UPLOAD_END (transferId)
server → UPLOAD_OK (transferId, fileId)   or   UPLOAD_ERR (transferId, reason)
```

Downloads mirror that: `DOWNLOAD_REQ` → `DOWNLOAD_BEGIN` → `DOWNLOAD_CHUNK` × N
→ `DOWNLOAD_END`. The server pushes six chunks per tick so it does not flood the
connection.

Incoming data goes straight to a temp file and outgoing data is streamed off
disk — a whole file is never held in memory.

Error reasons arrive as short strings: `too large`, `too many uploads`,
`bad chunk`, `write failed`, `unknown transfer`, `incomplete`, `store failed`,
`too many downloads`, `not found`, `open failed`, `read failed`, `denied`.

### Private messages

`PM_SEND` carries **two forms** of one message:

- `wire` — the mod's structured encoding (voice note, image, reply, poll, reaction);
- `plain` — plain text, in case the recipient has no mod.

The server checks whether the recipient is listening on `pmchat:media`:

| Recipient | What happens |
|---|---|
| online, with the mod | `PM_RECV` carrying the `wire` form |
| online, without the mod | the server runs `tell-command` with the `plain` text |
| offline | `PM_OFFLINE` back to the sender; the message is queued in the recipient's mailbox |

Queued messages are persisted to disk (`offline-mail.yml`, up to 200 per player) and
delivered automatically as `PM_RECV` the moment the recipient joins the server and
their client sends `HELLO` — they survive a server restart in the meantime. Another
plugin can take over delivery itself — see `PocketChatMessageOfflineEvent` in the
[Plugin API](./plugin.md).

### Gifts

```
client → GIFT_LIST_REQ
server → GIFT_CATALOG (balance + entries)
client → GIFT_BUY (to whom, what)
server → GIFT_RESULT (success, message, new balance)   to the buyer
server → GIFT_RECV (from whom, what, icon)             to the recipient
```

The catalog is empty when gifts are switched off (`gifts-enabled: false`) or no
Vault economy is hooked up.

### Streams

```
client → STREAM_START (title, link)   |   client → STREAM_STOP
server → STREAM_LIST (broadcast to everyone with the plugin)

client → STREAM_LIST_REQ
server → STREAM_LIST (to the requester only)

client → STREAM_DONATE (to whom, amount)
server → STREAM_DONATE_RESULT (success, message, new balance)   to the donor
server → STREAM_DONATE_RECV (from whom, amount)                 to the streamer
```

The stream list only lives in the plugin's memory (nothing on disk) and is
re-broadcast to every online player with the mod on each `STREAM_START` /
`STREAM_STOP`, plus sent individually to whoever sent `STREAM_LIST_REQ`.
`STREAM_DONATE` withdraws coins through Vault and deposits them to the
recipient — the plugin never streams or stores any video, only a title and an
external link (Twitch/YouTube etc.).

::: warning A file id *is* the access right
`fileId` is 16 random characters plus an extension. There is no other download
permission check: **anyone who knows the id can fetch the file**. If that is not
enough, add your own check through `PocketChatMediaDownloadEvent`.
:::
