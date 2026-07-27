# API клиентского мода

Артефакт `io.github.yurosing:pocketchat-api-mod`, пакет `com.pmchat.api.client`.

Библиотека **не содержит ни одного класса Minecraft или Fabric** — только записи,
перечисления и интерфейсы над обычными типами Java. Поэтому она собирается под
любую версию игры и не ломается при смене маппингов.

## Быстрый старт

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

::: tip Мягкая зависимость
Пропишите в `fabric.mod.json` `"suggests": { "pmchat": "*" }` и всегда
проверяйте <span class="pc-sig">PocketChatClient.isLoaded()</span> — тогда ваш
мод спокойно запустится и без PocketChat.
:::

PocketChat ставит реализацию во время своей инициализации, поэтому в
`onInitializeClient` вашего мода она уже может быть недоступна, если ваш мод
грузится раньше. Надёжнее подписываться при первом заходе в мир либо просто
проверять `isLoaded()` перед каждым обращением.

## Слушатель

У `PocketChatListener` **все методы `default`** — переопределяйте только нужные.
Все вызовы приходят в клиентском потоке: трогать игру из них можно, а вот
задерживать их надолго нельзя.

| Метод | Когда |
|---|---|
| `onMessageReceived(PmChatMessage)` | пришло сообщение — ЛС, группа, канал, общий чат |
| `onMessageSent(PmChatMessage)` | игрок отправил сообщение |
| `allowOutgoing(String, String)` | <span class="pc-badge cancel">вето</span> перед отправкой; `false` — сообщение не уйдёт |
| `onGiftReceived(String, String, String)` | игроку подарили подарок |
| `onConversationOpened(String)` | открыли переписку в окне мессенджера |
| `onServerTierChanged(ServerTier)` | сменился сервер или завершилось рукопожатие с плагином |

```java [ChatHooks.java]:line-numbers{5-8,13}
PocketChatClient.get().addListener(new PocketChatListener() {

    @Override
    public void onMessageReceived(PmChatMessage message) {
        // Структурные сообщения (голосовые, картинки, опросы) — не текст.
        if (!message.isPlainText()) return;
        if (message.text().toLowerCase().contains("помоги")) {
            PocketChatClient.get().toast("Зовут", message.sender());
        }
    }

    @Override
    public boolean allowOutgoing(String target, String text) {
        return !text.startsWith("!"); // не выпускаем команды в чат
    }
});
```

::: warning `text()` — не всегда текст
У голосовых, картинок, опросов и реакций в <span class="pc-sig">text()</span>
лежит служебная запись мода, а не читаемая строка. Проверяйте
<span class="pc-sig">isPlainText()</span> перед тем, как что-то показывать
игроку.
:::

## Что можно прочитать

```java
PocketChatClientApi api = PocketChatClient.get();

api.modVersion();    // "1.11.0"
api.selfName();      // ник игрока
api.serverTier();    // NONE / FREE / PRO — что умеет плагин на сервере
api.knownBalance();  // баланс строкой, "" если неизвестен
api.isBlocked("Steve");

// Список переписок, свежие сверху
for (Conversation c : api.conversations()) {
    System.out.printf("%s (%s), непрочитано %d%n",
            c.displayName(), c.kind(), c.unread());
}

// Сообщения одной переписки, старые сверху
List<PmChatMessage> messages = api.messages("Steve");
```

### Типы переписок

`ConversationKind` отличает личный диалог от служебных вкладок:

| Значение | Что это |
|---|---|
| `DIRECT` | личный диалог; `id` — это ник игрока |
| `GLOBAL` | общий чат сервера |
| `CHANNEL` | канал-лента |
| `GROUP` | групповой чат |
| `BROADCAST` | публичный канал |
| `SAVED` | «Избранное» — заметки самому себе |
| `COREPROTECT` | лента CoreProtect для стаффа |

::: tip Не собирайте `id` вручную
У всех видов, кроме `DIRECT`, идентификатор внутренний и с префиксом. Берите
готовый <span class="pc-sig">Conversation.id()</span> и передавайте его обратно
как есть.
:::

## Что можно сделать

```java
PocketChatClientApi api = PocketChatClient.get();

// Отправить — ровно как если бы игрок набрал сам.
// С плагином уйдёт через сервер, без него — через /m.
api.send("Steve", "иду");

// В общий чат
api.sendGlobal("всем привет");

// Открыть окно: на конкретной переписке или на списке
api.open("Steve");
api.open(null);

// Всплывашка в углу экрана
api.toast("Готово", "Файл загружен");

// Чёрный список
api.setBlocked("Griefer", true);
```

::: warning `send` тоже проходит через вето
<span class="pc-sig">send</span> спрашивает всех слушателей через
<span class="pc-sig">allowOutgoing</span>. Если ваш же слушатель вернёт `false`,
ваше сообщение не уйдёт и метод вернёт `false`.
:::

## `ServerTier` — что умеет сервер

```java
switch (api.serverTier()) {
    case NONE -> { /* плагина нет: ЛС через /m, файлы через внешние хостинги */ }
    case FREE -> { /* сервер сам передаёт сообщения и медиа */ }
    case PRO  -> { /* плюс премиум-функции клиента */ }
}
```

Значение известно только после рукопожатия с плагином, поэтому сразу после входа
на сервер оно может ещё быть `NONE`. Слушайте
<span class="pc-sig">onServerTierChanged</span> вместо опроса.

## Полный список классов

| Класс | Назначение |
|---|---|
| `PocketChatClient` | точка входа: `isLoaded()`, `get()`, `getOrNull()` |
| `PocketChatClientApi` | сам API |
| `PocketChatListener` | слушатель, все методы `default` |
| `PmChatMessage` | `record` сообщения + `isPlainText()`, `isMoney()`, `isReply()` |
| `Conversation` | `record (id, displayName, kind, unread, lastMessageTime)` |
| `ConversationKind` | вид переписки |
| `ServerTier` | `NONE` / `FREE` / `PRO` |

Готовые рецепты — на странице [примеров](/api/examples).
