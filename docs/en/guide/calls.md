# Voice calls

One button right in the chat, and you're talking to your contact directly —
no server plugins, no shared voice group that anyone else could join.

## What it needs

Both of you need a PocketChat backend account configured (the same one that
gives you the green verification checkmark and syncs history across
servers) — the voice channel itself runs through it. If the backend isn't
configured, the call button simply does nothing — no errors, no crashes.

## How to call someone

1. Open a chat with someone who definitely has PocketChat and a configured
   backend.
2. Click the phone-handset icon in the chat's top toolbar.
3. Your contact sees an incoming-call toast and can accept or decline it
   right from their PocketChat window.

## Privacy

Calls are anonymous: the server hands out a one-time call ID known only to
you and your contact, and simply relays audio between your two connections
— it never decrypts, stores, or logs it. There's no "group" a third player
could join — just a pair of connections for one specific call, which
disappears the moment either of you hangs up.

## How to end a call

The "Hang up" button is in the same call window you started it from.
