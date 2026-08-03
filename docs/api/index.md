# API PocketChat

PocketChat можно расширять из своего кода — и со стороны сервера, и со стороны
клиента. Обе библиотеки лежат на **Maven Central**, подключаются одной строкой и
не тянут за собой ни Bukkit, ни Minecraft в ваш итоговый jar.

## Библиотека

| | Для чего | Артефакт |
|---|---|---|
| **[API мода](/api/mod)** | Fabric-моды: читать переписки, слушать входящие и исходящие, отправлять сообщения, открывать окно мессенджера | `pocketchat-api-mod` |

Серверного плагина PocketChat этот репозиторий больше не собирает и не
распространяет — мод по умолчанию работает вообще без него, парся `/m` прямо
в чате.

## Подключение

Мод Fabric на Maven не собирают (сборка идёт через Gradle + Loom):

```groovy [build.gradle]
repositories {
    mavenCentral()
}

dependencies {
    modCompileOnly 'io.github.yurosing:pocketchat-api-mod:1.0.0'
}
```

```kotlin [build.gradle.kts]
repositories {
    mavenCentral()
}

dependencies {
    modCompileOnly("io.github.yurosing:pocketchat-api-mod:1.0.0")
}
```

::: tip Только `compileOnly`
Классы API уже лежат внутри `pmchat-mod-<версия>.jar`. Собирать их в свой jar
(`implementation`, shadow, shade) **не нужно и вредно** — получите два разных
класса в одной JVM и `ClassCastException` на ровном месте.
:::

## Как объявить зависимость

Мягкая зависимость в `fabric.mod.json`, чтобы ваш мод запускался и без
PocketChat:

```json [fabric.mod.json]
{
  "suggests": {
    "pmchat": "*"
  }
}
```

## Проверка наличия

Всегда проверяйте, что PocketChat вообще стоит, — иначе ваш мод упадёт у тех,
у кого его нет:

```java
if (PocketChatClient.isLoaded()) {
    PocketChatClientApi api = PocketChatClient.get();
    LOGGER.info("PocketChat {}", api.modVersion());
}
```

## Версии и совместимость

- Версия API (`1.0.0`) **не совпадает** с версией мода — это отдельная
  нумерация, чтобы обновление мессенджера не ломало ваш код.
- Внутри мажорной версии `1.x` ничего из публичного API не удаляется и не меняет
  сигнатуру. Новые методы и события — да, могут появляться.
- У интерфейсов-слушателей все методы `default`, так что новое событие в
  `1.x` не сломает вашу реализацию.
- Собираться нужно против той же или **более старой** версии API, чем стоит на
  сервере у игроков.

::: warning Что API не покрывает
Внутренние классы мода (`com.pmchat.client.*`, `com.pmchat.screen.*`) — не API.
Они меняются между версиями без предупреждения. Обращайтесь только к
`com.pmchat.api.*`.
:::

## Сборка до релиза на Central

Пока нужная версия ещё не опубликована, можно собраться против любого коммита
через JitPack:

```groovy [build.gradle]
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    modCompileOnly 'com.github.yurosing.pocketchat:api-mod:main-SNAPSHOT'
}
```

## Дальше

- **[API мода](/api/mod)** — слушатели, переписки, отправка.
- **[Примеры](/api/examples)** — готовые рецепты, которые можно скопировать.
