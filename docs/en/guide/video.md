# Video and YouTube

Watch video right inside the messenger — no downloading a file, no opening a
browser. Works for your own videos and for YouTube links.

<Shot src="/img/video-player.png" caption="The built-in player: pause, seek, volume, speed." />

## What it needs

The built-in player runs through **VLC media player** installed on your
computer. Most people already have it; if not, grab it from
[videolan.org](https://www.videolan.org/vlc/). Without VLC the mod just falls
back to the old behavior — opening the video externally, nothing breaks.

## Your own videos

1. Put a file (`.mp4`, `.webm`, and similar) into `config/pmchat-video/`.
2. In a chat, press the ▶ (video/audio) button and pick the file — it
   uploads and sends to your contact.
3. Click the video in the chat — the player opens fullscreen.

## A YouTube link

Just paste a video link (`youtube.com/...` or `youtu.be/...`) as a regular
message and send it. Clicking the link in the chat opens it in the built-in
player, same as your own video.

::: tip How it works
You can't hand a YouTube link straight to VLC anymore — YouTube locks its
streams behind protection that only the dedicated tool **yt-dlp** can get
past. So on your first YouTube video PocketChat downloads `yt-dlp` into
`config/pmchat-bin/` (the player shows "Getting yt-dlp"), then downloads the
clip to a temp file ("Downloading N%") and plays it. The first time is a bit
slower — the tool itself (~18 MB) is being fetched; after that it's just clips.

Clips are grabbed at 360p (audio+video in one file, so no ffmpeg needed).
:::

::: warning If it says "Couldn't play"
Some videos make YouTube ask you to sign in ("confirm you're not a bot"). In
that case export your YouTube cookies to `config/pmchat-cookies.txt` (Netscape
format — e.g. a "Get cookies.txt" browser extension) and PocketChat picks them
up automatically. Without cookies those videos open via the "Open in browser"
button.
:::

## Player controls

| Control | What it does |
|---|---|
| ▶ / ❚❚ | Play and pause |
| Seek bar | Click or drag; hover shows a time tooltip |
| Volume slider | Click or drag |
| Speed number | Click cycles through: 0.5x → 0.75x → 1x → 1.25x → 1.5x → 2x |
| Arrow-out icon | Open the original link in your browser |
| ✖ at the top or <kbd>Esc</kbd> | Close the player |

Clicking directly on the video frame also toggles play/pause.
