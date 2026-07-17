# Voice messages

Hold the microphone button, speak (up to 20 seconds), release — the message is
sent. The bubble shows a waveform and a timer.

<Shot src="/img/voice.png" caption="Voice message with a waveform and recognized text." />

## Speech-to-text (Vosk)

Voice notes can be transcribed offline with the [Vosk](https://alphacephei.com/vosk/)
engine. On first use the mod downloads a language model into `config/pmchat-stt/`.

- The recognition language is set by [`sttLang`](/en/config/voice) (0 — Russian, 1 — English).
- Model URLs live in `sttModelUrlRu` / `sttModelUrlEn` (a comma-separated list of
  mirrors, tried in order; the GitHub mirror comes first).

::: tip The model downloads once
The model is tens of megabytes and stays on disk. If the primary mirror is down,
the mod tries the next one in the list.
:::

## Global chat TTS

The [`ttsGlobal`](/en/config/voice) setting reads out global chat messages with
the system voice.
