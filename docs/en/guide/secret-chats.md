# Secret chats <Badge type="tip" text="NEW" />

A secret chat is a conversation nobody can read except you and the other
person. Even if someone sees the raw text the mod sends to the server (say, an
admin with log access), it's just a string of random characters — like a
Telegram secret chat.

<Shot src="/img/secret-chat.png" caption="An active secret chat: a lock icon in the header and on message bubbles." />

## How it's different from a normal chat

| | Normal chat | Secret chat |
|---|---|---|
| Who can read it | The server sees the plain text | Only you and the other person |
| Saved to disk | Yes, in your chat history | No, only while the game is open |
| Survives a restart | Yes | No — you'll need to start it again next time |
| Self-destructing messages | No | Optional timer |
| Reply, forward, pin, react | Available | Turned off — those would leak extra data over the unencrypted network |

## How to start one

1. Open a chat with someone who definitely has PocketChat (a green dot should
   be showing next to their name).
2. Click the stats icon (the bar-chart gear) and press **"Start secret chat"**.
3. Wait a couple of seconds — the other person doesn't need to press anything,
   their mod confirms it automatically. Once a lock icon appears in the chat
   header, it's active.

::: tip Why there's no manual accept step
To avoid adding yet another screen on top of an already small messenger
window, the chat confirms itself automatically as soon as the other side
definitely has the mod. You can cancel any time with "End secret chat".
:::

## While it's active

- Every message gets encrypted before sending — a small lock icon shows up on
  the bubble.
- Messages are shorter than usual (around 40 characters) — a technical limit
  of the encryption, explained below.
- You can't reply with a quote, forward, pin, or react to a secret message —
  only copy the text or delete it on your side.

## Self-destructing messages

In the same menu as the start button, once the secret chat is active, a timer
button appears — clicking it cycles through: off → 10 seconds → 30 seconds →
1 minute → 1 hour. The chosen value applies to every new message you send
until you change it. Once a message is delivered, a countdown starts — you'll
see the number right next to the message time, and once it hits zero the
message disappears from your history.

## How to end it

Press the same button again — now labeled "End secret chat". The other person
finds out immediately. The chat also ends automatically if either of you
quits the game — you'll need to start it again next time you both play.

## Why messages are shorter than usual

Encryption adds some overhead to every message (the encryption data and a
verification code), and all of that has to travel through the same `/m`
command as regular text — which has a length limit. That's why secret
messages are capped at around 40 characters. Not great for a long
conversation, but regular chats have no such limit.

## Where secret chats are stored

Nowhere. They only exist in memory while the game is running — the moment you
quit, everything is gone without a trace, and not even you can get it back.
That's on purpose: if it's a secret chat, it has no business being on disk.
