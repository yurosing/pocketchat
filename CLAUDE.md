# CLAUDE.md — PocketChat (public)

Guidance for working in this repo. Read this instead of re-deriving the
architecture each session.

## What this is

**PocketChat** is a **client-side Fabric mod for Minecraft 1.21.11** that turns a
server's plain `/m` private messages into a Telegram-style messenger (threaded
chats, bubbles, voice notes, stickers, media, calls, profiles, gifts). It works
**with no server plugin** — it parses `/m` chat lines — but an **optional Paper
server plugin** upgrades it (routes PMs/media through the server, adds balance +
a Vault gift shop).

This repo (`yurosing/pocketchat`) is the **public** build:
- Mod version scheme is `X.Y.Z` (no `-secret`).
- Ships the mod + the **free** server plugin edition only.
- The **Pro plugin** and a special Pro mod build live ONLY in the private repo
  `yurosing/pocketchat-sec` — do not add them here. The only Pro-gated client
  feature (voice→text transcription) stays locked in public unless a Pro plugin
  is present (which public doesn't distribute).
- Default branch: `main`. The **VitePress docs site is published from THIS repo**
  (`docs/`, workflow `docs.yml`, base `/pocketchat/`).

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
server-plugin/           — the Paper plugin (free edition only in this repo)
docs/                    — VitePress docs site (RU root + /en); published from here
.github/workflows/       — release.yml (build+publish, free-only), docs.yml
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
- **PmServerMedia** — client side of the `pmchat:media` plugin channel. Detects the
  plugin (`isAvailable()`), tier (`isPro()`), streams media up/down, routes PMs,
  and the **gift** subsystem (catalog/balance/inventory + buy). Opcodes MUST match
  the plugin's `Proto`.
- **PmWire** — wire-string encoding for structured messages over `/m` (voice, images,
  reactions, replies, forwards, polls, typing/seen meta, secret-chat handshake).
- **PmHistory / PmMessage** — persisted conversations + message model.
- Media/voice stack: PmImages, PmHosts (external image hosts w/ fallback order),
  PmMedia/PmVlc/PmVideo/PmYtDlp/PmYouTube/PmGif (VLC + JCodec players), PmVoice
  (recording), PmStt/PmVoiceTranscript (Vosk offline speech-to-text — **Pro-gated**),
  PmSvc (Simple Voice Chat calls), PmClipboard, PmCrypto (secret chats), PmUpdate.

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

### Server plugin (`com.pmchat.plugin`)
- **PocketChatPlugin** — `onEnable`; loads the gift catalog from config.yml. (This repo
  builds only the free `PocketChat` edition — `server-plugin/build.gradle` `editions`.)
- **MediaChannel** — the `pmchat:media` `PluginMessageListener`: media relay (chunked
  to disk), PM routing, gifts (Vault economy via the `Economy` service, `GiftStore`).
  **Proto** — the opcode contract (mirror of the mod's).
- **MediaStore / GiftStore** — disk persistence.

## Wire protocol (mod ↔ plugin)

Single plugin-messaging channel `pmchat:media`. Each message = `[opcode byte][payload]`
written with `DataOutputStream`. Opcodes are defined **twice** (plugin `Proto.java`
and mod `PmServerMedia.java`) and MUST stay in sync. Handshake HELLO/HELLO_ACK carries
the tier. Media is chunked. Gifts: `GIFT_LIST_REQ/GIFT_BUY/GIFT_INV_REQ` (C→S) and
`GIFT_CATALOG/GIFT_RESULT/GIFT_RECV/GIFT_INV` (S→C).

## Build & release

- Gradle + **fabric-loom**, Java 21, MC 1.21.11, Yarn mappings. **Mod Menu is resolved
  from Modrinth's maven** (`maven.modrinth:modmenu:...`) — terraformersmc is flaky/404s,
  do NOT depend on it. Deps are shaded via loom `include` (Vosk, gson, JCodec, vlcj/JNA).
- **You cannot build in this sandbox** — maven.fabricmc.net, Mojang, and repo.papermc.io
  are blocked by egress policy (403). Only Maven Central + the Gradle plugin portal are
  reachable. **Rely on GitHub Actions to compile.**
- `release.yml` builds the mod + the free plugin (no Pro jar) and publishes a GitHub
  Release using `RELEASE_NOTES.md` as the body. Pushing to `main` also rebuilds docs.
- **Releasing**: pushing a tag is blocked (proxy 403). Instead trigger `release.yml` via
  `workflow_dispatch` (mcp github `actions_run_trigger`); its "Resolve tag" step derives
  the tag from `gradle.properties` `mod_version`, and `action-gh-release` creates the tag.
  Bump `mod_version` + `RELEASE_NOTES.md` first, and check existing releases so the new
  version is higher (semver "latest").
- Watch runs with mcp github `actions_list`/`get_job_logs`. Fabric's first build is slow
  (~5–10 min).

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
- This repo is synced FROM `pocketchat-sec` (its code is the source of truth, minus the
  Pro plugin edition + special Pro mod). Keep public free-only.
