# Структура папок

Всё, что создаёт мод, лежит в папке `config/` вашего экземпляра игры (там же, где
`mods/`).

```
.minecraft/
├─ mods/
│  ├─ fabric-api-*.jar
│  └─ pmchat-mod-1.3.0.jar        # сам мод
└─ config/
   ├─ pmchat.json                 # все настройки
   ├─ pmchat-history.json         # вся переписка (локально, навсегда)
   ├─ pmchat-media/               # скачанные фото и голосовые
   ├─ pmchat-stickers/            # ваши стикеры и GIF (кладёте сюда)
   ├─ pmchat-wallpapers/          # обои фона чата (кладёте сюда)
   └─ pmchat-stt/                 # офлайн-модели Vosk для распознавания речи
```

| Путь | Что внутри |
|---|---|
| `config/pmchat.json` | Все настройки мода — см. [Обзор pmchat.json](/config/) |
| `config/pmchat-history.json` | Полная история диалогов, каналов и групп. Бэкап делайте отсюда |
| `config/pmchat-media/` | Кэш скачанных изображений и голосовых |
| `config/pmchat-stickers/` | Ваши `.png` и `.gif` — станут стикерами |
| `config/pmchat-wallpapers/` | Картинки-обои; имя указывается в `wallpaper` |
| `config/pmchat-stt/` | Модели Vosk (качаются автоматически) |

::: tip Резервная копия
Чтобы сохранить всю переписку и настройки, скопируйте `pmchat.json` и
`pmchat-history.json`. Стикеры и обои — из соответствующих папок.
:::
