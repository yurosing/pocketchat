# What is it

**PocketChat** turns plain private messages (`/m`) into a real chat, like
Telegram: colorful bubbles, voice notes, reactions, stickers, chat history and
search.

The mod installs **only on your own computer**. Nothing to set up on the server,
no permissions to ask for. Drop the file into `mods` and you're using it.

<Shot src="/img/hero.png" caption="Overview: conversation list on the left, open chat on the right." />

## How it works

The mod watches the regular chat and picks out private messages in it — both
incoming and outgoing. It shows each one nicely in its own window and saves it
on your computer. When you send a message from that window, the mod just types
the plain `/m Nick text` command for you, as if you typed it yourself.

- A contact **without** the mod sees a completely normal message.
- A contact **with** the mod sees everything you see: bubbles, reactions.

::: tip If messages don't show up
Your server's private messages probably look different from what the mod
expects by default. Easy to fix — see
[Setting it up for your server](/en/config/patterns).
:::

## What it can do

- 🧵 **Threads** — a separate conversation with every person, with an unread
  counter.
- 🎙️ **Voice notes** — up to 20 seconds, can be turned into text.
- ↩️ **Replies & reactions** — right-click a message.
- 🎞️ **Stickers & GIFs** — your own pictures from a folder.
- 🖼️ **Photos** — paste an image from the clipboard with
  <kbd>Ctrl</kbd>+<kbd>V</kbd>.
- 🔍 **Search** — across your whole chat history.
- 🌍 **Server's global chat** — also shown as nice bubbles, reply right there.
- 🎨 **Theme and colors** — dark or light, your own bubble color, chat
  wallpaper.
- 🟢 **Mod indicator** — a dot shows whether the other person also has
  PocketChat.

## What you need to install it

| Component | Version |
|---|---|
| Minecraft | 1.21.11 |
| [Fabric Loader](https://fabricmc.net/use/) | any recent version |
| [Fabric API](https://modrinth.com/mod/fabric-api) | required, the mod won't work without it |
| Where to install | only your own computer |
