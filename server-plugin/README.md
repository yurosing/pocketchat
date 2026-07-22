# PocketChat (server plugin)

Optional **Paper 1.21** server plugin for the PocketChat client mod. Ships in two
editions built from this one codebase:

- **PocketChat** — free: routes private messages and media (photos, voice, video)
  through the server instead of `/m` and external file hosts.
- **PocketChatPro** — everything in the free edition **plus** it unlocks premium
  client features (currently voice-message transcription). The edition is baked
  into the jar via the plugin name, so it can't be enabled with a config edit.

When the plugin is present the mod detects it automatically:

- **No plugin** on the server → premium features are shown greyed out with
  "требуется плагин на сервере", and messaging falls back to `/m` + external hosts.
- **PocketChat** (free) → messaging/media go through the server; premium features
  stay locked.
- **PocketChatPro** → premium features unlocked.

## Install

Drop **one** of the jars into your server's `plugins/` folder and restart:

- `PocketChat-<version>.jar` for the free edition, or
- `PocketChatPro-<version>.jar` for the Pro edition.

Mod users are switched over automatically on their next join.

## Config (`plugins/PocketChat[/Pro]/config.yml`)

| Key | Default | Meaning |
|-----|---------|---------|
| `retention-hours` | `168` | Delete stored media older than this (hours). |
| `max-total-mb` | `512` | Cap on total media storage; oldest files trimmed first. |
| `max-file-mb` | `25` | Largest single file accepted; bigger ones fall back to external hosts. |
| `tell-command` | `msg` | Whisper command used to deliver to players without the mod (msg/tell/w/m…). |

## Notes

- Uploads are streamed to a temp file and downloads streamed off disk — whole
  files are never held in memory, so large media doesn't spike server RAM.
- Uses only stable Bukkit API (plugin messaging, scheduler, config) — no NMS.
  Runs on any Paper 1.21.x server. (Not Folia-aware yet.)
