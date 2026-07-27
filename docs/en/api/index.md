# PocketChat API

PocketChat can be extended from your own code, on both the server and the client
side. Both libraries live on **Maven Central**, are one line to add, and pull
neither Bukkit nor Minecraft into your jar.

## Two libraries

| | What for | Artifact |
|---|---|---|
| **[Plugin API](/en/api/plugin)** | Paper plugins: intercept and cancel private messages, media and gift purchases, add your own gifts, send messages on the server's behalf | `pocketchat-api-plugin` |
| **[Mod API](/en/api/mod)** | Fabric mods: read conversations, listen to incoming and outgoing messages, send messages, open the messenger | `pocketchat-api-mod` |

Writing a proxy or a client for another platform? You want the
[raw protocol](/en/api/protocol) instead.

## Adding the dependency

::: code-group

```groovy [build.gradle]
repositories {
    mavenCentral()
}

dependencies {
    // Paper plugin
    compileOnly 'io.github.yurosing:pocketchat-api-plugin:1.0.0'

    // Fabric mod
    modCompileOnly 'io.github.yurosing:pocketchat-api-mod:1.0.0'
}
```

```kotlin [build.gradle.kts]
repositories {
    mavenCentral()
}

dependencies {
    // Paper plugin
    compileOnly("io.github.yurosing:pocketchat-api-plugin:1.0.0")

    // Fabric mod
    modCompileOnly("io.github.yurosing:pocketchat-api-mod:1.0.0")
}
```

```xml [pom.xml]
<!-- add inside the <dependencies> block of your pom.xml -->
<dependencies>
    <dependency>
        <groupId>io.github.yurosing</groupId>
        <artifactId>pocketchat-api-plugin</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

:::

::: tip Maven is plugin-only
The Fabric mod is not built with Maven — it uses Gradle + Loom — so the Maven
tab only has the plugin dependency. Use the Gradle tab above for the mod.
:::

::: tip `compileOnly` only
The API classes already ship inside `PocketChat-<version>.jar` and inside
`pmchat-mod-<version>.jar`. Bundling them into your own jar (`implementation`,
shadow, shade) is **unnecessary and harmful** — you end up with two different
`PocketChatApi` classes in one JVM and a `ClassCastException` out of nowhere.
:::

## Declaring the dependency

The plugin is built in two editions with **different names** — `PocketChat` and
`PocketChatPro` — so `depend: [PocketChat]` breaks on Pro servers:

```yaml [plugin.yml]
name: MyPlugin
main: com.example.MyPlugin
api-version: '1.21'
softdepend: [PocketChat, PocketChatPro] # [!code highlight]
```

The API itself is looked up through Bukkit's services manager rather than by
plugin name, so both editions behave identically.

For a mod, declare a soft dependency in `fabric.mod.json` so your mod still
starts without PocketChat:

```json [fabric.mod.json]
{
  "suggests": {
    "pmchat": "*"
  }
}
```

## Checking availability

Always check that PocketChat is installed at all — otherwise your plugin or mod
breaks for everyone who does not have it.

::: code-group

```java [Plugin]
if (PocketChat.isPresent()) {
    PocketChatApi api = PocketChat.api();
    getLogger().info("PocketChat " + api.version() + ", edition " + api.tier());
}
```

```java [Mod]
if (PocketChatClient.isLoaded()) {
    PocketChatClientApi api = PocketChatClient.get();
    LOGGER.info("PocketChat {}", api.modVersion());
}
```

:::

## Versioning and compatibility

- The API version (`1.0.0`) is **not** the mod version or the plugin version —
  it is numbered separately so a messenger update does not break your code.
- Within the `1.x` major, nothing public is removed and no signature changes.
  New methods and events may still appear.
- Every listener interface has `default` methods only, so a new event in `1.x`
  will not break your implementation.
- Build against the same version as, or an **older** version than, the one
  running on your players' servers.

::: warning What the API does not cover
The mod's internals (`com.pmchat.client.*`, `com.pmchat.screen.*`) and the
plugin's (`com.pmchat.plugin.*`) are not API. They change between versions
without notice. Only touch `com.pmchat.api.*`.
:::

## Building before a Central release

While a version is not published yet, you can build against any commit through
JitPack:

```groovy [build.gradle]
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.yurosing.pocketchat:api:main-SNAPSHOT'
    modCompileOnly 'com.github.yurosing.pocketchat:api-mod:main-SNAPSHOT'
}
```

## Next

- **[Plugin API](/en/api/plugin)** — events, service, gifts, media.
- **[Mod API](/en/api/mod)** — listeners, conversations, sending.
- **[Protocol](/en/api/protocol)** — the `pmchat:media` opcodes.
- **[Examples](/en/api/examples)** — recipes you can copy as-is.
