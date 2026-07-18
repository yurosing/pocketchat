# Chat filters (No Global Chat)

The mod can hide unwanted messages from the in-game chat: the whole global chat,
Discord messages, specific players, and messages matched by text. Everything is
configured in a dedicated window.

## Opening it

In [settings](/en/config/) there is a **"Chat filters"** row — click it to open
the filters window.

## What you can disable

- **Disable global chat** — hides all global chat messages.
- **Disable Discord** — hides messages like `(Discord) Nick » text`.

Both are toggles right in the main window.

## Category folders

Below the toggles are three sticker-like folder tiles: **Chat ignore**, **Discord
ignore**, and **Text filters**. Each tile shows how many entries it holds.
Clicking a tile opens that category in its own window — so the nick/message list
never runs off-screen, even with lots of entries.

### Inside a folder

- **Add** — type a nick or text in the top field and press **Add**.
- **Edit** — the pencil `✎` next to an entry loads it into the field; fix it and
  press **Save**. For text filters the scope (**Both / Global / Discord**) is
  edited here too.
- **Delete** — the `✕`.
- **Nick autocomplete** — start typing a nick and press **Tab** (cycles through
  online players), like the normal chat.
- Long messages wrap to several lines and are shown in full; the list
  **scrolls** with the mouse wheel.

One entry can be up to **256 characters**.

## What is NEVER filtered

- **Private messages** (`(PM)`), channels (clan/alliance/group), and **your own
  messages** are never hidden.

::: tip Your own messages always show
On servers where local and global chat look identical (`Nick » text`), enabling
"disable global chat" used to hide your own local line too. Now your own messages
are never filtered — you write in local chat and the message stays visible.
:::

## If global/Discord chat is detected incorrectly

Message formats differ between servers. The global and Discord patterns can be
adjusted in `pmchat.json` — see
[Adapting to your server](/en/config/patterns).

## Standalone mod

The same filters are available as a standalone **No Global Chat** mod — without
PocketChat, if you only need chat filtering.
