import { defineConfig } from 'vitepress'

// ---- Боковое меню (RU) ----
const sidebarRu = [
  {
    text: 'Начало',
    collapsed: false,
    items: [
      { text: 'Что это', link: '/guide/what-is' },
      { text: 'Установка', link: '/guide/install' },
      { text: 'Быстрый старт', link: '/guide/quickstart' },
    ],
  },
  {
    text: 'Использование',
    collapsed: false,
    items: [
      { text: 'Интерфейс', link: '/guide/interface' },
      { text: 'Профиль, роли и подарки', link: '/guide/profiles' },
      { text: 'Голосовые сообщения', link: '/guide/voice' },
      { text: 'Фото и стикеры', link: '/guide/media' },
      { text: 'Видео и YouTube', link: '/guide/video' },
      { text: 'Музыка и плейлисты', link: '/guide/music' },
      { text: 'Глобальный чат', link: '/guide/global' },
      { text: 'Каналы и группы', link: '/guide/channels' },
      { text: 'Фильтры чата', link: '/guide/filters' },
      { text: 'Звонки', link: '/guide/calls' },
      { text: 'Команды и клавиши', link: '/guide/commands' },
    ],
  },
  {
    text: 'Конфигурация',
    collapsed: false,
    items: [
      { text: 'Обзор pmchat.json', link: '/config/' },
      { text: 'Внешний вид', link: '/config/appearance' },
      { text: 'Голос и озвучка', link: '/config/voice' },
      { text: 'Регэкспы под сервер', link: '/config/patterns' },
      { text: 'Хостинги файлов', link: '/config/hosts' },
      { text: 'Staff и CoreProtect', link: '/config/staff' },
    ],
  },
  {
    text: 'Разработчикам',
    collapsed: false,
    items: [
      { text: 'Обзор API', link: '/api/' },
      { text: 'API плагина', link: '/api/plugin' },
      { text: 'API мода', link: '/api/mod' },
      { text: 'Протокол pmchat:media', link: '/api/protocol' },
      { text: 'Примеры', link: '/api/examples' },
    ],
  },
  {
    text: 'Справка',
    collapsed: false,
    items: [
      { text: 'Структура папок', link: '/reference/folders' },
      { text: 'FAQ', link: '/reference/faq' },
    ],
  },
]

// ---- Sidebar (EN) ----
const sidebarEn = [
  {
    text: 'Getting started',
    collapsed: false,
    items: [
      { text: 'What is it', link: '/en/guide/what-is' },
      { text: 'Installation', link: '/en/guide/install' },
      { text: 'Quick start', link: '/en/guide/quickstart' },
    ],
  },
  {
    text: 'Usage',
    collapsed: false,
    items: [
      { text: 'Interface', link: '/en/guide/interface' },
      { text: 'Profiles, roles & gifts', link: '/en/guide/profiles' },
      { text: 'Voice messages', link: '/en/guide/voice' },
      { text: 'Photos & stickers', link: '/en/guide/media' },
      { text: 'Video & YouTube', link: '/en/guide/video' },
      { text: 'Music & playlists', link: '/en/guide/music' },
      { text: 'Global chat', link: '/en/guide/global' },
      { text: 'Channels & groups', link: '/en/guide/channels' },
      { text: 'Chat filters', link: '/en/guide/filters' },
      { text: 'Calls', link: '/en/guide/calls' },
      { text: 'Commands & keys', link: '/en/guide/commands' },
    ],
  },
  {
    text: 'Configuration',
    collapsed: false,
    items: [
      { text: 'pmchat.json overview', link: '/en/config/' },
      { text: 'Appearance', link: '/en/config/appearance' },
      { text: 'Voice & TTS', link: '/en/config/voice' },
      { text: 'Server regex', link: '/en/config/patterns' },
      { text: 'File hosts', link: '/en/config/hosts' },
      { text: 'Staff & CoreProtect', link: '/en/config/staff' },
    ],
  },
  {
    text: 'For developers',
    collapsed: false,
    items: [
      { text: 'API overview', link: '/en/api/' },
      { text: 'Plugin API', link: '/en/api/plugin' },
      { text: 'Mod API', link: '/en/api/mod' },
      { text: 'pmchat:media protocol', link: '/en/api/protocol' },
      { text: 'Examples', link: '/en/api/examples' },
    ],
  },
  {
    text: 'Reference',
    collapsed: false,
    items: [
      { text: 'Folder layout', link: '/en/reference/folders' },
      { text: 'FAQ', link: '/en/reference/faq' },
    ],
  },
]

/**
 * Заголовок файла у блока кода: ```java [MyPlugin.java]
 *
 * VitePress рисует такое имя только внутри ::: code-group (как подпись вкладки), а у
 * одиночного блока молча выбрасывает — имя файла просто пропадает. Дорисовываем плашку
 * сами.
 *
 * Имя приходится снимать на этапе разбора: к моменту рендера `token.info` уже очищен
 * от `[...]`, поэтому в core-правиле кладём его в `token.meta`, а в рендерере только
 * читаем. Блоки внутри code-group пропускаем — там подпись уже есть.
 */
function codeBlockTitles(md) {
  md.core.ruler.push('pmchat_code_title', (state) => {
    let insideCodeGroup = 0
    for (const token of state.tokens) {
      if (token.type === 'container_code-group_open') insideCodeGroup++
      else if (token.type === 'container_code-group_close') insideCodeGroup--
      else if (token.type === 'fence' && insideCodeGroup === 0) {
        const match = /\[([^\]]+)\]/.exec(token.info)
        if (match) token.meta = { ...token.meta, pmchatTitle: match[1] }
      }
    }
    return true
  })

  const fence = md.renderer.rules.fence
  md.renderer.rules.fence = (tokens, idx, options, env, self) => {
    const html = fence(tokens, idx, options, env, self)
    const title = tokens[idx].meta?.pmchatTitle
    if (!title) return html
    // Плашка должна оказаться ВНУТРИ .language-*, иначе скругления разъедутся.
    const insertAt = html.indexOf('>') + 1
    return html.slice(0, insertAt)
      + `<div class="vp-code-title">${md.utils.escapeHtml(title)}</div>`
      + html.slice(insertAt)
  }
}

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: 'PocketChat',
  description: 'Клиентский Minecraft-мессенджер поверх /m — история, голосовые, стикеры, поиск.',

  // Репозиторий называется pocketchat → сайт будет на
  // https://yurosing.github.io/pocketchat/
  base: '/pocketchat/',

  cleanUrls: true,
  lastUpdated: true,

  markdown: {
    theme: { light: 'github-light', dark: 'github-dark' },
    config: codeBlockTitles,
  },

  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/pocketchat/img/logo.png' }],
    ['meta', { name: 'theme-color', content: '#6fbf8b' }],
    ['meta', { property: 'og:title', content: 'PocketChat — документация' }],
    ['meta', { property: 'og:description', content: 'Клиентский Minecraft-мессенджер поверх /m.' }],
  ],

  themeConfig: {
    logo: '/img/logo.png',
    siteTitle: 'PocketChat',
    socialLinks: [
      { icon: 'github', link: 'https://github.com/yurosing/pocketchat' },
    ],
    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: { buttonText: 'Поиск', buttonAriaLabel: 'Поиск' },
              modal: {
                noResultsText: 'Ничего не найдено',
                resetButtonTitle: 'Сбросить',
                footer: { selectText: 'выбрать', navigateText: 'навигация', closeText: 'закрыть' },
              },
            },
          },
        },
      },
    },
  },

  locales: {
    root: {
      label: 'Русский',
      lang: 'ru-RU',
      themeConfig: {
        nav: [
          { text: 'Документация', link: '/guide/what-is' },
          { text: 'Настройка', link: '/config/' },
          { text: 'API', link: '/api/' },
          { text: 'v1.12.0', items: [
            { text: 'Minecraft 1.21.11', link: '/guide/install' },
            { text: 'API 1.0.0', link: '/api/' },
            { text: 'GitHub', link: 'https://github.com/yurosing/pocketchat' },
          ]},
        ],
        sidebar: sidebarRu,
        outline: { label: 'На этой странице', level: [2, 3] },
        docFooter: { prev: 'Назад', next: 'Далее' },
        lastUpdatedText: 'Обновлено',
        darkModeSwitchLabel: 'Тема',
        lightModeSwitchTitle: 'Светлая тема',
        darkModeSwitchTitle: 'Тёмная тема',
        sidebarMenuLabel: 'Меню',
        returnToTopLabel: 'Наверх',
        langMenuLabel: 'Сменить язык',
        footer: {
          message: 'Клиентский мод · серверный плагин не нужен · MIT',
          copyright: 'PocketChat для Minecraft 1.21.11',
        },
      },
    },

    en: {
      label: 'English',
      lang: 'en-US',
      themeConfig: {
        nav: [
          { text: 'Docs', link: '/en/guide/what-is' },
          { text: 'Config', link: '/en/config/' },
          { text: 'API', link: '/en/api/' },
          { text: 'v1.12.0', items: [
            { text: 'Minecraft 1.21.11', link: '/en/guide/install' },
            { text: 'API 1.0.0', link: '/en/api/' },
            { text: 'GitHub', link: 'https://github.com/yurosing/pocketchat' },
          ]},
        ],
        sidebar: sidebarEn,
        outline: { label: 'On this page', level: [2, 3] },
        docFooter: { prev: 'Previous', next: 'Next' },
        footer: {
          message: 'Client-side mod · no server plugin · MIT',
          copyright: 'PocketChat for Minecraft 1.21.11',
        },
      },
    },
  },
})
