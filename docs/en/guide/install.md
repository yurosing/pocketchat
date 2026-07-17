# Installation

PocketChat installs only on your own computer — nothing to set up on the server.
Just 4 steps if Minecraft already has Fabric. If not, here's the full walkthrough.

## What to download

| What | Why | Where to get it |
|---|---|---|
| Fabric Loader | Lets Minecraft load mods | [fabricmc.net/use](https://fabricmc.net/use/) |
| Fabric API | A mod almost every other mod depends on, including PocketChat | [modrinth.com/mod/fabric-api](https://modrinth.com/mod/fabric-api) |
| PocketChat | The messenger itself | from whoever sent it to you / the mod's page |

Minecraft version — **1.21.11**.

## If Minecraft doesn't have Fabric yet (from scratch)

1. Open [fabricmc.net/use](https://fabricmc.net/use/) and download the
   **installer** (Fabric Installer).
2. Run the downloaded file. In the window that opens:
   - tab **Client**;
   - Minecraft version — **1.21.11**;
   - click **Install**.
3. The installer creates a new profile in the Minecraft launcher called
   "fabric-loader-...". Nothing else to do there, you can close it.
4. Open the regular **Minecraft launcher**, pick the **Fabric** profile from the
   dropdown at the top, and click **Play** once — this makes the launcher set up
   the game folder for that profile. Once the game opens, you can close it and
   move on to installing mods.

## Where to put mod files

1. Open the game folder:
   - **Windows:** press <kbd>Win</kbd>+<kbd>R</kbd>, type `%appdata%\.minecraft`, press Enter.
   - Or in the Minecraft launcher: **Installations → Open game folder**.
2. Inside, find (or create, if missing) a folder called **`mods`**.
3. Drag the downloaded files into it:
   - `fabric-api-*.jar`
   - `pmchat-mod-1.4.0.jar`

<Shot src="/img/mods-folder.png" caption="The mods/ folder with fabric-api and pmchat." />

::: tip Double-clicking the file does nothing — that's normal
Mod files (`.jar`) don't open on their own like a program. There's no
"installer" step for them — just drop the file into the `mods` folder, done.
:::

## Launching

Open the Minecraft launcher, pick the **Fabric** profile at the top and click
**Play**. The mod loads on its own — nothing to configure on the server.

::: warning Only for you
The mod works only on your side. Your contacts don't have to install it — but if
they do, you'll both get reactions, typing status and read receipts.
:::

## Common problems

### The mod isn't showing up / nothing changed

- Make sure the launcher has the **Fabric** profile selected, not the regular
  "Release" or "Latest release" one.
- Make sure both `fabric-api` and `pmchat-mod` are **directly** in the `mods`
  folder, not inside a subfolder or a `.zip` archive.
- Check that the Fabric profile's Minecraft version is **1.21.11**, matching the
  mod's version.

### The game crashes on startup

- Almost always caused by a missing **Fabric API**. PocketChat won't work
  without it.
- Double-check the Fabric profile's Minecraft version matches the mod's version.

### Can't find the `mods` folder

It isn't created until you launch the game with the Fabric profile at least
once. Launch it once first (you can close it right after it loads), then create
the `mods` folder yourself if it's still missing.

### The file doesn't look right / no `.jar` extension shown

If Windows hides file extensions, the downloaded file may appear without
`.jar` at the end — that's fine, it just needs to be dropped into `mods` as-is,
without renaming.

## Next

Open [Quick start](/en/guide/quickstart) — a couple of steps to confirm it all
works.
