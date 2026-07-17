# Голос и озвучка

| Ключ | По умолчанию | Описание |
|---|---|---|
| `sttLang` | `0` | Язык распознавания речи: 0 русский, 1 английский |
| `sttModelUrlRu` | зеркала | Ссылки на русскую модель Vosk (через запятую) |
| `sttModelUrlEn` | зеркала | Ссылки на английскую модель Vosk (через запятую) |
| `ttsGlobal` | `false` | Озвучивать сообщения глобального чата системным голосом |

## Модели Vosk

Модель для распознавания речи скачивается автоматически при первом использовании
в папку `config/pmchat-stt/`. В настройках `sttModelUrlRu` / `sttModelUrlEn`
указан список зеркал через запятую — они пробуются по порядку:

```json
"sttModelUrlRu": "https://github.com/yurosing/pocketchat/releases/download/models/vosk-model-small-ru-0.22.zip,https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
```

::: tip GitHub-зеркало первым
`alphacephei.com` часто недоступен, поэтому первым стоит зеркало на GitHub
Releases. Если первое зеркало не отвечает — мод пробует следующее.
:::

Подробнее про голосовые сообщения — в разделе [Голосовые](/guide/voice).
