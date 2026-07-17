# What is it

**PocketChat** turns the server's `/m` command (private messages) into a real
Telegram-style chat: message bubbles, voice notes, reactions, stickers, history
and search.

The mod runs **entirely on the client**: no plugin, permissions or server setup
required. Just drop the `.jar` into `mods/` and start talking.

<Shot src="/img/hero.png" caption="Overview: conversation list on the left, open chat on the right." />

## How it works

The mod reads lines like "(PM) Nick → me » text" from the regular chat, sorts
them into threads and saves them to disk. Your messages are sent with the plain
`/m Nick text` command.

- Players **without** the mod see plain text.
- Players **with** the mod see everything: bubbles, reactions, statuses.

::: tip Set it up once per server
The mod needs to be matched to your server's PM format once — via regular
expressions. Defaults target the Essentials format. If incoming PMs don't show
up, see [Server regex](/en/config/patterns).
:::

## What's inside

- 🧵 **Threads** — a separate conversation per contact with unread badges.
- 🎙️ **Voice** — up to 20 seconds with a waveform and speech-to-text.
- ↩️ **Replies & reactions** — right-click context menu.
- 🎞️ **Stickers & GIFs** — your own animated files from a folder.
- 🖼️ **Photos** — paste from clipboard with <kbd>Ctrl</kbd>+<kbd>V</kbd>.
- 🔍 **Search & history** — everything stored locally, forever.
- 🌍 **Global chat** — the server's public chat as the same bubbles.
- 🎨 **Themes & colors** — dark/light, bubble color, wallpapers.
- 🟢 **Who has the mod** — a dot by the name shows PocketChat presence.

## Requirements

| Component | Version |
|---|---|
| Minecraft | 1.21.11 |
| [Fabric Loader](https://fabricmc.net/use/) | 0.15+ |
| [Fabric API](https://modrinth.com/mod/fabric-api) | any for 1.21.11 |
| Side | client only |
