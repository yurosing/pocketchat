# FAQ

## Private messages don't show up in the mod

Your server's private message format probably differs from what the mod
expects by default. Fixed in settings — see
[Setting it up for your server](/en/config/patterns).

## Photos and voice notes won't send

Try `/pm hosts` — it shows which upload sites are currently reachable from your
connection. More in [Where files get uploaded](/en/config/hosts).

## Does my contact need the mod too?

No. They'll see a completely normal text message, as always. The mod is only
needed on both sides for extras: reactions, typing status and read receipts.

## Where is my chat history stored?

On your own computer, in a file called `pmchat-history.json`. Nowhere else —
nothing gets sent anywhere. See [Where the mod's files live](/en/reference/folders).

## How do I change the key that opens the messenger?

**Options → Controls → Key Binds** in Minecraft, PocketChat section. Default is
<kbd>J</kbd>.

## The first voice note takes forever or shows an error

The first time you use speech recognition, the mod downloads a file it needs —
can take a minute. If the main download source fails, it automatically tries
another one — just wait a bit.

## I changed a setting and nothing happened

Most settings apply **after restarting the game** — just quit and reopen it.

## A secret chat disappeared after restarting the game <Badge type="tip" text="NEW" />

That's by design. Secret chats aren't saved anywhere — you'll need to start a
new one the next time you launch the game. See
[Secret chats](/en/guide/secret-chats) for details.

## Video won't open in the built-in player <Badge type="tip" text="NEW" />

This needs **VLC media player** installed on your computer. If you don't
have it, grab it from [videolan.org](https://www.videolan.org/vlc/), or the
mod falls back to opening the video the old way, externally. For YouTube
links specifically, VLC sometimes can't parse a given link — after 8 seconds
an "Open in browser" button appears instead. More in
[Video and YouTube](/en/guide/video).

## The "Call" button doesn't do anything <Badge type="tip" text="NEW" />

Calls run through the **Simple Voice Chat** mod, which needs to be installed
**on the server** (not just on your computer). If the server doesn't have
it, the call command just fails silently — no errors, no crashes. More in
[Voice calls](/en/guide/calls).

## The mod isn't in the mod list / the game doesn't see it

Check that:
- the **Fabric** profile is selected, not the regular one;
- both `fabric-api` and `pmchat-mod` are in the `mods` folder;
- the Minecraft versions match.

Full walkthrough on the [Installation](/en/guide/install#common-problems) page.
