# Video and YouTube <Badge type="tip" text="NEW" />

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

::: tip If YouTube doesn't open
The player relies on VLC's own YouTube link parsing, which doesn't always
keep up with changes on YouTube's side. If no video shows up after a few
seconds, an "Open in browser" button appears instead — click that.
:::

## Player controls

| Control | What it does |
|---|---|
| ▶ / ❚❚ | Play and pause |
| Bottom bar | Click to seek to that point |
| Speed number | Click cycles through: 0.5x → 0.75x → 1x → 1.25x → 1.5x → 2x |
| Volume slider | Click at the spot you want |
| ✖ or <kbd>Esc</kbd> | Close the player |

Clicking directly on the video frame also toggles play/pause.
