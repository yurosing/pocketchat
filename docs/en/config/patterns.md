# Setting it up for your server

Every server formats private messages a bit differently. The mod is set up by
default for the most common case (the Essentials plugin), but if your server's
format is different, messages might not get captured. That's fixed in settings.

::: tip Check first if you even need this
If [Quick start](/en/guide/quickstart) worked fine and messages get captured —
you can skip this page, everything already works.
:::

## How to tell this is the problem

If you send or receive a private message and it doesn't show up in the
PocketChat window — that's almost always the reason. The regular chat shows the
message fine, the mod just doesn't "recognize" it.

## How to fix it

1. Send yourself a test private message and look at **exactly** how it appears
   in the regular Minecraft chat.
2. Open the mod's settings and find the fields about the PM format — enter that
   format there.
3. Save and restart the game.

If your server's format is very different and the in-game settings don't cut
it, here's a more detailed explanation for anyone comfortable poking around in
the settings file.

## In detail: how the format works (advanced)

The mod finds messages using a text pattern (the technical term is "regular
expression") — basically an instruction like "find a line where a nickname
comes first, then some separator, then the message text".

In the `pmchat.json` settings file, three entries handle this:

| Entry | What it's for |
|---|---|
| `incomingPattern` | What a message someone sent you looks like |
| `outgoingPattern` | What a message you sent looks like |
| `globalPattern` | What a line in the server's global chat looks like |

Default values:

```json
"incomingPattern": "\\(ЛС\\)\\s*\\W*([A-Za-z0-9_]{2,16})\\s*->\\s*я\\s*»\\s*(.+)",
"outgoingPattern": "\\(ЛС\\)\\s*я\\s*->\\s*\\W*([A-Za-z0-9_]{2,16})\\s*»\\s*(.+)",
"globalPattern":   "([A-Za-z0-9_]{2,16})[^»A-Za-z0-9_]*»\\s*(.+)"
```

What matters: the part in parentheses `([A-Za-z0-9_]{2,16})` is the nickname,
and `(.+)` is the message text. Leave those alone — only change what's around
them (the "(ЛС)" label, the arrow, the "»" separator) to match what your server
actually sends.

::: warning Backslashes are doubled in JSON
If editing the file by hand: `\s`, `\(`, `\W` need to be written as `\\s`,
`\\(`, `\\W`. Parentheses with letters inside `(...)` stay as they are.
:::

## Extra chats (clan, alliance)

For how separate tabs for clan or alliance chat are set up, see
[Channels & groups](/en/guide/channels).
