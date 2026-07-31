# Where files get uploaded

Photos, stickers, GIFs and voice notes can't be sent directly to another
player — Minecraft isn't built for that. So the mod first uploads the file to
one of several free image-hosting sites and sends your contact only a link —
that's how most messengers work under the hood.

## Checking which sites are working

If a photo won't send, type `/pm hosts` in chat. The mod checks every site in
turn and shows which ones currently respond from your internet connection.

::: tip
Some upload sites may be blocked in your country — that's not a mod bug, that
particular site is just unreachable. The mod automatically tries the next one.
:::

## The order sites are tried in

Settings let you change which site is tried first (this can speed things up if
one of them works better for you than the others). The default order is:

1. kappa.lol
2. x0.at
3. qu.ax
4. catbox.moe *(blocked in Russia — listed last)*

If, say, only one site reliably works for you, put it first in the list in
settings.

## Your own backend (Railway) instead of anonymous sites

For full control over file storage — and to get your own currency and gifts
without a server plugin (see [Gifts and currency](/en/guide/profiles)) —
you can deploy your own backend:

1. Fork or deploy the [`pocketchat-backend`](https://github.com/yurosing/server-pocketchat)
   repository to [Railway](https://railway.app) — follow the README in that
   repo (Postgres + a Volume for files).
2. Put the Railway URL it gives you into `pmchat.json`:
   ```json
   "backendUrl": "https://my-project.up.railway.app"
   ```
3. Rejoin the server — photos/videos/voice notes/stickers now upload there
   first (before the external sites above), and your profile gets a wallet
   and gift shop section.

Your own backend works whether or not the PocketChat server plugin is
installed — it's the way to get gifts and currency with no plugin at all.
