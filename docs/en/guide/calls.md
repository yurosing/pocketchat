# Voice calls <Badge type="tip" text="NEW" />

One button right in the chat sends your contact an invite to a voice
channel — no typing commands by hand.

## What it needs

Calls run through **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)**
— the same mod many people already use for regular voice chat on a server.
Important: it needs to be installed **on the server** you're playing on (not
just on your own computer) — the invite command is handled by the server.

If the server doesn't have Simple Voice Chat, the call button simply does
nothing — no errors, no crashes.

## How to call someone

1. Open a chat with someone who definitely has PocketChat.
2. Click the phone-handset icon in the chat's top toolbar.
3. Done — Simple Voice Chat invites your contact into your voice group
   (creating one if you're not already in one). From there it's the usual
   Simple Voice Chat flow: your contact sees the invite through that mod
   itself.

## How to leave the channel

There's no button for this in PocketChat — use the same way you always would
with Simple Voice Chat: the `/voicechat leave` command, or its own menu
(opens with <kbd>V</kbd> by default — the group tab).

::: tip Under the hood
PocketChat just runs the official `/voicechat invite <nickname>` command —
everything else is handled by Simple Voice Chat itself, no extra logic on
top.
:::
