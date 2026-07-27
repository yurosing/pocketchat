# Examples

Working recipes you can copy wholesale. Every plugin example assumes
`softdepend: [PocketChat, PocketChatPro]` in `plugin.yml`; every mod example
assumes `"suggests": { "pmchat": "*" }` in `fabric.mod.json`.

## Private-message anti-spam

Rate-limits DMs and blocks repeats. It works for players without the mod too —
what is cancelled is the delivery itself, not just the client-side part.

```java [AntiSpamListener.java]:line-numbers{21,28}
package com.example;

import com.pmchat.api.event.PocketChatMessageEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AntiSpamListener implements Listener {

    private static final long COOLDOWN_MS = 1500L;

    private final Map<UUID, Long> lastSent = new HashMap<>();
    private final Map<UUID, String> lastText = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMessage(PocketChatMessageEvent event) {
        Player sender = event.getSender();
        if (sender.hasPermission("myserver.spam.bypass")) return;

        UUID id = sender.getUniqueId();
        long now = System.currentTimeMillis();

        if (now - lastSent.getOrDefault(id, 0L) < COOLDOWN_MS) {
            event.setCancelled(true);
            sender.sendMessage("§cSlow down.");
            return;
        }
        if (event.getPlain().equalsIgnoreCase(lastText.get(id))) {
            event.setCancelled(true);
            sender.sendMessage("§cYou just sent that.");
            return;
        }
        lastSent.put(id, now);
        lastText.put(id, event.getPlain());
    }
}
```

## Logging DMs to a database

Records readable messages only — structured ones (voice, images, polls) are
skipped. The write goes off-thread so the main thread is never held up.

```java{9,14}
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void logMessage(PocketChatMessageEvent event) {
    String text = event.getPlain();
    if (text.isEmpty()) return; // reactions, typing indicators and other plumbing

    String from = event.getSender().getName();
    String to = event.getTargetName();
    long at = System.currentTimeMillis();

    getServer().getScheduler().runTaskAsynchronously(this, () -> db.insertPm(from, to, text, at));
}
```

::: tip `MONITOR` priority
For logging, always use `MONITOR` with `ignoreCancelled = true` — that way you
record exactly what actually went out, after every cancellation has been decided.
:::

## Rank-gated custom gifts

Adds shop entries that are not in `config.yml`, and discounts them for VIPs.

```java [GiftIntegration.java]{9-11,19,24}
public final class GiftIntegration implements Listener {

    private final Plugin plugin;

    public GiftIntegration(Plugin plugin) {
        this.plugin = plugin;
        GiftRegistry gifts = PocketChat.api().gifts();
        // The catalog is not written back to config.yml — register on every start.
        gifts.register(new Gift("tulip", "Tulip", "❀", 75));
        gifts.register(new Gift("trophy", "Trophy", "♛", 5000));
    }

    @EventHandler
    public void onBuy(PocketChatGiftPurchaseEvent event) {
        // The trophy is only for event winners.
        if (event.getGift().id().equals("trophy")
                && !event.getBuyer().hasPermission("myserver.champion")) {
            event.setFailureMessage("The trophy is for event winners");
            event.setCancelled(true);
            return;
        }
        if (event.getBuyer().hasPermission("myserver.vip")) {
            event.setPrice(event.getPrice() * 0.75);
        }
    }
}
```

Granting a gift with no money involved — an event reward, say:

```java
PocketChat.api().giveGift("Steve", PocketChat.api().gifts().byId("trophy"), "Summer tournament");
```

## Media permissions and limits

PocketChat has no media permission — just the global `max-file-mb`. Here is a
complete rule set on your side.

```java{6,12,20}
public final class MediaPolicy implements Listener {

    @EventHandler
    public void onUpload(PocketChatMediaUploadEvent event) {
        Player p = event.getPlayer();
        if (!p.hasPermission("myserver.pocketchat.media")) {
            event.setDenyReason("Media sharing starts at VIP rank");
            event.setCancelled(true);
            return;
        }
        long limit = p.hasPermission("myserver.pocketchat.media.big")
                ? 16L * 1024 * 1024 : 4L * 1024 * 1024;
        if (event.getSizeBytes() > limit) {
            event.setDenyReason("Your limit is " + (limit / 1024 / 1024) + " MB");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onStored(PocketChatMediaStoredEvent event) {
        getLogger().info(event.getPlayer().getName() + " uploaded "
                + event.getFileId() + " (" + event.getSizeBytes() + " bytes)");
    }
}
```

## An offline mailbox

Saves messages for players who are away and delivers them on login.

```java{7,17-19}
public final class Mailbox implements Listener {

    private final Map<String, List<String>> pending = new HashMap<>();

    @EventHandler
    public void onOffline(PocketChatMessageOfflineEvent event) {
        if (event.getPlain().isEmpty()) return; // structured — not worth storing

        pending.computeIfAbsent(event.getTargetName().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                .add("§7[offline] §f" + event.getSender().getName() + ": " + event.getPlain());

        event.setHandled(true); // PocketChat skips its "offline" notice
        event.getSender().sendMessage("§7Saved — they'll get it when they log in.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        List<String> mail = pending.remove(event.getPlayer().getName().toLowerCase(Locale.ROOT));
        if (mail != null) mail.forEach(event.getPlayer()::sendMessage);
    }
}
```

## A Discord bridge

Relays outgoing DMs out, and feeds incoming ones back into the PocketChat thread.

```java{5,16}
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void toDiscord(PocketChatMessageEvent event) {
    if (event.getPlain().isEmpty()) return;
    getServer().getScheduler().runTaskAsynchronously(this, () ->
            discord.relay(event.getSender().getName(), event.getTargetName(), event.getPlain()));
}

/** Called from your Discord listener — always hop to the main thread. */
public void fromDiscord(String discordName, String minecraftName, String text) {
    getServer().getScheduler().runTask(this, () -> {
        Player target = getServer().getPlayerExact(minecraftName);
        if (target == null) return;
        PocketChatApi api = PocketChat.api();
        // Modded players get it in their thread; everyone else, a normal message.
        if (!api.sendSystemMessage(target, discordName + " (Discord)", text)) {
            target.sendMessage("§9[Discord] §f" + discordName + ": " + text);
        }
    });
}
```

## Greeting players who have the mod

`hasClient()` still returns `false` on `PlayerJoinEvent` — the handshake happens
later. The right hook is `PocketChatClientConnectEvent`.

```java{3,7}
@EventHandler
public void onPocketChat(PocketChatClientConnectEvent event) {
    Player p = event.getPlayer();
    p.sendMessage("§aPocketChat detected — DMs and files go through the server.");

    if (event.getClientProtocolVersion() < PocketChat.api().protocolVersion()) {
        p.sendMessage("§eYour mod version is out of date, please update.");
    }
}
```

## Mod: mirroring messages into your own overlay

```java [ChatOverlay.java]{6,12}
PocketChatClient.get().addListener(new PocketChatListener() {

    @Override
    public void onMessageReceived(PmChatMessage message) {
        // Voice notes, images and polls arrive as internal encodings — skip them.
        if (!message.isPlainText()) return;
        overlay.push(message.sender(), message.text(), message.time());
    }

    @Override
    public void onServerTierChanged(ServerTier tier) {
        overlay.setBadge(tier.hasPlugin() ? "server relay" : "/m");
    }
});
```

## Mod: auto-reply while AFK

```java{5,9-11}
PocketChatClient.get().addListener(new PocketChatListener() {

    @Override
    public void onMessageReceived(PmChatMessage message) {
        if (!afk.isAfk() || !message.isPlainText()) return;
        // Reply once per person so this does not turn into ping-pong.
        if (!replied.add(message.sender())) return;
        PocketChatClient.get().send(message.sender(), "AFK right now, I'll get back to you.");
    }
});
```

::: warning Careful with auto-replies
<span class="pc-sig">send</span> from inside
<span class="pc-sig">onMessageReceived</span> easily becomes an endless exchange
if the other side runs the same mod. Track who you already answered, and clear
the set when you come back from AFK.
:::

## Mod: unread count for a HUD

```java
int unread = 0;
for (Conversation c : PocketChatClient.get().conversations()) {
    if (c.kind() == ConversationKind.DIRECT) unread += c.unread();
}
hud.setBadge(unread);
```
