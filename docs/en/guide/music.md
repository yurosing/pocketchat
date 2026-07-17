# Music and playlists <Badge type="tip" text="NEW" />

The built-in player handles more than YouTube video — it plays your own music
as playlists of local mp3s. It runs in a small corner window and **keeps
playing when the messenger is closed**.

## Adding music

1. Drop audio files into `config/pmchat-music/`.
   - Subfolders work — each subfolder is its own playlist.
   - Formats: `mp3`, `wav`, `flac`, `ogg`, `m4a`, `aac`.
2. In-game press <kbd>K</kbd> to open the "Media & playlists" menu.
3. Click a folder to start it as a playlist. Clicking a single track plays
   from there (and continues through the neighboring files).

The "Open folder" button opens `config/pmchat-music` in your file manager so
you can drop files in easily.

## The corner window

While something is playing, a compact window sits in the bottom-right corner:

| Control | What it does |
|---|---|
| ◀ / ❚❚ / ▶ | Previous track · pause · next track |
| Top bar | Progress of the current track |
| Arrow frame (video) | Expand to fullscreen |
| ✖ | Stop and dismiss the window |

The window and its controls show up in the chat, in the media menu, and **in
the world over the HUD** — the music keeps playing even with everything closed.

## Keyboard control

To control playback without opening the menu (e.g. right in the world):

| Key | Action |
|---|---|
| <kbd>K</kbd> | Open the "Media & playlists" menu |
| *(unbound)* | Play/pause |
| *(unbound)* | Next track |

Play/pause and next are unbound by default — set them in **Options →
Controls** (PocketChat section) if you want to skip tracks without opening the
menu.

::: tip Video minimizes too
The "minimize" button (the bar) in the YouTube player tucks the video into
this same window, so you can scroll the chat or run around while it plays in
the corner.
:::
