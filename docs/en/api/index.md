# PocketChat API

PocketChat's mod can be extended from your own code. The library lives on
**Maven Central**, is one line to add, and pulls neither Bukkit nor Minecraft
into your jar.

## The library

| | What for | Artifact |
|---|---|---|
| **[Mod API](/en/api/mod)** | Fabric mods: read conversations, listen to incoming and outgoing messages, send messages, open the messenger | `pocketchat-api-mod` |

This repository no longer builds or ships a PocketChat server plugin — the mod
works standalone by default, parsing `/m` right in chat.

## Adding the dependency

The Fabric mod is not built with Maven — it uses Gradle + Loom:

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

::: tip `compileOnly` only
The API classes already ship inside `pmchat-mod-<version>.jar`. Bundling them
into your own jar (`implementation`, shadow, shade) is **unnecessary and
harmful** — you end up with two different classes in one JVM and a
`ClassCastException` out of nowhere.
:::

## Declaring the dependency

Declare a soft dependency in `fabric.mod.json` so your mod still starts
without PocketChat:

```json [fabric.mod.json]
{
  "suggests": {
    "pmchat": "*"
  }
}
```

## Checking availability

Always check that PocketChat is installed at all — otherwise your mod breaks
for everyone who does not have it:

```java
if (PocketChatClient.isLoaded()) {
    PocketChatClientApi api = PocketChatClient.get();
    LOGGER.info("PocketChat {}", api.modVersion());
}
```

## Versioning and compatibility

- The API version (`1.0.0`) is **not** the mod version — it is numbered
  separately so a messenger update does not break your code.
- Within the `1.x` major, nothing public is removed and no signature changes.
  New methods and events may still appear.
- Every listener interface has `default` methods only, so a new event in `1.x`
  will not break your implementation.
- Build against the same version as, or an **older** version than, the one
  running on your players' servers.

::: warning What the API does not cover
The mod's internals (`com.pmchat.client.*`, `com.pmchat.screen.*`) are not
API. They change between versions without notice. Only touch
`com.pmchat.api.*`.
:::

## Building before a Central release

While a version is not published yet, you can build against any commit through
JitPack:

```groovy [build.gradle]
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    modCompileOnly 'com.github.yurosing.pocketchat:api-mod:main-SNAPSHOT'
}
```

## Next

- **[Mod API](/en/api/mod)** — listeners, conversations, sending.
- **[Examples](/en/api/examples)** — recipes you can copy as-is.
