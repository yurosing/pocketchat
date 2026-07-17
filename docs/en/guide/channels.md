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
