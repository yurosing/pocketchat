# Appearance

<Shot src="/img/themes.png" caption="Theme, bubble colors and wallpaper settings." />

| Key | Default | Description |
|---|---|---|
| `theme` | `0` | 0 — dark, 1 — light |
| `uiScale` | `0` | Window size: 0 small, 1 medium, 2 large |
| `fullscreen` | `false` | Fullscreen mode (like Telegram Desktop) |
| `outColor` | `0` | Your bubble color (palette index) |
| `inColor` | `0` | Incoming bubble color (palette index) |
| `uniformNames` | `false` | One name color instead of the "rainbow" |
| `nameColor` | `0` | Index of the uniform name color |
| `msgTextColor` | `0` | Message text color (0 — auto) |
| `textScalePct` | `100` | Text size, % (60–150) |
| `badgeColor` | `0` | Unread badge color |
| `wallpaper` | `""` | Wallpaper file name from `config/pmchat-wallpapers/` |

## Wallpaper

Put an image into `config/pmchat-wallpapers/` and set its file name in `wallpaper`.
An empty value means no wallpaper.

## Colors

Colors are set as indices into the mod's internal palette. The easiest way to pick
them is in-game, on the [settings screen](/en/config/) — it shows a preview.
