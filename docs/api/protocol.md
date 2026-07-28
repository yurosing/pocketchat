# Протокол `pmchat:media`

Мод и плагин общаются по одному каналу плагин-сообщений — `pmchat:media`.

::: tip Скорее всего, эта страница вам не нужна
Для обычной интеграции есть [API плагина](/api/plugin) и [API мода](/api/mod).
Сырой протокол нужен, только если вы пишете **прокси, мост или клиент под другую
платформу**.
:::

Константы опкодов опубликованы в классе `PocketChatProtocol` (артефакт
`pocketchat-api-plugin`, пакет `com.pmchat.api.protocol`) — берите их оттуда, а
не переписывайте числа руками.

## Формат сообщения

Каждое сообщение — один массив байт вида `[опкод][полезная нагрузка]`. Нагрузка
пишется через `DataOutputStream` строго в том порядке полей, что указан в
таблицах ниже. `UTF` означает `writeUTF` / `readUTF`.

```java
ByteArrayOutputStream bos = new ByteArrayOutputStream();
try (DataOutputStream out = new DataOutputStream(bos)) {
    out.writeByte(PocketChatProtocol.PM_SEND);
    out.writeUTF("Steve");         // target
    out.writeUTF("привет");        // wire
    out.writeUTF("привет");        // plain
}
player.sendPluginMessage(plugin, PocketChat.CHANNEL, bos.toByteArray());
```

| Константа | Значение |
|---|---|
| `PROTOCOL_VERSION` | `1` |
| `CHUNK_BYTES` | `24000` — байт данных в одном чанке |
| `TIER_FREE` / `TIER_PRO` | `0` / `1` |

::: tip Неизвестные опкоды игнорируются
Обе стороны молча пропускают опкод, которого не знают. Поэтому новые сообщения
можно добавлять, не ломая старые моды и плагины.
:::

## Клиент → сервер

| Опкод | Байт | Нагрузка |
|---|---|---|
| `HELLO` | `0x01` | `[int protocolVersion]` |
| `UPLOAD_BEGIN` | `0x10` | `[long transferId][int totalBytes][UTF ext][byte kind]` |
| `UPLOAD_CHUNK` | `0x11` | `[long transferId][int offset][int len][len байт]` |
| `UPLOAD_END` | `0x12` | `[long transferId]` |
| `DOWNLOAD_REQ` | `0x20` | `[UTF fileId]` |
| `PM_SEND` | `0x30` | `[UTF target][UTF wire][UTF plain]` |
| `GIFT_LIST_REQ` | `0x40` | *(пусто)* — каталог и свой баланс |
| `GIFT_BUY` | `0x41` | `[UTF target][UTF giftId]` |
| `GIFT_INV_REQ` | `0x42` | `[UTF player]` |
| `STREAM_START` | `0x50` | `[UTF title][UTF url]` — объявить, что этот игрок начал стримить |
| `STREAM_STOP` | `0x51` | *(пусто)* — стрим закончен |
| `STREAM_LIST_REQ` | `0x52` | *(пусто)* — запросить текущий список стримов |
| `STREAM_DONATE` | `0x54` | `[UTF target][double amount]` — задонатить стримеру |

## Сервер → клиент

| Опкод | Байт | Нагрузка |
|---|---|---|
| `HELLO_ACK` | `0x02` | `[int protocolVersion][int maxFileBytes][int maxChunkBytes][byte tier]` |
| `UPLOAD_OK` | `0x13` | `[long transferId][UTF fileId]` |
| `UPLOAD_ERR` | `0x14` | `[long transferId][UTF reason]` |
| `DOWNLOAD_BEGIN` | `0x21` | `[UTF fileId][int totalBytes]` |
| `DOWNLOAD_CHUNK` | `0x22` | `[UTF fileId][int offset][int len][len байт]` |
| `DOWNLOAD_END` | `0x23` | `[UTF fileId]` |
| `DOWNLOAD_ERR` | `0x24` | `[UTF fileId][UTF reason]` |
| `PM_RECV` | `0x31` | `[UTF sender][UTF wire]` |
| `PM_OFFLINE` | `0x32` | `[UTF target]` |
| `GIFT_CATALOG` | `0x43` | `[double balance][int n]` × `{[UTF id][UTF name][UTF icon][double price]}` |
| `GIFT_RESULT` | `0x44` | `[boolean ok][UTF message][double newBalance]` |
| `GIFT_RECV` | `0x45` | `[UTF from][UTF giftName][UTF icon]` |
| `GIFT_INV` | `0x46` | `[UTF player][int n]` × `{[UTF giftName][UTF icon][UTF from]}` |
| `STREAM_LIST` | `0x53` | `[int n]` × `{[UTF player][UTF title][UTF url]}` — список сейчас идущих стримов |
| `STREAM_DONATE_RESULT` | `0x55` | `[boolean ok][UTF message][double newBalance]` |
| `STREAM_DONATE_RECV` | `0x56` | `[UTF from][double amount]` |

## Как проходит обмен

### Рукопожатие

Клиент шлёт `HELLO` сразу, как только сервер объявил канал. Ответ `HELLO_ACK`
несёт лимит на файл, размер чанка и **издание плагина** — по нему клиент решает,
показывать ли премиум-функции.

### Передача файла

Файлы не влезают в одно плагин-сообщение, поэтому режутся на чанки по
`CHUNK_BYTES`:

```
клиент → UPLOAD_BEGIN (transferId, размер, расширение)
клиент → UPLOAD_CHUNK × N   (offset растёт)
клиент → UPLOAD_END (transferId)
сервер → UPLOAD_OK (transferId, fileId)   либо   UPLOAD_ERR (transferId, причина)
```

Скачивание — зеркально: `DOWNLOAD_REQ` → `DOWNLOAD_BEGIN` → `DOWNLOAD_CHUNK` × N
→ `DOWNLOAD_END`. Сервер отдаёт по 6 чанков за тик, чтобы не забить канал.

Сервер пишет приходящее сразу во временный файл и отдаёт исходящее потоком с
диска — целиком в память файл не попадает никогда.

Причины ошибок приходят короткими строками: `too large`, `too many uploads`,
`bad chunk`, `write failed`, `unknown transfer`, `incomplete`, `store failed`,
`too many downloads`, `not found`, `open failed`, `read failed`, `denied`.

### Личные сообщения

`PM_SEND` несёт **две формы** одного сообщения:

- `wire` — структурная запись мода (голосовое, картинка, ответ, опрос, реакция);
- `plain` — простой текст на случай, если у получателя мода нет.

Сервер смотрит, слушает ли получатель канал `pmchat:media`:

| Получатель | Что происходит |
|---|---|
| онлайн, с модом | `PM_RECV` с формой `wire` |
| онлайн, без мода | сервер выполняет `tell-command` с текстом `plain` |
| оффлайн | `PM_OFFLINE` отправителю |

### Подарки

```
клиент → GIFT_LIST_REQ
сервер → GIFT_CATALOG (баланс + позиции)
клиент → GIFT_BUY (кому, что)
сервер → GIFT_RESULT (успех, сообщение, новый баланс)   отправителю
сервер → GIFT_RECV (от кого, что, значок)               получателю
```

Каталог пустой, если подарки выключены (`gifts-enabled: false`) или не подключена
экономика Vault.

### Стримы

```
клиент → STREAM_START (название, ссылка)   |   клиент → STREAM_STOP
сервер → STREAM_LIST (broadcast всем с плагином)

клиент → STREAM_LIST_REQ
сервер → STREAM_LIST (только запросившему)

клиент → STREAM_DONATE (кому, сумма)
сервер → STREAM_DONATE_RESULT (успех, сообщение, новый баланс)   донатеру
сервер → STREAM_DONATE_RECV (от кого, сумма)                     стримеру
```

Список стримов хранится только в памяти плагина (никакого файла) и рассылается
всем онлайн-игрокам с модом при каждом `STREAM_START`/`STREAM_STOP`, а также
отдельно тому, кто прислал `STREAM_LIST_REQ`. `STREAM_DONATE` списывает монеты
через Vault и зачисляет их получателю — плагин не транслирует и не хранит
видео, только заголовок и внешнюю ссылку (Twitch/YouTube и т.п.).

::: warning id файла — это и есть право доступа
`fileId` — 16 случайных символов плюс расширение. Никакой другой проверки прав на
скачивание нет: **кто знает id, тот скачает файл**. Если этого мало, поставьте
свою проверку через `PocketChatMediaDownloadEvent`.
:::
