# Client mod API

Artifact `io.github.yurosing:pocketchat-api-mod`, package `com.pmchat.api.client`.

The library contains **no Minecraft or Fabric classes at all** — just records,
enums and interfaces over ordinary Java types. So it compiles against any game
version and never breaks on a mappings change.

## Quick start

```java [ExampleMod.java]{9,12-17}
package com.example;

import com.pmchat.api.client.*;
import net.fabricmc.api.ClientModInitializer;

public class ExampleMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        if (!PocketChatClient.isLoaded()) return;

        PocketChatClient.get().addListener(new PocketChatListener() {
            @Override
            public void onMessageReceived(PmChatMessage message) {
                System.out.println(message.sender() + ": " + message.text());
            }
        });
    }
}
```

::: tip Soft dependency
Put `"suggests": { "pmchat": "*" }` in your `fabric.mod.json` and always check
<span class="pc-sig">PocketChatClient.isLoaded()</span> — then your mod starts
fine without PocketChat.
:::

PocketChat installs its implementation during its own init, so it may not be
there yet in your `onInitializeClient` if your mod loads first. The safest
approach is to subscribe when the player first joins a world, or simply to check
`isLoaded()` before each call.

## The listener

Every method on `PocketChatListener` is `default` — override only what you need.
All callbacks arrive on the client thread: touching the game from them is fine,
holding them up is not.

| Method | When |
|---|---|
| `onMessageReceived(PmChatMessage)` | a message arrived — DM, group, channel, global chat |
| `onMessageSent(PmChatMessage)` | the player sent a message |
| `allowOutgoing(String, String)` | <span class="pc-badge cancel">veto</span> before sending; `false` drops the message |
| `onGiftReceived(String, String, String)` | somebody sent the player a gift |
| `onConversationOpened(String)` | a conversation was opened in the messenger |
| `onServerTierChanged(ServerTier)` | the server changed, or the plugin handshake completed |

```java [ChatHooks.java]:line-numbers{5-8,13}
PocketChatClient.get().addListener(new PocketChatListener() {

    @Override
    public void onMessageReceived(PmChatMessage message) {
        // Structured messages (voice, images, polls) are not text.
        if (!message.isPlainText()) return;
        if (message.text().toLowerCase().contains("help")) {
            PocketChatClient.get().toast("Ping", message.sender());
        }
    }

    @Override
    public boolean allowOutgoing(String target, String text) {
        return !text.startsWith("!"); // keep command-ish lines out of chat
    }
});
```

::: warning `text()` is not always text
For voice notes, images, polls and reactions, <span class="pc-sig">text()</span>
holds the mod's internal encoding rather than a readable string. Check
<span class="pc-sig">isPlainText()</span> before showing it to a player.
:::

## What you can read

```java
PocketChatClientApi api = PocketChatClient.get();

api.modVersion();    // "1.11.0"
api.selfName();      // the player's name
api.serverTier();    // NONE / FREE / PRO — what the server's plugin offers
api.knownBalance();  // balance as a string, "" when unknown
api.isBlocked("Steve");

// Conversation list, newest activity first
for (Conversation c : api.conversations()) {
    System.out.printf("%s (%s), %d unread%n",
            c.displayName(), c.kind(), c.unread());
}

// Messages of one conversation, oldest first
List<PmChatMessage> messages = api.messages("Steve");
```

### Conversation kinds

`ConversationKind` separates a real chat from the service tabs:

| Value | What it is |
|---|---|
| `DIRECT` | a private chat; the `id` is the player's name |
| `GLOBAL` | the server's global chat |
| `CHANNEL` | a channel feed |
| `GROUP` | a group chat |
| `BROADCAST` | a public broadcast |
| `SAVED` | "saved messages" — notes to self |
| `COREPROTECT` | the CoreProtect feed for staff |

::: tip Never build an `id` yourself
Every kind except `DIRECT` uses an internal, prefixed identifier. Take
<span class="pc-sig">Conversation.id()</span> and pass it back verbatim.
:::

## What you can do

```java
PocketChatClientApi api = PocketChatClient.get();

// Send exactly as if the player had typed it.
// Goes through the server with the plugin, through /m without it.
api.send("Steve", "on my way");

// To global chat
api.sendGlobal("hi everyone");

// Open the window: on a conversation, or on the list
api.open("Steve");
api.open(null);

// Toast in the corner of the screen
api.toast("Done", "File uploaded");

// Block list
api.setBlocked("Griefer", true);
```

::: warning `send` is subject to the veto too
<span class="pc-sig">send</span> asks every listener through
<span class="pc-sig">allowOutgoing</span>. If your own listener returns `false`,
your message will not go out and the method returns `false`.
:::

## `ServerTier` — what the server offers

```java
switch (api.serverTier()) {
    case NONE -> { /* no plugin: DMs via /m, files via external hosts */ }
    case FREE -> { /* the server relays messages and media itself */ }
    case PRO  -> { /* plus premium client features */ }
}
```

The value is only known after the plugin handshake, so right after joining a
server it may still read `NONE`. Listen for
<span class="pc-sig">onServerTierChanged</span> instead of polling.

## Full class list

| Class | Purpose |
|---|---|
| `PocketChatClient` | entry point: `isLoaded()`, `get()`, `getOrNull()` |
| `PocketChatClientApi` | the API itself |
| `PocketChatListener` | the listener, all methods `default` |
| `PmChatMessage` | message `record` plus `isPlainText()`, `isMoney()`, `isReply()` |
| `Conversation` | `record (id, displayName, kind, unread, lastMessageTime)` |
| `ConversationKind` | conversation kind |
| `ServerTier` | `NONE` / `FREE` / `PRO` |

Ready-made recipes are on the [examples](/en/api/examples) page.
