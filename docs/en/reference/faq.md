# FAQ

## Incoming PMs don't show up in the mod

Your server's PM format doesn't match the default regex. Adjust
`incomingPattern` / `outgoingPattern` — see [Server regex](/en/config/patterns).

## Photos and voice don't upload

The file hosts may be unreachable from your connection. Check with `/pm hosts`
and reorder [`uploadOrder`](/en/config/hosts).

## Does my contact need the mod?

No. They'll see plain text. The mod on both sides is only needed for "mod
features": reactions, typing indicators and read receipts.

## Where is my chat history stored?

In `config/pmchat-history.json`. Only on your machine, locally. See
[Folder layout](/en/reference/folders).

## How do I rebind the open key?

**Options → Controls → Key Binds** in Minecraft, PocketChat category.
Default — <kbd>J</kbd>.

## The speech model won't download

The primary mirror may have been down. The mod tries the mirrors in
`sttModelUrlRu` / `sttModelUrlEn` in order. You can add your own mirror — see
[Voice & TTS](/en/config/voice).

## Settings didn't apply

Most settings take effect **on the next launch**. Restart the client.

## The mod isn't visible in-game

Make sure you launched the **Fabric** profile and that both `fabric-api` and
`pmchat-mod` are in `mods/`. See [Installation](/en/guide/install).
