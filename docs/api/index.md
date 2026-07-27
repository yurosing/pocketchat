# API PocketChat

PocketChat можно расширять из своего кода — и со стороны сервера, и со стороны
клиента. Обе библиотеки лежат на **Maven Central**, подключаются одной строкой и
не тянут за собой ни Bukkit, ни Minecraft в ваш итоговый jar.

## Две библиотеки

| | Для чего | Артефакт |
|---|---|---|
| **[API плагина](/api/plugin)** | Плагины Paper: ловить и отменять ЛС, медиа и покупки подарков, добавлять свои подарки, слать сообщения от имени сервера | `pocketchat-api-plugin` |
| **[API мода](/api/mod)** | Fabric-моды: читать переписки, слушать входящие и исходящие, отправлять сообщения, открывать окно мессенджера | `pocketchat-api-mod` |

Если вы пишете прокси или клиент под другую платформу — вам нужен не API, а
[сырой протокол](/api/protocol).

## Подключение

::: code-group

```groovy [build.gradle]
repositories {
    mavenCentral()
}

dependencies {
    // Плагин Paper
    compileOnly 'io.github.yurosing:pocketchat-api-plugin:1.0.0'

    // Мод Fabric
    modCompileOnly 'io.github.yurosing:pocketchat-api-mod:1.0.0'
}
```

```kotlin [build.gradle.kts]
repositories {
    mavenCentral()
}

dependencies {
    // Плагин Paper
    compileOnly("io.github.yurosing:pocketchat-api-plugin:1.0.0")

    // Мод Fabric
    modCompileOnly("io.github.yurosing:pocketchat-api-mod:1.0.0")
}
```

```xml [pom.xml]
<dependency>
    <groupId>io.github.yurosing</groupId>
    <artifactId>pocketchat-api-plugin</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

:::

::: tip Только `compileOnly`
Классы API уже лежат внутри `PocketChat-<версия>.jar` и внутри
`pmchat-mod-<версия>.jar`. Собирать их в свой jar (`implementation`, shadow,
shade) **не нужно и вредно** — получите два разных класса `PocketChatApi` в
одной JVM и `ClassCastException` на ровном месте.
:::

## Как объявить зависимость

Плагин собирается в двух изданиях с **разными именами** — `PocketChat` и
`PocketChatPro`. Поэтому `depend: [PocketChat]` сломается на серверах с Pro:

```yaml [plugin.yml]
name: MyPlugin
main: com.example.MyPlugin
api-version: '1.21'
softdepend: [PocketChat, PocketChatPro] # [!code highlight]
```

Сам API при этом ищется **не по имени плагина**, а через сервис-менеджер Bukkit,
так что оба издания работают одинаково.

Для мода — мягкая зависимость в `fabric.mod.json`, чтобы ваш мод запускался и
без PocketChat:

```json [fabric.mod.json]
{
  "suggests": {
    "pmchat": "*"
  }
}
```

## Проверка наличия

Всегда проверяйте, что PocketChat вообще стоит, — иначе ваш плагин или мод
упадёт у тех, у кого его нет.

::: code-group

```java [Плагин]
if (PocketChat.isPresent()) {
    PocketChatApi api = PocketChat.api();
    getLogger().info("PocketChat " + api.version() + ", издание " + api.tier());
}
```

```java [Мод]
if (PocketChatClient.isLoaded()) {
    PocketChatClientApi api = PocketChatClient.get();
    LOGGER.info("PocketChat {}", api.modVersion());
}
```

:::

## Версии и совместимость

- Версия API (`1.0.0`) **не совпадает** с версией мода и версией плагина — это
  отдельная нумерация, чтобы обновление мессенджера не ломало ваш код.
- Внутри мажорной версии `1.x` ничего из публичного API не удаляется и не меняет
  сигнатуру. Новые методы и события — да, могут появляться.
- У интерфейсов-слушателей все методы `default`, так что новое событие в
  `1.x` не сломает вашу реализацию.
- Собираться нужно против той же или **более старой** версии API, чем стоит на
  сервере у игроков.

::: warning Что API не покрывает
Внутренние классы мода (`com.pmchat.client.*`, `com.pmchat.screen.*`) и плагина
(`com.pmchat.plugin.*`) — не API. Они меняются между версиями без
предупреждения. Обращайтесь только к `com.pmchat.api.*`.
:::

## Сборка до релиза на Central

Пока нужная версия ещё не опубликована, можно собраться против любого коммита
через JitPack:

```groovy [build.gradle]
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.yurosing.pocketchat:api:main-SNAPSHOT'
    modCompileOnly 'com.github.yurosing.pocketchat:api-mod:main-SNAPSHOT'
}
```

## Дальше

- **[API плагина](/api/plugin)** — события, сервис, подарки, медиа.
- **[API мода](/api/mod)** — слушатели, переписки, отправка.
- **[Протокол](/api/protocol)** — опкоды `pmchat:media`.
- **[Примеры](/api/examples)** — готовые рецепты, которые можно скопировать.
