# Примеры

Рабочие рецепты, которые можно скопировать целиком. Все примеры для плагина
предполагают `softdepend: [PocketChat, PocketChatPro]` в `plugin.yml`, все
примеры для мода — `"suggests": { "pmchat": "*" }` в `fabric.mod.json`.

## Антиспам в личных сообщениях

Ограничивает частоту ЛС и режет повторы. Работает и для тех, у кого мода нет —
отменяется сама доставка, а не только клиентская часть.

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
            sender.sendMessage("§cНе так быстро.");
            return;
        }
        if (event.getPlain().equalsIgnoreCase(lastText.get(id))) {
            event.setCancelled(true);
            sender.sendMessage("§cВы уже это отправляли.");
            return;
        }
        lastSent.put(id, now);
        lastText.put(id, event.getPlain());
    }
}
```

## Логирование ЛС в базу

Пишет только читаемые сообщения — структурные (голосовые, картинки, опросы)
пропускает. Запись уходит асинхронно, чтобы не держать главный поток.

```java{9,14}
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void logMessage(PocketChatMessageEvent event) {
    String text = event.getPlain();
    if (text.isEmpty()) return; // реакции, «печатает…» и прочая служебка

    String from = event.getSender().getName();
    String to = event.getTargetName();
    long at = System.currentTimeMillis();

    getServer().getScheduler().runTaskAsynchronously(this, () -> db.insertPm(from, to, text, at));
}
```

::: tip Приоритет `MONITOR`
Для логирования всегда берите `MONITOR` и `ignoreCancelled = true` — так вы
запишете ровно то, что действительно ушло, и уже после всех отмен.
:::

## Свои подарки за ранг

Добавляет в магазин позиции, которых нет в `config.yml`, и делает их дешевле для
VIP.

```java [GiftIntegration.java]{9-11,19,24}
public final class GiftIntegration implements Listener {

    private final Plugin plugin;

    public GiftIntegration(Plugin plugin) {
        this.plugin = plugin;
        GiftRegistry gifts = PocketChat.api().gifts();
        // Каталог не сохраняется в config.yml — регистрируем при каждом старте.
        gifts.register(new Gift("tulip", "Тюльпан", "❀", 75));
        gifts.register(new Gift("trophy", "Кубок", "♛", 5000));
    }

    @EventHandler
    public void onBuy(PocketChatGiftPurchaseEvent event) {
        // Кубок — только для тех, кто выиграл ивент.
        if (event.getGift().id().equals("trophy")
                && !event.getBuyer().hasPermission("myserver.champion")) {
            event.setFailureMessage("Кубок доступен победителям ивентов");
            event.setCancelled(true);
            return;
        }
        if (event.getBuyer().hasPermission("myserver.vip")) {
            event.setPrice(event.getPrice() * 0.75);
        }
    }
}
```

Выдать подарок вообще без денег — например, в награду за ивент:

```java
PocketChat.api().giveGift("Steve", PocketChat.api().gifts().byId("trophy"), "Летний турнир");
```

## Права и лимиты на файлы

У PocketChat нет прав на отправку медиа — только общий `max-file-mb`. Вот полный
набор правил на своей стороне.

```java{6,12,20}
public final class MediaPolicy implements Listener {

    @EventHandler
    public void onUpload(PocketChatMediaUploadEvent event) {
        Player p = event.getPlayer();
        if (!p.hasPermission("myserver.pocketchat.media")) {
            event.setDenyReason("Отправка файлов доступна с ранга VIP");
            event.setCancelled(true);
            return;
        }
        long limit = p.hasPermission("myserver.pocketchat.media.big")
                ? 16L * 1024 * 1024 : 4L * 1024 * 1024;
        if (event.getSizeBytes() > limit) {
            event.setDenyReason("Ваш лимит — " + (limit / 1024 / 1024) + " МБ");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onStored(PocketChatMediaStoredEvent event) {
        getLogger().info(event.getPlayer().getName() + " загрузил "
                + event.getFileId() + " (" + event.getSizeBytes() + " байт)");
    }
}
```

## Почтовый ящик для оффлайна

Сохраняет сообщения тем, кого нет в сети, и отдаёт при заходе.

```java{7,17-19}
public final class Mailbox implements Listener {

    private final Map<String, List<String>> pending = new HashMap<>();

    @EventHandler
    public void onOffline(PocketChatMessageOfflineEvent event) {
        if (event.getPlain().isEmpty()) return; // структурное — не сохраняем

        pending.computeIfAbsent(event.getTargetName().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                .add("§7[оффлайн] §f" + event.getSender().getName() + ": " + event.getPlain());

        event.setHandled(true); // PocketChat промолчит про «не в сети»
        event.getSender().sendMessage("§7Сохранил — получит, когда зайдёт.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        List<String> mail = pending.remove(event.getPlayer().getName().toLowerCase(Locale.ROOT));
        if (mail != null) mail.forEach(event.getPlayer()::sendMessage);
    }
}
```

## Мост в Discord

Отдаёт исходящие ЛС наружу и заводит входящие обратно в тред PocketChat.

```java{5,16}
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void toDiscord(PocketChatMessageEvent event) {
    if (event.getPlain().isEmpty()) return;
    getServer().getScheduler().runTaskAsynchronously(this, () ->
            discord.relay(event.getSender().getName(), event.getTargetName(), event.getPlain()));
}

/** Вызывается из вашего Discord-слушателя, обязательно в главном потоке. */
public void fromDiscord(String discordName, String minecraftName, String text) {
    getServer().getScheduler().runTask(this, () -> {
        Player target = getServer().getPlayerExact(minecraftName);
        if (target == null) return;
        PocketChatApi api = PocketChat.api();
        // Игроку с модом — прямо в тред; остальным — обычным сообщением.
        if (!api.sendSystemMessage(target, discordName + " (Discord)", text)) {
            target.sendMessage("§9[Discord] §f" + discordName + ": " + text);
        }
    });
}
```

## Приветствие для тех, у кого есть мод

`hasClient()` на `PlayerJoinEvent` ещё вернёт `false` — рукопожатие происходит
позже. Правильное место — `PocketChatClientConnectEvent`.

```java{3,7}
@EventHandler
public void onPocketChat(PocketChatClientConnectEvent event) {
    Player p = event.getPlayer();
    p.sendMessage("§aУ вас PocketChat — ЛС и файлы идут через сервер.");

    if (event.getClientProtocolVersion() < PocketChat.api().protocolVersion()) {
        p.sendMessage("§eВаша версия мода устарела, обновитесь.");
    }
}
```

## Мод: пересылка сообщений в свой оверлей

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

## Мод: авто-ответ, когда игрок AFK

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

## Мод: подсчёт непрочитанного для HUD

```java
int unread = 0;
for (Conversation c : PocketChatClient.get().conversations()) {
    if (c.kind() == ConversationKind.DIRECT) unread += c.unread();
}
hud.setBadge(unread);
```
