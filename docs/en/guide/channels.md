# Channels & groups

## Channels (clan / alliance / group)

**Channels** are server chats. A tab appears automatically as soon as the mod
captures the first channel message via its regex. Three are defined by default:

| id | Tab | Send command |
|---|---|---|
| `clan` | Clan | `.` |
| `ally` | Alliance | `ally` |
| `gc` | Group | `gc` |

<Shot src="/img/groups.png" caption="Group chat and channel tabs." />

Channels are configured in the `channels` array of `pmchat.json`. Each channel has:

- `id` — internal identifier;
- `label` — tab name;
- `command` — the server send command;
- `pattern` — a regex with **named groups**:
  `(?<name>…)` — nick, `(?<text>…)` — text, optionally `(?<clan>…)` — clan tag.

More on named groups in [Server regex](/en/config/patterns#channels).

## Groups

**Groups** are local conversations with several players. The mod sends ordinary
`/m` to each member and collects incoming messages into one feed. The group id is
deterministic (derived from the member set) so it matches across all members who
have the mod.

::: tip
Groups are a purely client-side abstraction: the server still sees ordinary
private messages to each member individually.
:::
