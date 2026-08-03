# Примеры

Рабочие рецепты для мода, которые можно скопировать целиком. Все примеры
предполагают `"suggests": { "pmchat": "*" }` в `fabric.mod.json`.

## Пересылка сообщений в свой оверлей

```java [ChatOverlay.java]{6,12}
PocketChatClient.get().addListener(new PocketChatListener() {

    @Override
    public void onMessageReceived(PmChatMessage message) {
        // Голосовые, картинки и опросы приходят служебной записью — пропускаем.
        if (!message.isPlainText()) return;
        overlay.push(message.sender(), message.text(), message.time());
    }

    @Override
    public void onServerTierChanged(ServerTier tier) {
        overlay.setBadge(tier.hasPlugin() ? "server relay" : "/m");
    }
});
```

## Авто-ответ, когда игрок AFK

```java{5,9-11}
PocketChatClient.get().addListener(new PocketChatListener() {

    @Override
    public void onMessageReceived(PmChatMessage message) {
        if (!afk.isAfk() || !message.isPlainText()) return;
        // Отвечаем один раз на собеседника, чтобы не устроить пинг-понг.
        if (!replied.add(message.sender())) return;
        PocketChatClient.get().send(message.sender(), "Я сейчас отошёл, отвечу позже.");
    }
});
```

::: warning Осторожно с авто-ответами
<span class="pc-sig">send</span> из <span class="pc-sig">onMessageReceived</span>
легко превращается в бесконечный обмен, если у собеседника такой же мод.
Запоминайте, кому уже ответили, и сбрасывайте список при возвращении из AFK.
:::

## Подсчёт непрочитанного для HUD

```java
int unread = 0;
for (Conversation c : PocketChatClient.get().conversations()) {
    if (c.kind() == ConversationKind.DIRECT) unread += c.unread();
}
hud.setBadge(unread);
```
