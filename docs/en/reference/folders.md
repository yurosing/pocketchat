# Where the mod's files live

All of the mod's own files go into the game folder, next to `mods`, inside a
`config` subfolder.

## How to open that folder

1. Press <kbd>Win</kbd>+<kbd>R</kbd>, type `%appdata%\.minecraft\config` and
   press Enter.
2. Or in the Minecraft launcher: **Installations → Open game folder**, then go
   into `config`.

## What's where

```
.minecraft/
├─ mods/
│  └─ pmchat-mod-1.4.0.jar        # the mod itself
└─ config/
   ├─ pmchat.json                 # all settings
   ├─ pmchat-history.json         # your entire chat history
   ├─ pmchat-media/               # downloaded photos and voice notes
   ├─ pmchat-stickers/            # put your own stickers and GIFs here
   ├─ pmchat-wallpapers/          # put chat background images here
   └─ pmchat-stt/                 # files used for speech recognition
```

| Folder or file | What's in it |
|---|---|
| `pmchat.json` | All settings — see [Mod settings](/en/config/) |
| `pmchat-history.json` | Your whole chat history: threads, channels, groups |
| `pmchat-media/` | Already-downloaded photos and voice notes (cache) |
| `pmchat-stickers/` | Drop your `.png` and `.gif` files here — they become stickers |
| `pmchat-wallpapers/` | Drop pictures here — they become available as backgrounds |
| `pmchat-stt/` | Speech recognition files, downloaded automatically |

::: tip Back up your chat history
To avoid losing your chat history, copy the `pmchat-history.json` file
somewhere safe — for example, before reinstalling Windows.
:::

::: tip <Badge type="tip" text="NEW" /> Secret chats never end up here
[Secret chats](/en/guide/secret-chats) aren't saved anywhere — on purpose, so
they can't be read even by someone with access to this folder. They only
exist while the game is running.
:::
