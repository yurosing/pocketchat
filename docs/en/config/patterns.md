# Server regex

The mod detects PMs via regular expressions. By default they target the
**Essentials** format like "(PM) me → nick » text". If your server uses a
different format, adjust the patterns.

::: warning Important about groups
In the PM and global patterns: **group 1 — nick, group 2 — text**.
:::

| Key | What it captures |
|---|---|
| `incomingPattern` | Incoming PM (someone messaged you) |
| `outgoingPattern` | Outgoing PM (you messaged someone) |
| `globalPattern` | Public chat lines for the "Global" tab |

## Defaults

```json
"incomingPattern": "\\(ЛС\\)\\s*\\W*([A-Za-z0-9_]{2,16})\\s*->\\s*я\\s*»\\s*(.+)",
"outgoingPattern": "\\(ЛС\\)\\s*я\\s*->\\s*\\W*([A-Za-z0-9_]{2,16})\\s*»\\s*(.+)",
"globalPattern":   "([A-Za-z0-9_]{2,16})[^»A-Za-z0-9_]*»\\s*(.+)"
```

## How to tune for your server

1. Send yourself a test PM and look at the **exact** chat line text.
2. Replace the `(ЛС)` label, the `->` arrow and the `»` separator with what your
   server actually sends.
3. Keep the groups `([A-Za-z0-9_]{2,16})` for the nick and `(.+)` for the text.
4. Save the file and restart the game.

::: tip Escaping in JSON
In JSON a backslash is written twice: `\\s`, `\\(`, `\\W`. Capture groups `(...)`
are not doubled.
:::

## Channels

Channels (clan/alliance/group) are configured in the `channels` array and use
**named groups** instead of numbered ones:

- `(?<name>…)` — nick;
- `(?<text>…)` — message text;
- `(?<clan>…)` — optional clan tag, shown next to the nick.

Example of the default "Clan" channel:

```json
{
  "id": "clan",
  "label": "Клан",
  "command": ".",
  "pattern": "^\\W{0,6}(?:клан|clan)\\W+.*?(?<name>[A-Za-z0-9_]{2,16})\\s*[»:>]\\s*(?<text>.+)"
}
```

On using channels, see [Channels & groups](/en/guide/channels).
