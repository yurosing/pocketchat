# CLAUDE.md — PocketChat (public)

Guidance for working in this repo. Read this instead of re-deriving the
architecture each session.

## What this is

**PocketChat** is a **client-side Fabric mod for Minecraft 26.2** that turns a
server's plain `/m` private messages into a Telegram-style messenger (threaded
chats, bubbles, voice notes, stickers, media, calls, profiles, gifts). It works
**with no server plugin** — it parses `/m` chat lines. This repo no longer builds
or ships a server plugin at all (removed — see "No server plugin" below); the
client mod still speaks the `pmchat:media` wire protocol client-side
(`PmServerMedia`) so it stays compatible with any third-party or private server
implementing it, but nothing here provides one.

This repo (`yurosing/pocketchat`) is the **public** build:
- Mod version scheme is `X.Y.Z` (no `-secret`).
- Ships the mod only.
- A special Pro mod build lives ONLY in the private repo `yurosing/pocketchat-sec`
  — do not add it here. The only Pro-gated client feature (voice→text
  transcription) stays locked in public.
- Default branch: `main`. The **VitePress docs site is published from THIS repo**
  (`docs/`, workflow `docs.yml`, base `/pocketchat/`).

## No server plugin

The Paper server plugin (`server-plugin/`, editions `PocketChat`/`PocketChatPro`,
its published API `pocketchat-api-plugin`) has been **removed from this repo** —
gifts, Vault balance, server-relayed media, streams, and the offline-message
mailbox all went with it, since they were plugin-only features. Client code that
optionally talks to a plugin (`PmServerMedia`, gift/balance/streams UI) was left
in place — it degrades gracefully when no plugin answers, which is now always —
but don't add anything that *requires* a plugin to exist; there isn't one to
build or test against. `docs/api/plugin.md` and `docs/api/protocol.md` were
deleted along with their sidebar entries; `docs/api/index.md` and
`docs/api/examples.md` were trimmed to the mod API only.

::: warning
This repo is normally synced FROM `pocketchat-sec` (see the note at the bottom
of this file) — if that private repo still carries a server-plugin edition, an
unmodified future sync could reintroduce `server-plugin/` here. Flag that to
whoever runs the sync rather than silently re-adding it.
:::

## Layout

```
src/main/java/com/pmchat/
  client/   — non-UI logic (the mod's "backend")
  screen/   — all GUI (Screens, widgets, theming)
  mixin/    — ChatScreenMixin (hooks the vanilla chat screen)
src/main/resources/
  fabric.mod.json        — entrypoints: client=PmChatClient, modmenu=PmModMenu
  pmchat.mixins.json
  assets/pmchat/lang/    — ru_ru.json, en_us.json (ALL user-facing strings)
api-mod/                 — PUBLIC client API (com.pmchat.api.client), plain Java, no loom
docs/                    — VitePress docs site (RU root + /en); published from here
.github/workflows/       — release.yml (build+publish), docs.yml,
                           publish-api.yml (Maven Central, workflow_dispatch)
```

### Client package (`com.pmchat.client`) — key files
- **PmChatClient** — entrypoint + hub. Chat-line capture/parsing (incoming/outgoing/
  global/channel/Discord/CoreProtect regexes from config), routing sends through
  `pmDeliver`, history, toasts, mentions, update check. Static `config`, `history`.
  `selfName()`, `commandTarget(conv)` (alias→/m target), `giftToast(...)`,
  `setBlocked/isBlocked`, `knownBalance`.
- **PmConfig** — the whole config model (GSON `pmchat.json`). Every feature flag /
  list / map lives here. `load()` has per-field null-guards + migrations — ADD A
  GUARD for every new collection field. Notable: contacts, aliases(+aliasAsTarget),
  blocked(+ignoreCommand), profileBirthday/Description, channels, groups(+avatar),
  filters, pinned, stickerCache.
- **PmServerMedia** — client side of the `pmchat:media` plugin channel. Detects a
  plugin (`isAvailable()`), tier (`isPro()`), streams media up/down, routes PMs,
  and the **gift** subsystem (catalog/balance/inventory + buy) — all no-ops now
  that this repo ships no plugin to answer the handshake. Opcode constants are
  private to this class (no external API mirrors them anymore).
- **PmWire** — wire-string encoding for structured messages over `/m` (voice, images,
  reactions, replies, forwards, polls, typing/seen meta).
- **PmHistory / PmMessage** — persisted conversations + message model.
- Media/voice stack: PmImages, PmHosts (external image hosts w/ fallback order),
  PmMedia/PmVlc/PmVideo/PmYtDlp/PmYouTube/PmGif (VLC + JCodec players), PmVoice
  (recording), PmStt/PmVoiceTranscript (Vosk offline speech-to-text — **Pro-gated**),
  PmSvc (Simple Voice Chat calls), PmClipboard, PmCrypto (local history encryption
  at rest), PmUpdate.

### Screen package (`com.pmchat.screen`) — key files
- **PmScreen** — THE main messenger window (~5k lines). Conversation list, chat view,
  composer, context menu, media pickers, calls, groups. `applyTheme()` builds the
  palette from `config.theme`. Custom immediate-mode drawing + `FlatButton` widgets;
  hit-testing via stored `int[]` rects. Rebuilds widgets in `rebuild()`/`init()`.
- **PmProfileScreen** — player profile (own + others): full display name, auto role,
  status, birthday/description (own), balance (own), gifts, blacklist, rename(alias).
- **PmSettingsScreen** — options grid (cycle-a-value rows). Bump `rows` when adding one.
- **PmTheme** — dialog-window palettes + theme registry (`COUNT`, `isLight`, `nameKey`).
  Themes: 0 dark,1 light,2 slate,3 midnight,4 nord,5 rosé,6 sand. `PmScreen.applyTheme`
  has a matching branch per theme.
- **PmRoles** — role badges (Ⓒ/Ⓗ/Ⓜ/Ⓔ/Ⓓ) + `detect(fullNick)` (auto from prefix/suffix).
- **PmNames** — full formatted nick (prefix+name+suffix) from the tab-list display name.
- **PmPalettes** — message/name/badge color arrays. **PmIcons** — pixel-bitmap icons.
- FlatButton, PmFilters*/PmMediaScreen — dialogs.

## Public API (`com.pmchat.api.client`) — published to Maven Central

`pocketchat-api-mod` (`api-mod`) — `PocketChatClient`/`PocketChatClientApi`,
`PocketChatListener`, `PmChatMessage`/`Conversation`. **Zero Minecraft imports** —
keep it that way, it is what makes the artifact version-proof. Packed UNRELOCATED
into the mod jar (`from project(':api-mod').sourceSets.main.output` in the `jar`
task), so consumers use `compileOnly`/`modCompileOnly` only.

Fire points live in `PocketChatClientImpl` (static `fireX` helpers) and are called
from `PmChatClient` (receive/send/gift/init), `PmServerMedia` (HELLO_ACK, reset)
and `PmScreen` (conversation opened). Adding a listener method → give it a `default`
body so existing implementors keep compiling.

## Build & release

- Gradle + **fabric-loom** (1.17.20), **Java 25**, MC 26.2. **26.1+ ships fully
  unobfuscated** (real names baked into the jar) — Mojang doesn't even publish a
  mappings file for these versions, so there is **no `mappings` line in `build.gradle`
  and no `yarn_mappings` property at all**, and mod dependencies (fabric-loader,
  fabric-api) use plain `implementation`/`compileOnly`, not the old
  `modImplementation`/`modCompileOnly` (no remapping step exists anymore). **Mod Menu
  is resolved from Modrinth's maven** (`maven.modrinth:modmenu:...`) — terraformersmc
  is flaky/404s, do NOT depend on it. Deps are shaded via loom `include` (Vosk, gson,
  JCodec, vlcj/JNA).
- **You cannot build in this sandbox** — maven.fabricmc.net, Mojang, and repo.papermc.io
  are blocked by egress policy (403). Only Maven Central + the Gradle plugin portal are
  reachable. **Rely on GitHub Actions to compile.**
- **26.2 port status**: `build.gradle`/`gradle.properties`/the workflow YAMLs are updated
  for 26.2 (loader 0.19.3, fabric-api 0.158.0+26.2, Loom 1.17.20, Java 25, no mappings
  dependency), and this configuration now resolves and starts compiling in CI. The mod
  doesn't register any items/blocks, so the registration-rewrite / item-model-definition
  / `ItemStackTemplate` breaks from the 1.21.2–26.1 migration notes don't apply here —
  watch `build-check.yml` for any remaining compile errors (renamed GUI/rendering/
  client-network classes, mixin target changes) and fix from there.
- `release.yml` builds the mod and publishes a GitHub Release using
  `RELEASE_NOTES.md` as the body. Pushing to `main` also rebuilds docs.
- **Releasing**: pushing a tag is blocked (proxy 403). Instead trigger `release.yml` via
  `workflow_dispatch` (mcp github `actions_run_trigger`); its "Resolve tag" step derives
  the tag from `gradle.properties` `mod_version`, and `action-gh-release` creates the tag.
  Bump `mod_version` + `RELEASE_NOTES.md` first, and check existing releases so the new
  version is higher (semver "latest").
- Watch runs with mcp github `actions_list`/`get_job_logs`. Fabric's first build is slow
  (~5–10 min).
- **API artifact**: `publish-api.yml`, `workflow_dispatch`. `dry_run: true` (default)
  compiles `api-mod` and publishes it to mavenLocal — no credentials, so it is the
  fast way to check an API change. `dry_run: false` deploys to Central and needs
  `MAVEN_CENTRAL_USERNAME/PASSWORD` + `SIGNING_KEY/PASSWORD`. Version is
  `api_version` in `gradle.properties`.
- **The API module DOES compile in this sandbox**: `api-mod` is dependency-free —
  `javac` it directly. Worth doing before pushing — CI is slow.

## Gotchas / conventions

- **All user-facing strings go in `lang/ru_ru.json` + `en_us.json`** (validate JSON).
  RU is primary. Update `docs/` (RU + `/en`) + the version label in
  `docs/.vitepress/config.mjs` when you add a user-visible feature.
- `gradlew` must stay executable (`git update-index --chmod=+x gradlew`) or CI fails
  with `Permission denied` (126).
- Adding a new `PmConfig` collection field → add its null-guard in `load()`.
- New theme → add a branch in BOTH `PmTheme.dialog` and `PmScreen.applyTheme`, bump
  `PmTheme.COUNT`, keep `isLight` correct.
- Adding a settings row → bump `rows` in `PmSettingsScreen.init()`.
- PmScreen is immediate-mode: draw + store rects, handle clicks in `mouseClicked`.
- Docs code fences support ```` ```java [File.java] ```` titles via the `codeBlockTitles`
  markdown rule in `config.mjs` — VitePress itself drops that syntax outside
  `::: code-group`, and the rule has to read the title in a **core** rule because
  `token.info` is already stripped by render time.
- This repo is synced FROM `pocketchat-sec` (its code is the source of truth, minus the
  Pro plugin edition + special Pro mod). Keep public free-only.
