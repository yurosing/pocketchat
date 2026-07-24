# Channels & groups

## Channels (clan / alliance / group)

**Channels** are separate server chats, like a clan or alliance chat. A tab for
one appears automatically as soon as the mod sees the first message in it.
Three come set up out of the box:

| Tab | How to send a message |
|---|---|
| Clan | `.text` |
| Alliance | `ally text` |
| Group | `gc text` |

<Shot src="/img/groups.png" caption="Group chat and channel tabs." />

If your server uses different chat names or commands, they can be adjusted in
[settings](/en/config/patterns#channels).

## Groups

**Groups** are chats with several friends at once, created right in the mod.
Under the hood, the mod just sends a plain private message to every member
separately and combines all replies into one shared conversation — the server
just sees ordinary `/m` commands.

::: tip
Everyone in the group who has PocketChat sees the same combined conversation.
:::

## Broadcasts (public channels)

**Broadcasts** are an analog of Telegram channels: one owner (plus any admins
they appoint) posts, everyone else is a read-only subscriber who can still see
the subscriber count and read the history.

Create one with the "＋ New channel" row under the group list. The owner gets
an ⓘ "Channel info" button in the header with an invite code to copy and share,
a subscriber list with admin promote/demote, and a delete-channel button.
Anyone who receives the code pastes it into "🔗 Join with a code" and their
request goes straight to the owner.

::: tip
The owner sees the exact subscriber count and per-post view counts;
subscribers see the last known count. Posts can be pinned (right-click →
"Pin", available to whoever can post) and the channel can be muted from the
bell icon in the header, just like Telegram.
:::
