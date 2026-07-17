![PocketChat](docs/banner.png)

<div align="center">
  <h1>💬 PocketChat</h1>
  <p><i>Forget about clunky chat-based messaging.</i></p>
</div>

<div align="center">

<a href="https://fabricmc.net/" target="_blank">
  <img src="https://wsrv.nl/?url=https%3A%2F%2Fflectone.net%2Fpulse%2Ffabric.svg&n=-1" alt="Fabric">
</a>
<a href="https://modrinth.com/mod/fabric-api" target="_blank">
  <img src="https://img.shields.io/badge/Fabric_API-required-DEDC00?logo=fabric&logoColor=white" alt="Fabric API">
</a>
<a href="#" target="_blank">
  <img src="https://img.shields.io/badge/Minecraft-1.21.11-6FBF8B" alt="Minecraft 1.21.11">
</a>
<a href="#" target="_blank">
  <img src="https://img.shields.io/badge/Side-Client_Only-2E5F46" alt="Client Only">
</a>

</div>

PocketChat turns your server's plain `/m` command into a real conversation experience — bubbles, voice notes, reactions, stickers and everything else a modern chat deserves. It runs entirely on your client: **no server plugin, no permissions setup, no admin required.** Drop the jar in `mods/` and start talking.

Every private message you send or receive is captured, organized into threads and saved to your disk — searchable, permanent, and yours alone.

---

## ⚠️ Requirements

<div align="center">
  <p><b>• Minecraft 1.21.11</b></p>
  <p><b>• <a href="https://fabricmc.net/use/" target="_blank">Fabric Loader</a> 0.15+</b></p>
  <p><b>• <a href="https://modrinth.com/mod/fabric-api" target="_blank">Fabric API</a></b></p>
</div>

No server-side installation of any kind. PocketChat reads and writes ordinary `/m` messages your server already supports — everyone sees plain text, players with PocketChat installed see the full experience.

---

## 💬 Threaded conversations

Every player you've exchanged messages with gets their own thread, complete with unread badges and a live preview of the last message. No more scrolling through a wall of chat to find what someone said an hour ago.

![Replace this with a screenshot of the conversation list](docs/screenshot-list.png)

## 🎙️ Voice messages

Hold to record, release to send — up to 20 seconds, played back with a waveform right inside the bubble. Perfect for when typing just takes too long.

![Replace this with a screenshot of a voice message bubble](docs/screenshot-voice.png)

## ↩️ Quote replies & reactions

Right-click any message for a Telegram-style menu: reply with a quote, drop a reaction, copy the text, or delete it. Reactions and quotes sync live with anyone else running PocketChat.

![Replace this with a screenshot of the context menu](docs/screenshot-context-menu.png)

## 🎞️ Stickers & animated GIFs

Drop your own PNGs or GIFs into `config/pmchat-stickers/` and they show up ready to send — fully animated, right in the conversation.

![Replace this with a screenshot of the sticker picker](docs/screenshot-stickers.png)

## 🔍 Search & full history

Every conversation is saved locally forever. Search across every message you've ever sent or received, instantly.

![Replace this with a screenshot of search results](docs/screenshot-search.png)

## 🌍 Global chat tab

A pinned tab shows your server's public chat as the same clean bubble feed — colored names, clickable links, and all — with a reply box so you never have to leave PocketChat to talk.

## 🎨 Themes & message colors

Switch between dark and light themes, and pick your own bubble color from six accents. Resize the window to fit how you like to chat.

![Replace this with a screenshot of the theme picker](docs/screenshot-themes.png)

## 🟢 See who else has PocketChat

A small dot next to every name tells you at a glance whether that player has PocketChat installed — so you know whether reactions, read receipts, and typing indicators will actually reach them.

## 🔒 NEW: Secret chats

Start an end-to-end encrypted chat with anyone running PocketChat — messages are locked with a key that only exists on your two computers, are never written to disk, and can be set to self-destruct a few seconds after they arrive. Even the server only ever sees scrambled text.

## ▶️ NEW: Built-in video player, with YouTube links

Watch your own video files or paste a YouTube link and it opens in a player right inside the messenger — pause, seek, volume, and a speed control, no browser or external player needed. Requires VLC installed on your computer.

## 📞 NEW: Voice calls

One button starts a call: PocketChat creates a private voice channel through Simple Voice Chat and drops both of you into it — no manual group setup. Requires Simple Voice Chat installed on the server.

## 🙈 NEW: Spoiler photos & videos

Send a photo or video blurred out, Telegram/Discord-style — your contact reveals it themselves with a click, whenever they're ready.

---

## 🧭 Commands

<table border="1" cellspacing="0" cellpadding="8">
  <tr>
    <th>Command</th>
    <th>Description</th>
  </tr>
  <tr>
    <td><code>/pm</code></td>
    <td>Opens the PocketChat window (same as pressing <b>J</b>)</td>
  </tr>
  <tr>
    <td><code>/pm hosts</code></td>
    <td>Tests every file host used for photos/voice messages and reports which ones are reachable from your connection</td>
  </tr>
</table>

**Keybind:** press <b>J</b> anytime to open or close PocketChat. Press <b>Ctrl+V</b> while a chat is open to send an image straight from your clipboard.

---

## ⚙️ Config settings

All settings live in <code>config/pmchat.json</code> and take effect on the next launch.

<table border="1" cellspacing="0" cellpadding="8">
  <tr>
    <th>Setting</th>
    <th>Description</th>
  </tr>
  <tr>
    <td><code>incomingPattern</code> / <code>outgoingPattern</code></td>
    <td>Regex used to detect private messages in your server's chat format</td>
  </tr>
  <tr>
    <td><code>globalPattern</code></td>
    <td>Regex used to detect public chat lines for the Global Chat tab</td>
  </tr>
  <tr>
    <td><code>msgCommand</code></td>
    <td>The command PocketChat uses to send private messages (default <code>m</code>)</td>
  </tr>
  <tr>
    <td><code>payCommand</code></td>
    <td>The command PocketChat uses for in-chat money transfers (default <code>pay</code>)</td>
  </tr>
  <tr>
    <td><code>uploadOrder</code></td>
    <td>Priority order of file hosts used for photos, stickers, GIFs and voice messages</td>
  </tr>
  <tr>
    <td><code>hideChatLines</code></td>
    <td>Hides raw private-message lines from the vanilla chat once PocketChat has captured them</td>
  </tr>
  <tr>
    <td><code>theme</code></td>
    <td>0 for dark, 1 for light</td>
  </tr>
  <tr>
    <td><code>outColor</code></td>
    <td>Accent color index for your own message bubbles</td>
  </tr>
  <tr>
    <td><code>uiScale</code></td>
    <td>Window size preset (0 small, 1 medium, 2 large)</td>
  </tr>
  <tr>
    <td><code>soundEnabled</code></td>
    <td>Plays a notification sound on incoming messages</td>
  </tr>
  <tr>
    <td><code>enableMeta</code></td>
    <td>Enables typing indicators and read receipts between PocketChat users</td>
  </tr>
</table>

---

<div align="center">
  <p>Client-side only · No server plugin required · Your messages, saved locally, always</p>
</div>
