# pmchat.json overview

All settings live in `config/pmchat.json` and take effect **on the next launch**.
Most are easier to change in-game: **ModMenu → PocketChat → Settings** (or the gear
button in the messenger window).

<Shot src="/img/settings.png" caption="The in-game PocketChat settings screen." />

## Main settings

| Key | Default | Description |
|---|---|---|
| `msgCommand` | `"m"` | Server command for private messages |
| `payCommand` | `"pay"` | Command for in-chat money transfers |
| `hideChatLines` | `false` | Hide captured PM lines from the vanilla chat |
| `globalPrefix` | `"!"` | Prefix when sending to the global chat |
| `soundEnabled` | `true` | Sound on incoming message |
| `notifySound` | `0` | Notification sound: 0 xp, 1 bell, 2 item, 3 off |
| `notifyVolume` | `100` | Notification volume, % (5–100) |
| `dnd` | `false` | Do Not Disturb — no popups or sound |
| `mentionEnabled` | `true` | Highlight and ping when your nick is mentioned |
| `mentionExtra` | `""` | Extra trigger words, comma-separated |
| `mentionOnCopy` | `false` | Prepend "nick: " when copying |
| `closeOnDamage` | `false` | Close the window when you take damage |
| `enableMeta` | `true` | Typing indicators and read receipts between mods |

## Settings sections

- [Appearance](/en/config/appearance) — theme, colors, wallpaper, scale.
- [Voice & TTS](/en/config/voice) — Vosk and TTS.
- [Server regex](/en/config/patterns) — capturing PMs, global and channels.
- [File hosts](/en/config/hosts) — where photos and voice upload.
- [Staff & CoreProtect](/en/config/staff) — features for server staff.

::: warning Edit carefully
The file is JSON. Mind the quotes and commas. If you corrupt it, the mod recreates
a fresh one with defaults on launch.
:::
