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
- **Ignore players (chat)** — like `/ignoreplayer`: add a nick and their chat
  messages disappear.
- **Ignore players (Discord)** — same, but for authors of Discord messages (by
  nick).
- **Text filters** — hides messages containing the given text. Each filter has a
  scope: **Both**, **Global**, or **Discord**.

To add an entry, type a nick or text into the field and press **Add**. To remove
it, use the `✕` on the right of the row.

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
