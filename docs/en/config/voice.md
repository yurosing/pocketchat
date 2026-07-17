# Voice & TTS

| Key | Default | Description |
|---|---|---|
| `sttLang` | `0` | Speech recognition language: 0 Russian, 1 English |
| `sttModelUrlRu` | mirrors | Russian Vosk model URLs (comma-separated) |
| `sttModelUrlEn` | mirrors | English Vosk model URLs (comma-separated) |
| `ttsGlobal` | `false` | Read global chat aloud with the system voice |

## Vosk models

The speech-recognition model downloads automatically on first use into
`config/pmchat-stt/`. The `sttModelUrlRu` / `sttModelUrlEn` settings hold a
comma-separated list of mirrors, tried in order:

```json
"sttModelUrlRu": "https://github.com/yurosing/pocketchat/releases/download/models/vosk-model-small-ru-0.22.zip,https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
```

::: tip GitHub mirror first
`alphacephei.com` is often unreachable, so the GitHub Releases mirror is listed
first. If the first mirror fails, the mod tries the next one.
:::

More about voice notes in [Voice messages](/en/guide/voice).
