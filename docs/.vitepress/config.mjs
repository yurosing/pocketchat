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
      { text: 'Голосовые сообщения', link: '/guide/voice' },
      { text: 'Фото и стикеры', link: '/guide/media' },
      { text: 'Видео и YouTube<span class="new-badge">NEW</span>', link: '/guide/video' },
      { text: 'Глобальный чат', link: '/guide/global' },
      { text: 'Каналы и группы', link: '/guide/channels' },
      { text: 'Секретные чаты<span class="new-badge">NEW</span>', link: '/guide/secret-chats' },
      { text: 'Звонки<span class="new-badge">NEW</span>', link: '/guide/calls' },
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
      { text: 'Voice messages', link: '/en/guide/voice' },
      { text: 'Photos & stickers', link: '/en/guide/media' },
      { text: 'Video & YouTube<span class="new-badge">NEW</span>', link: '/en/guide/video' },
      { text: 'Global chat', link: '/en/guide/global' },
      { text: 'Channels & groups', link: '/en/guide/channels' },
      { text: 'Secret chats<span class="new-badge">NEW</span>', link: '/en/guide/secret-chats' },
      { text: 'Calls<span class="new-badge">NEW</span>', link: '/en/guide/calls' },
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
    text: 'Reference',
    collapsed: false,
    items: [
      { text: 'Folder layout', link: '/en/reference/folders' },
      { text: 'FAQ', link: '/en/reference/faq' },
    ],
  },
]

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: 'PocketChat',
  description: 'Клиентский Minecraft-мессенджер поверх /m — история, голосовые, стикеры, поиск.',

  // Репозиторий называется pocketchat → сайт будет на
  // https://yurosing.github.io/pocketchat/
  base: '/pocketchat/',

  cleanUrls: true,
  lastUpdated: true,

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
          { text: 'v1.4.0', items: [
            { text: 'Minecraft 1.21.11', link: '/guide/install' },
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
          { text: 'v1.4.0', items: [
            { text: 'Minecraft 1.21.11', link: '/en/guide/install' },
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
