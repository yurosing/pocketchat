# Quick start

1. Join a server and press <kbd>J</kbd> — the PocketChat window opens.
2. On first launch the mod shows its rules (full Mojang EULA compliance, a
   free-speech messenger, threats, harassment and deception are forbidden) —
   click "I accept", otherwise the messenger window won't open. The exact
   wording is set by the server admin (editable from the admin panel, no mod
   release needed), so it may differ from this description.
3. Send someone a private message with the usual command (`/m Nick hi`) or
   right from the open window.
4. Check that both the sent and received message show up as bubbles in the
   window.

::: tip If messages don't show up
Your server's private messages probably look different from what the mod
expects by default. Takes a couple of minutes to fix — see
[Setting it up for your server](/en/config/patterns).
:::

## Step-by-step check

| Step | How to tell it worked |
|---|---|
| Mod loaded | PocketChat shows up in the mod list (ModMenu) |
| Window opens | <kbd>J</kbd> opens and closes the window |
| Messages captured | An incoming message shows up as a separate thread |
| Photos work | <kbd>Ctrl</kbd>+<kbd>V</kbd> in a chat sends an image |
| Upload sites reachable | `/pm hosts` shows at least one working site |

## What to set up first

- [Setting it up for your server](/en/config/patterns) — if messages aren't captured.
- [Where files get uploaded](/en/config/hosts) — if photos and voice notes fail to send.
- [Appearance](/en/config/appearance) — theme, colors, window size.
