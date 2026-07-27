# Server plugin API

Artifact `io.github.yurosing:pocketchat-api-plugin`, packages `com.pmchat.api`,
`com.pmchat.api.event`, `com.pmchat.api.protocol`.

Everything PocketChat does on the server goes through an event you can listen to
and cancel. Everything you want to do yourself goes through the `PocketChatApi`
service.

## Quick start

```java [MyPlugin.java]{10,14-16}
package com.example;

import com.pmchat.api.PocketChat;
import com.pmchat.api.PocketChatApi;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        if (!PocketChat.isPresent()) {
            getLogger().warning("PocketChat not found — integration disabled.");
            return;
        }
        PocketChatApi api = PocketChat.api();
        getLogger().info("PocketChat " + api.version() + " (" + api.tier() + ")");
        getServer().getPluginManager().registerEvents(new PocketChatHooks(), this);
    }
}
```

::: warning Do not cache `PocketChatApi` across reloads
On `/reload`, or when PocketChat is disabled, the service is unregistered. Keep
the reference within a single `onEnable` — better still, call `PocketChat.api()`
where you need it.
:::

## Events

All extend `PocketChatEvent` and fire **on the main thread**, even the ones that
originate in disk work. So a listener may touch the Bukkit API freely — and must
not block for long.

| Event | | When |
|---|---|---|
| `PocketChatClientConnectEvent` | <span class="pc-badge info">info</span> | a modded client finished its handshake |
| `PocketChatMessageEvent` | <span class="pc-badge cancel">cancellable</span> | a player sends a private message |
| `PocketChatMessageOfflineEvent` | <span class="pc-badge info">takeover</span> | the recipient is offline |
| `PocketChatMediaUploadEvent` | <span class="pc-badge cancel">cancellable</span> | a client starts uploading a file |
| `PocketChatMediaStoredEvent` | <span class="pc-badge info">info</span> | a file was received and stored |
| `PocketChatMediaDownloadEvent` | <span class="pc-badge cancel">cancellable</span> | a client requests a file |
| `PocketChatGiftPurchaseEvent` | <span class="pc-badge cancel">cancellable</span> | a gift purchase, before any money moves |
| `PocketChatGiftReceiveEvent` | <span class="pc-badge info">info</span> | a gift was granted to its recipient |

### Private messages

`PocketChatMessageEvent` is the central one. A PocketChat message has **two
forms**, and you see both:

- <span class="pc-sig">getWire()</span> — the mod's structured encoding: a voice
  note, an image, a reply, a poll, a reaction. Delivered to players who run the
  mod. Read-only — rewriting it would corrupt structured messages.
- <span class="pc-sig">getPlain()</span> — plain text, delivered as an ordinary
  whisper to players without the mod. Empty for messages with no text form (a
  reaction, for instance). This one **is** mutable.

<span class="pc-sig">getDeliveryMode()</span> tells you up front which route the
message will take: `MOD`, `FALLBACK_WHISPER` or `OFFLINE`.

```java [PocketChatHooks.java]:line-numbers{8-11}
@EventHandler(ignoreCancelled = true)
public void onMessage(PocketChatMessageEvent event) {
    Player sender = event.getSender();

    // A muted message just vanishes — so explain it yourself.
    if (mutes.isMuted(sender.getUniqueId())) {
        event.setCancelled(true);
        sender.sendMessage("§cYou are muted and cannot send private messages.");
        return;
    }

    // Censoring only makes sense for the text form — leave the structured one alone.
    if (event.getDeliveryMode() == DeliveryMode.FALLBACK_WHISPER) {
        event.setPlain(censor.clean(event.getPlain()));
    }
}
```

::: tip Cancelling tells the player nothing
A cancelled message disappears without a trace: the client gets no error and no
confirmation. If the player deserves an explanation, send it yourself.
:::

### Messages to offline players

By default the sender's client shows an "offline" notice and the message is
dropped. Mark the event as handled and PocketChat stays quiet, leaving delivery
to you:

```java
@EventHandler
public void onOffline(PocketChatMessageOfflineEvent event) {
    mailbox.store(event.getTargetName(), event.getSender().getName(), event.getPlain());
    event.setHandled(true); // [!code highlight]
    event.getSender().sendMessage("§7Saved — they'll get it when they log in.");
}
```

### Media

PocketChat has no upload permission of any kind — only the global `max-file-mb`
limit. `PocketChatMediaUploadEvent` is the first and only place to add rules:

```java{4,8}
@EventHandler
public void onUpload(PocketChatMediaUploadEvent event) {
    if (!event.getPlayer().hasPermission("myserver.pocketchat.media")) {
        event.setDenyReason("Media sharing is a donator perk");
        event.setCancelled(true);
        return;
    }
    if (event.getSizeBytes() > 4 * 1024 * 1024) {
        event.setDenyReason("Files are capped at 4 MB here");
        event.setCancelled(true);
    }
}
```

::: warning The extension comes from the client
`getExtension()` is what the client **claimed**, not what the file actually is.
Do not rely on it to validate content.
:::

Download access rests on one assumption too: the file id is 16 random characters
known only to the recipient. If that is not enough, add your own check in
`PocketChatMediaDownloadEvent`.

## Gifts

`PocketChatGiftPurchaseEvent` fires **before** the Vault withdrawal, so the price
can be rewritten and the purchase refused.

```java{4,9-10}
@EventHandler
public void onBuy(PocketChatGiftPurchaseEvent event) {
    if (event.getBuyer().hasPermission("myserver.vip")) {
        event.setPrice(event.getPrice() * 0.5); // half price for VIPs
    }
    if (limits.reachedDaily(event.getBuyer())) {
        event.setFailureMessage("Daily gift limit reached");
        event.setCancelled(true);
    }
}
```

`PocketChatGiftReceiveEvent` follows once the gift is granted. It cannot be
cancelled, but the chat line can be replaced or removed:

```java
@EventHandler
public void onGift(PocketChatGiftReceiveEvent event) {
    event.setAnnouncement(null); // silence the default line // [!code highlight]
    Player to = event.getRecipient();
    if (to != null) {
        to.playSound(to.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        to.sendMessage("§d" + event.getGift().icon() + " §fA gift from " + event.getFrom());
    }
}
```

## The `PocketChatApi` service

### Information

| Method | Returns |
|---|---|
| `tier()` | `FREE` or `PRO` — which edition is installed |
| `version()` | the plugin version, e.g. `2.0.0` |
| `protocolVersion()` | the `pmchat:media` protocol version |
| `hasClient(Player)` | whether the player runs the client mod |
| `clients()` | every online player with the mod |
| `hasEconomy()` | whether a Vault economy is hooked up |
| `balanceOf(OfflinePlayer)` | the balance as the gift shop reads it |

::: tip The mod is not detected instantly
On `PlayerJoinEvent`, <span class="pc-sig">hasClient</span> still returns
`false` — the handshake happens slightly later. Listen for
`PocketChatClientConnectEvent` instead of polling.
:::

### Sending messages

```java
PocketChatApi api = PocketChat.api();

// As the player, exactly as if they had typed it.
// Lands in the mod's thread, or goes out as a whisper without the mod.
api.sendMessage(player, "Steve", "meet me at spawn");

// As the server — only reaches players who run the mod.
api.sendSystemMessage(player, "Auction", "Your item sold for 1200 coins");
```

<span class="pc-sig">sendMessage</span> goes through
`PocketChatMessageEvent`, so other plugins (and you) can still cancel it. It
returns `false` when the recipient is offline or a listener cancelled it.

### Gifts

The catalog is live: it starts from the `gifts:` section of `config.yml` and you
can add your own entries.

```java{5,11}
GiftRegistry gifts = PocketChat.api().gifts();

// Your own gift. Register it in every onEnable —
// it is not written back to config.yml.
gifts.register(new Gift("tulip", "Tulip", "❀", 75));

// What is in the catalog right now
for (Gift g : gifts.all()) {
    getLogger().info(g.icon() + " " + g.name() + " — " + g.price());
}

// Grant one for free: no Vault, no balance check
PocketChat.api().giveGift("Steve", gifts.byId("tulip"), "Summer event");

// What a player has already been given
for (ReceivedGift r : PocketChat.api().giftsOf("Steve")) {
    getLogger().info(r.icon() + " " + r.name() + " from " + r.from());
}
```

### Media

`MediaService` is direct access to the server's file store.

```java
MediaService media = PocketChat.api().media();

// Every method touches the disk — async only!
getServer().getScheduler().runTaskAsynchronously(this, () -> {
    try {
        String fileId = media.store(pngBytes, "png");
        byte[] back = media.read(fileId);
        getLogger().info("stored " + fileId + ", " + back.length + " bytes");
    } catch (IOException e) {
        getLogger().warning("could not store: " + e);
    }
});
```

::: danger A file id is a password
Files are stored as `<16 random characters>.<ext>` and there is no other access
check: **anyone who knows the id can download the file**. Never print ids to
public chat, logs or a web panel.
:::

## Full class list

| Class | Purpose |
|---|---|
| `PocketChat` | entry point: `isPresent()`, `api()`, `apiOrNull()`, `CHANNEL` |
| `PocketChatApi` | the service itself |
| `PocketChatTier` | `FREE` / `PRO` |
| `Gift` | `record (id, name, icon, price)` |
| `ReceivedGift` | `record (name, icon, from)` |
| `GiftRegistry` | catalog: `register`, `unregister`, `byId`, `all`, `isEnabled` |
| `MediaService` | `store`, `read`, `path`, `exists`, `delete`, `maxFileBytes` |
| `PocketChatProtocol` | `pmchat:media` opcodes — see the [protocol](/en/api/protocol) |

Ready-made recipes are on the [examples](/en/api/examples) page.
