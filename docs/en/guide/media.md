# Photos & stickers

## Photo from clipboard

Copy an image to the clipboard and, with a chat open, press
<kbd>Ctrl</kbd>+<kbd>V</kbd> — the photo uploads to a file host and is sent to
your contact.

::: tip
Where files upload and how to switch hosts is in [File hosts](/en/config/hosts).
Check availability with `/pm hosts`.
:::

## Your own stickers & GIFs

Put your `.png` and `.gif` files into `config/pmchat-stickers/` — they show up in
the picker ready to send, fully animated.

<Shot src="/img/stickers.png" caption="Sticker picker — files from config/pmchat-stickers/." />

- Recently used stickers float to the top of the list.
- Uploaded stickers are cached (their host id is remembered) so the same file
  isn't re-uploaded.

## Chat wallpaper

Wallpaper images go into `config/pmchat-wallpapers/`, and the file name is set in
the [`wallpaper`](/en/config/appearance) option.
