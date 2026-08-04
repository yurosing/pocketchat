# Profiles, roles & gifts

New in 1.8.4: every player has a profile — Telegram-style.

## Opening a profile

- **Right-click a message → “Player profile”** — the universal way: it works in
  DMs, the global chat, channels and groups, and opens the profile of **that
  message's author** (new in 1.10.1), **or**
- **Right-click** a chat in the left list, **or**
- the **“☺”** button in an open private chat's header, **or**
- the **“⋮” → “Profile”** menu.

::: tip Only players have profiles
A broadcast channel, a group, the global chat and the CoreProtect feed have no
profile — there is no profile button for them. Previously such a tab silently
opened **your own** profile.
:::

## What a profile shows

- **The full nick with prefix and suffix** — exactly how the player looks on the
  server (rank, clan, donor tag, etc.). Read from the player (tab) list.
- **Avatar** (skin) and **online / offline** status.
- **Birthday** and **description** — you can set these on your own profile
  (“My profile”).
- **Balance** — on your own profile only, next to the nick (requires the server
  plugin + Vault).

## The role is detected automatically

The position/role is **not set by hand** — it is detected from the nick
(prefix/suffix). If the nick contains one of these markers or words, a badge is
shown next to the name:

| Badge | Role |
|:---:|---|
| Ⓒ | Content maker |
| Ⓗ | Helper |
| Ⓜ | Moderator |
| Ⓔ | Event maker |
| Ⓓ | Developer |

Keywords are recognised too: `helper`, `moder(ator)`, `developer`, `event`,
`content` (and their Russian equivalents).

## Private notes

Like Discord: any player's profile has a **"Note"** field — a short reminder
for yourself (e.g. "sold me an enchanted pickaxe for 5 diamonds").

::: tip Only you can see the note
The note is never published or sent anywhere — it lives only on your own
computer, in `pmchat.json`. The other player never finds out about it, even if
they run PocketChat too.
:::

## Rename a player

Any player's profile has a **“Name”** field. Type a name there and the player
shows up under it in the chat list and the header (their real nick stays in the
profile title). Renaming also adds them to your **contacts**. It doesn't affect
contacts. Clear the field to remove the rename.

By default the rename is display-only — `/m` still goes to the real nick. If
your server addresses players by their display name, enable **“/m uses rename”**
in settings and `/m` will target the name you set.

## Blacklist

A player's profile has a **“Blacklist”** button:

- the blocked player's **avatar is hidden** (even while online);
- the chat header shows a **“⊘ blocked”** marker;
- without the server plugin the block is mirrored to Essentials `/ignore`.

Remove from the blacklist with the same button.

## Reporting a player

Next to "Blacklist" there's a **"Report"** button: a short reason goes to the
PocketChat backend and is only visible to the server admin (see "PocketChat
rules" on first launch — threats and harassment are forbidden). Needs a
backend account (the "Account" tab in settings).

## Support

In settings, "Account" tab, there's a **"Support"** button — a short message
goes to the same admin as reports.

## Gifts for coins (Vault)

The **“Gifts”** section works when the server runs the **PocketChat** plugin +
**Vault** and an economy (e.g. EssentialsX).

- Open **another** player's profile → the “Gifts” section → **click a gift**.
- The price is withdrawn from your Vault balance; the recipient is notified.
- Received gifts show up on the player's profile.
- The gift catalog is configured in the plugin's `config.yml` (`gifts:` section).

Without the plugin the section shows “requires the plugin”.

## Streams + donations (Vault)

The camera icon at the bottom of the messenger opens a list of players who are
currently live on an external service (Twitch/YouTube — the mod doesn't
stream video itself, it only shows the status and link):

- **“Start streaming”** — a title + link, visible to everyone with the
  plugin installed;
- **Donate** — next to a streamer's name, withdraws Vault coins from your
  balance and deposits them to the streamer.

The stream list and donations only work with the server **PocketChat** plugin
+ **Vault** and an economy — without the plugin the section shows “requires
the plugin”.

## Interface themes

New themes were added in settings (dark and light): **Midnight**, **Nord**,
**Rosé**, **Sand** — alongside the existing Dark, Light and Slate.

## Group avatars

A group chat can have a custom picture: drop an image into
`config/pmchat-avatars/` and click the avatar in the group header — it cycles
through the available files.
