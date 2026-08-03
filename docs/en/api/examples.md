# Examples

Working recipes for the mod, ready to copy as-is. All examples assume
`"suggests": { "pmchat": "*" }` in `fabric.mod.json`.

## Mirroring messages into your own overlay

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

## Auto-reply while AFK

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

## Unread count for a HUD

```java
int unread = 0;
for (Conversation c : PocketChatClient.get().conversations()) {
    if (c.kind() == ConversationKind.DIRECT) unread += c.unread();
}
hud.setBadge(unread);
```
