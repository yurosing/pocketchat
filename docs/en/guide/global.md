# Global chat

A pinned tab shows the server's public chat as the same clean bubbles: colored
names, clickable links and a reply box — you never have to leave PocketChat to
post in the public chat.

<Shot src="/img/global.png" caption="The server's global chat inside the messenger." />

## Send prefix

Sending to global uses the prefix from the [`globalPrefix`](/en/config/) setting.
On many servers this is `!`. If your global chat is the default (no prefix),
leave the field empty.

## How lines are captured

Public messages are detected by the [`globalPattern`](/en/config/patterns) regex.
If names or text in global are parsed incorrectly, adjust that pattern.

## TTS

Global chat messages can be read aloud with the system voice — the
[`ttsGlobal`](/en/config/voice) setting.
