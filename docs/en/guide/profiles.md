# Profiles, roles & gifts

New in 1.8.2: every player has a profile — Telegram-style.

## Opening a profile

- **Right-click** a chat in the left list, **or**
- the **“☺”** button in an open private chat's header, **or**
- the **“⋮” → “Profile”** menu.

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

## Blacklist

A player's profile has a **“Blacklist”** button:

- the blocked player's **avatar is hidden** (even while online);
- the chat header shows a **“⊘ blocked”** marker;
- without the server plugin the block is mirrored to Essentials `/ignore`.

Remove from the blacklist with the same button.

## Gifts for coins (Vault)

The **“Gifts”** section works when the server runs the **PocketChat** plugin +
**Vault** and an economy (e.g. EssentialsX).

- Open **another** player's profile → the “Gifts” section → **click a gift**.
- The price is withdrawn from your Vault balance; the recipient is notified.
- Received gifts show up on the player's profile.
- The gift catalog is configured in the plugin's `config.yml` (`gifts:` section).

Without the plugin the section shows “requires the plugin”.

## Interface themes

New themes were added in settings (dark and light): **Midnight**, **Nord**,
**Rosé**, **Sand** — alongside the existing Dark, Light and Slate.

## Group avatars

A group chat can have a custom picture: drop an image into
`config/pmchat-avatars/` and click the avatar in the group header — it cycles
through the available files.
