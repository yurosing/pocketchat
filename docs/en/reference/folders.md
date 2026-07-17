# Folder layout

Everything the mod creates lives in the `config/` folder of your game instance
(the same place as `mods/`).

```
.minecraft/
├─ mods/
│  ├─ fabric-api-*.jar
│  └─ pmchat-mod-1.3.0.jar        # the mod itself
└─ config/
   ├─ pmchat.json                 # all settings
   ├─ pmchat-history.json         # all chats (local, forever)
   ├─ pmchat-media/               # downloaded photos and voice notes
   ├─ pmchat-stickers/            # your stickers and GIFs (put here)
   ├─ pmchat-wallpapers/          # chat wallpapers (put here)
   └─ pmchat-stt/                 # offline Vosk models for speech-to-text
```

| Path | What's inside |
|---|---|
| `config/pmchat.json` | All mod settings — see [pmchat.json overview](/en/config/) |
| `config/pmchat-history.json` | Full history of chats, channels and groups. Back up from here |
| `config/pmchat-media/` | Cache of downloaded images and voice notes |
| `config/pmchat-stickers/` | Your `.png` and `.gif` files become stickers |
| `config/pmchat-wallpapers/` | Wallpaper images; the name goes into `wallpaper` |
| `config/pmchat-stt/` | Vosk models (downloaded automatically) |

::: tip Backup
To keep all your chats and settings, copy `pmchat.json` and
`pmchat-history.json`. Stickers and wallpapers — from their folders.
:::
