# API серверного плагина

Артефакт `io.github.yurosing:pocketchat-api-plugin`, пакеты `com.pmchat.api`,
`com.pmchat.api.event`, `com.pmchat.api.protocol`.

Всё, что делает PocketChat на сервере, проходит через события — их можно ловить и
отменять. Всё, что вы хотите сделать сами, — через сервис `PocketChatApi`.

## Быстрый старт

```java [MyPlugin.java]{10,14-16}
package com.example;

import com.pmchat.api.PocketChat;
import com.pmchat.api.PocketChatApi;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        if (!PocketChat.isPresent()) {
            getLogger().warning("PocketChat не найден — интеграция выключена.");
            return;
        }
        PocketChatApi api = PocketChat.api();
        getLogger().info("PocketChat " + api.version() + " (" + api.tier() + ")");
        getServer().getPluginManager().registerEvents(new PocketChatHooks(), this);
    }
}
```

::: warning Не кэшируйте `PocketChatApi` между релоадами
При `/reload` или выключении PocketChat сервис снимается с регистрации. Держите
ссылку в пределах одного `onEnable`, а лучше — вызывайте `PocketChat.api()` там,
где он нужен.
:::

## События

Все наследуют `PocketChatEvent` и срабатывают **в главном потоке** — даже те, что
рождаются из дисковых операций. Значит, из слушателя можно свободно трогать Bukkit
API (и нельзя — блокировать поток надолго).

| Событие | | Когда |
|---|---|---|
| `PocketChatClientConnectEvent` | <span class="pc-badge info">инфо</span> | клиент с модом завершил рукопожатие |
| `PocketChatMessageEvent` | <span class="pc-badge cancel">отменяемое</span> | игрок отправляет ЛС |
| `PocketChatMessageOfflineEvent` | <span class="pc-badge info">перехват</span> | получатель оффлайн |
| `PocketChatMediaUploadEvent` | <span class="pc-badge cancel">отменяемое</span> | клиент начинает загрузку файла |
| `PocketChatMediaStoredEvent` | <span class="pc-badge info">инфо</span> | файл принят и сохранён |
| `PocketChatMediaDownloadEvent` | <span class="pc-badge cancel">отменяемое</span> | клиент запрашивает файл |
| `PocketChatGiftPurchaseEvent` | <span class="pc-badge cancel">отменяемое</span> | покупка подарка, до списания денег |
| `PocketChatGiftReceiveEvent` | <span class="pc-badge info">инфо</span> | подарок выдан получателю |

### Личные сообщения

`PocketChatMessageEvent` — центральное событие. У сообщения PocketChat **две
формы**, и вы видите обе:

- <span class="pc-sig">getWire()</span> — структурная запись мода: голосовое,
  картинка, ответ на сообщение, опрос, реакция. Уходит тем, у кого есть мод.
  Только для чтения — переписав её, вы поломаете структурные сообщения.
- <span class="pc-sig">getPlain()</span> — простой текст. Уходит обычным шёпотом
  тем, у кого мода нет. Пустой у сообщений без текстовой формы (например, у
  реакции). Его **можно менять**.

<span class="pc-sig">getDeliveryMode()</span> заранее говорит, каким путём
сообщение пойдёт: `MOD`, `FALLBACK_WHISPER` или `OFFLINE`.

```java [PocketChatHooks.java]:line-numbers{8-11}
@EventHandler(ignoreCancelled = true)
public void onMessage(PocketChatMessageEvent event) {
    Player sender = event.getSender();

    // Мут: сообщение молча исчезает — объясняем сами.
    if (mutes.isMuted(sender.getUniqueId())) {
        event.setCancelled(true);
        sender.sendMessage("§cВы в муте и не можете писать в ЛС.");
        return;
    }

    // Цензура работает только для текстовой формы — структурную не трогаем.
    if (event.getDeliveryMode() == DeliveryMode.FALLBACK_WHISPER) {
        event.setPlain(censor.clean(event.getPlain()));
    }
}
```

::: tip Отмена ничего не сообщает игроку
Отменённое сообщение исчезает бесследно: клиент не получает ни ошибки, ни
подтверждения. Если игрок должен понять, что произошло, напишите ему сами.
:::

### Сообщение в оффлайн

По умолчанию отправитель получает пометку «игрок не в сети», а сообщение
теряется. Пометьте событие как обработанное — и PocketChat промолчит, а доставка
останется на вас:

```java
@EventHandler
public void onOffline(PocketChatMessageOfflineEvent event) {
    mailbox.store(event.getTargetName(), event.getSender().getName(), event.getPlain());
    event.setHandled(true); // [!code highlight]
    event.getSender().sendMessage("§7Сохранил — получит, когда зайдёт.");
}
```

### Медиа

У PocketChat нет никаких прав на загрузку файлов — есть только общий лимит
`max-file-mb`. `PocketChatMediaUploadEvent` — первое и единственное место, где
можно поставить свои правила:

```java{4,8}
@EventHandler
public void onUpload(PocketChatMediaUploadEvent event) {
    if (!event.getPlayer().hasPermission("myserver.pocketchat.media")) {
        event.setDenyReason("Отправка файлов — привилегия донатеров");
        event.setCancelled(true);
        return;
    }
    if (event.getSizeBytes() > 4 * 1024 * 1024) {
        event.setDenyReason("У нас лимит 4 МБ");
        event.setCancelled(true);
    }
}
```

::: warning Расширение приходит от клиента
`getExtension()` — то, что клиент **заявил**, а не то, чем файл является.
Не полагайтесь на него для проверки содержимого.
:::

Доступ к скачиванию у PocketChat тоже держится на одном допущении: id файла —
случайные 16 символов, и знает его только получатель. Если этого мало, добавьте
свою проверку в `PocketChatMediaDownloadEvent`.

### Подарки

`PocketChatGiftPurchaseEvent` срабатывает **до** списания через Vault: цену можно
переписать, покупку — отменить.

```java{4,9-10}
@EventHandler
public void onBuy(PocketChatGiftPurchaseEvent event) {
    if (event.getBuyer().hasPermission("myserver.vip")) {
        event.setPrice(event.getPrice() * 0.5); // VIP — полцены
    }
    if (limits.reachedDaily(event.getBuyer())) {
        event.setFailureMessage("Дневной лимит подарков исчерпан");
        event.setCancelled(true);
    }
}
```

`PocketChatGiftReceiveEvent` идёт следом, когда подарок уже выдан. Отменить его
нельзя, но можно заменить или убрать строку в чате:

```java
@EventHandler
public void onGift(PocketChatGiftReceiveEvent event) {
    event.setAnnouncement(null); // убрать стандартную строку // [!code highlight]
    Player to = event.getRecipient();
    if (to != null) {
        to.playSound(to.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        to.sendMessage("§d" + event.getGift().icon() + " §fПодарок от " + event.getFrom());
    }
}
```

## Сервис `PocketChatApi`

### Информация

| Метод | Что возвращает |
|---|---|
| `tier()` | `FREE` или `PRO` — какое издание плагина стоит |
| `version()` | версия плагина, например `2.0.0` |
| `protocolVersion()` | версия протокола `pmchat:media` |
| `hasClient(Player)` | стоит ли у игрока клиентский мод |
| `clients()` | все онлайн-игроки с модом |
| `hasEconomy()` | подключена ли экономика через Vault |
| `balanceOf(OfflinePlayer)` | баланс так, как его видит магазин подарков |

::: tip Мод определяется не сразу
На `PlayerJoinEvent` <span class="pc-sig">hasClient</span> ещё вернёт `false` —
рукопожатие происходит чуть позже. Ловите `PocketChatClientConnectEvent` вместо
опроса в цикле.
:::

### Отправка сообщений

```java
PocketChatApi api = PocketChat.api();

// От имени игрока — как будто он сам написал.
// Дойдёт в тред мода, а без мода — обычным шёпотом.
api.sendMessage(player, "Steve", "встречаемся на спавне");

// От имени сервера — только тем, у кого есть мод.
api.sendSystemMessage(player, "Аукцион", "Ваш лот продан за 1200 монет");
```

<span class="pc-sig">sendMessage</span> проходит через
`PocketChatMessageEvent`, так что другие плагины (и вы сами) всё ещё могут его
отменить. Возвращает `false`, если получатель оффлайн или сообщение отменили.

### Подарки

Каталог живой: он начинается с секции `gifts:` из `config.yml`, и в него можно
добавлять свои позиции.

```java{5,11}
GiftRegistry gifts = PocketChat.api().gifts();

// Свой подарок. Регистрировать нужно в каждом onEnable —
// в config.yml он не сохраняется.
gifts.register(new Gift("tulip", "Тюльпан", "❀", 75));

// Что сейчас в каталоге
for (Gift g : gifts.all()) {
    getLogger().info(g.icon() + " " + g.name() + " — " + g.price());
}

// Выдать бесплатно: без Vault, без проверки баланса
PocketChat.api().giveGift("Steve", gifts.byId("tulip"), "Летний ивент");

// Что игроку уже подарили
for (ReceivedGift r : PocketChat.api().giftsOf("Steve")) {
    getLogger().info(r.icon() + " " + r.name() + " от " + r.from());
}
```

### Медиа

`MediaService` — прямой доступ к хранилищу файлов сервера.

```java
MediaService media = PocketChat.api().media();

// Все методы ходят на диск — только асинхронно!
getServer().getScheduler().runTaskAsynchronously(this, () -> {
    try {
        String fileId = media.store(pngBytes, "png");
        byte[] back = media.read(fileId);
        getLogger().info("сохранил " + fileId + ", " + back.length + " байт");
    } catch (IOException e) {
        getLogger().warning("не смог сохранить: " + e);
    }
});
```

::: danger id файла — это пароль
Файлы лежат под именем `<16 случайных символов>.<расширение>`, и никакой другой
проверки прав нет: **кто знает id, тот скачает файл**. Не выводите id в
публичный чат, в логи и в веб-панель.
:::

## Полный список классов

| Класс | Назначение |
|---|---|
| `PocketChat` | точка входа: `isPresent()`, `api()`, `apiOrNull()`, `CHANNEL` |
| `PocketChatApi` | сам сервис |
| `PocketChatTier` | `FREE` / `PRO` |
| `Gift` | `record (id, name, icon, price)` |
| `ReceivedGift` | `record (name, icon, from)` |
| `GiftRegistry` | каталог: `register`, `unregister`, `byId`, `all`, `isEnabled` |
| `MediaService` | `store`, `read`, `path`, `exists`, `delete`, `maxFileBytes` |
| `PocketChatProtocol` | опкоды `pmchat:media` — см. [протокол](/api/protocol) |

Готовые рецепты — на странице [примеров](/api/examples).
