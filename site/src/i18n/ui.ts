import type { Locale } from './config'

interface PageMeta {
  title: string
  description: string
}

interface ScreenshotText {
  id: 'camera' | 'review' | 'report'
  label: string
  alt: string
}

interface UiText {
  homeMeta: PageMeta
  privacyMeta: PageMeta
  updatesMeta: PageMeta
  skipToContent: string
  logoAlt: string
  navigationLabel: string
  menuLabel: string
  languageSwitcherLabel: string
  privacyLink: string
  updatesLink: string
  sourceLink: string
  googlePlayAlt: string
  heroDescription: string
  screenshotsLabel: string
  screenshots: readonly ScreenshotText[]
  latestReleaseEyebrow: string
  releaseDateLabel: string
  allUpdatesLink: string
  noUpdates: string
  footerLabel: string
  notFoundTitle: string
  notFoundDescription: string
  homeLink: string
}

export const ui = {
  et: {
    homeMeta: {
      title: 'Pole parkla',
      description: 'Androidi rakendus jalgratta- või kõnniteele pargitud sõidukist teate koostamiseks.'
    },
    privacyMeta: {
      title: 'Privaatsuspoliitika — Pole parkla',
      description: 'Kuidas Pole parkla töötleb fotosid, asukohta, teateid ja valikulise pilvetuvastuse andmeid.'
    },
    updatesMeta: {
      title: 'Muudatused — Pole parkla',
      description: 'Pole parkla avalike versioonide muudatused.'
    },
    skipToContent: 'Liigu põhisisu juurde',
    logoAlt: 'Pole parkla märk',
    navigationLabel: 'Põhinavigatsioon',
    menuLabel: 'Ava menüü',
    languageSwitcherLabel: 'Vali keel',
    privacyLink: 'Privaatsus',
    updatesLink: 'Muudatused',
    sourceLink: 'Lähtekood',
    googlePlayAlt: 'Laadi alla Google Playst',
    heroDescription: 'Pole parkla aitab kiiremini koostada teate jalgratta- või kõnniteele pargitud sõidukist.',
    screenshotsLabel: 'Rakenduse vaated',
    screenshots: [
      { id: 'camera', label: 'Kaamera', alt: 'Pole parkla kaameravaade' },
      { id: 'review', label: 'Kontroll', alt: 'Pole parkla teate kontrollvaade' },
      { id: 'report', label: 'Teade', alt: 'Pole parkla valmis teate vaade' }
    ],
    latestReleaseEyebrow: 'Viimane versioon',
    releaseDateLabel: 'Avaldatud',
    allUpdatesLink: 'Kõik muudatused',
    noUpdates: 'Avalikke versioone ei ole veel lisatud.',
    footerLabel: 'Jaluse navigatsioon',
    notFoundTitle: 'Lehte ei leitud',
    notFoundDescription: 'Seda aadressi ei ole olemas või leht on teisaldatud.',
    homeLink: 'Tagasi avalehele'
  },
  en: {
    homeMeta: {
      title: 'Pole parkla',
      description: 'An Android app for preparing reports about vehicles parked on cycle paths or footways.'
    },
    privacyMeta: {
      title: 'Privacy Policy — Pole parkla',
      description: 'How Pole parkla handles photos, location, reports, and optional cloud recognition data.'
    },
    updatesMeta: {
      title: 'Updates — Pole parkla',
      description: 'Changes in public Pole parkla releases.'
    },
    skipToContent: 'Skip to main content',
    logoAlt: 'Pole parkla mark',
    navigationLabel: 'Main navigation',
    menuLabel: 'Open menu',
    languageSwitcherLabel: 'Choose language',
    privacyLink: 'Privacy',
    updatesLink: 'Updates',
    sourceLink: 'Source',
    googlePlayAlt: 'Get it on Google Play',
    heroDescription: 'Pole parkla helps you prepare a report about a vehicle parked on a cycle path or footway.',
    screenshotsLabel: 'App views',
    screenshots: [
      { id: 'camera', label: 'Camera', alt: 'Pole parkla camera view' },
      { id: 'review', label: 'Review', alt: 'Pole parkla report review view' },
      { id: 'report', label: 'Report', alt: 'Pole parkla prepared report view' }
    ],
    latestReleaseEyebrow: 'Latest version',
    releaseDateLabel: 'Released',
    allUpdatesLink: 'All updates',
    noUpdates: 'No public releases have been added yet.',
    footerLabel: 'Footer navigation',
    notFoundTitle: 'Page not found',
    notFoundDescription: 'This address does not exist or the page has moved.',
    homeLink: 'Back to the home page'
  },
  ru: {
    homeMeta: {
      title: 'Pole parkla',
      description: 'Android-приложение для подготовки обращений о машинах на велодорожках и тротуарах.'
    },
    privacyMeta: {
      title: 'Политика конфиденциальности — Pole parkla',
      description: 'Как Pole parkla обрабатывает фотографии, геолокацию, обращения и данные облачного распознавания.'
    },
    updatesMeta: {
      title: 'Изменения — Pole parkla',
      description: 'Изменения в публичных версиях Pole parkla.'
    },
    skipToContent: 'Перейти к основному содержанию',
    logoAlt: 'Знак Pole parkla',
    navigationLabel: 'Основная навигация',
    menuLabel: 'Открыть меню',
    languageSwitcherLabel: 'Выбрать язык',
    privacyLink: 'Приватность',
    updatesLink: 'Изменения',
    sourceLink: 'Исходники',
    googlePlayAlt: 'Доступно в Google Play',
    heroDescription: 'Pole parkla помогает быстрее подготовить обращение о машине, припаркованной на велодорожке или тротуаре.',
    screenshotsLabel: 'Экраны приложения',
    screenshots: [
      { id: 'camera', label: 'Камера', alt: 'Экран камеры Pole parkla' },
      { id: 'review', label: 'Проверка', alt: 'Экран проверки обращения Pole parkla' },
      { id: 'report', label: 'Обращение', alt: 'Экран готового обращения Pole parkla' }
    ],
    latestReleaseEyebrow: 'Последняя версия',
    releaseDateLabel: 'Опубликовано',
    allUpdatesLink: 'Все изменения',
    noUpdates: 'Публичных версий пока нет.',
    footerLabel: 'Навигация в подвале',
    notFoundTitle: 'Страница не найдена',
    notFoundDescription: 'Такого адреса нет или страница была перемещена.',
    homeLink: 'Вернуться на главную'
  }
} as const satisfies Record<Locale, UiText>
