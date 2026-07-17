# Mod settings

The easiest way to change settings is right in the game — via **ModMenu →
PocketChat** or the gear button in the messenger window itself. Everything
described below can be found there.

<Shot src="/img/settings.png" caption="The in-game PocketChat settings screen." />

Under the hood, all settings live in a file called `pmchat.json` (where it is —
see [Where the mod's files live](/en/reference/folders)). You only need to open
that file by hand if you want to tweak something very specific — usually that's
not necessary.

## Main options

| Setting | Default | What it does |
|---|---|---|
| Private message command | `m` | Which command the mod uses to send PMs |
| Money transfer command | `pay` | Which command sends money from chat |
| Hide PM lines in regular chat | Off | Removes lines the mod already shows in its own window |
| Symbol before global chat message | `!` | The mod adds this automatically when sending to global |
| Sound on new message | On | |
| Do Not Disturb | Off | Turns off popups and sound |
| React to your name being mentioned | On | Highlight and sound when someone types your nickname |
| Close window when taking damage | Off | |

## Settings sections

- [Appearance](/en/config/appearance) — theme, colors, wallpaper, window size.
- [Voice & TTS](/en/config/voice) — speech-to-text and reading chat aloud.
- [Setting it up for your server](/en/config/patterns) — if messages aren't captured.
- [Where files get uploaded](/en/config/hosts) — photos, stickers, voice notes.
- [For server staff](/en/config/staff) — features for people with moderator rights.

::: warning Be careful with the settings file
If you open `pmchat.json` by hand — don't remove quotes or commas, or the file
stops loading. If something breaks, just delete the file; the mod recreates it
with defaults on the next launch.
:::
