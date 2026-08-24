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

interface HomeInfoItem {
  body: string
  title: string
}

interface FaqItem {
  answer: string
  question: string
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
  howItWorksTitle: string
  howItWorksSteps: readonly string[]
  recognitionTitle: string
  recognitionItems: readonly HomeInfoItem[]
  privacyDetailsLink: string
  faqTitle: string
  faqItems: readonly FaqItem[]
  screenshotsLabel: string
  screenshots: readonly ScreenshotText[]
  socialImageAlt: string
  rssTitle: string
  rssDescription: string
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
      title: 'Pole parkla! — teade valesti pargitud sõidukist',
      description: 'Androidi rakendus jalgratta- või kõnniteele pargitud sõidukist tõenditega teate ettevalmistamiseks Eestis.'
    },
    privacyMeta: {
      title: 'Privaatsuspoliitika — Pole parkla!',
      description: 'Kuidas Pole parkla töötleb fotosid, asukohta, teateid ja valikulise pilvetuvastuse andmeid.'
    },
    updatesMeta: {
      title: 'Muudatused — Pole parkla!',
      description: 'Pole parkla avalike versioonide muudatused.'
    },
    skipToContent: 'Liigu põhisisu juurde',
    logoAlt: 'Pole parkla! märk',
    navigationLabel: 'Põhinavigatsioon',
    menuLabel: 'Ava menüü',
    languageSwitcherLabel: 'Vali keel',
    privacyLink: 'Privaatsus',
    updatesLink: 'Muudatused',
    sourceLink: 'Lähtekood',
    googlePlayAlt: 'Laadi alla Google Playst',
    heroDescription: 'Pole parkla aitab Eestis ette valmistada tõenditega teate jalgratta- või kõnniteele pargitud sõidukist.',
    howItWorksTitle: 'Kuidas teade valmib',
    howItWorksSteps: [
      'Lisa valesti pargitud sõidukist üks kuni kolm fotot.',
      'Kontrolli tuvastatud numbrimärki, sõidukit, asukohta ja aega.',
      'Ava valmis eestikeelne teade oma meilirakenduses ning muuda või saada see ise.'
    ],
    recognitionTitle: 'Tuvastus ja sinu kontroll',
    recognitionItems: [
      {
        title: 'Kohapealne tuvastus',
        body: 'Rakendusse lisatud OCR ja numbrimärgi mudelid töötavad seadmes; nende jaoks fotosid üles ei laadita.'
      },
      {
        title: 'Võrku vajavad valikud',
        body: 'Aadressiotsing, kaart ja sinu käivitatud valikuline pilvetuvastus vajavad internetiühendust.'
      },
      {
        title: 'Otsus jääb sulle',
        body: 'Automaatne tuvastus ei kinnita andmeid ega saada teadet. Enne meilirakenduse avamist kontrollid kõike sina.'
      }
    ],
    privacyDetailsLink: 'Loe andmete töötlemisest',
    faqTitle: 'Korduma kippuvad küsimused',
    faqItems: [
      {
        question: 'Kas Pole parkla saadab teate ise?',
        answer: 'Ei. Rakendus avab sinu valitud meilirakenduses muudetava mustandi. Sina otsustad, kas seda muuta, saata või kustutada.'
      },
      {
        question: 'Mis töötab internetita?',
        answer: 'Fotode lisamine, kohalik OCR, numbrimärgi tuvastus ja teate muutmine töötavad seadmes. Aadressiotsing, kaart ja valikuline pilvetuvastus vajavad võrku.'
      },
      {
        question: 'Kus fotosid ja teateid hoitakse?',
        answer: 'Need jäävad rakenduse privaatsesse salvestusruumi, kuni kustutad teate või kõik kohalikud andmed.'
      },
      {
        question: 'Millised keeled on toetatud?',
        answer: 'Kasutajaliides on eesti, inglise ja vene keeles. Valmis ametlik teade koostatakse eesti keeles.'
      },
      {
        question: 'Mida võib pilvetuvastusse saata?',
        answer: 'Ainult pärast sinu nupuvajutust ja nõusolekut saadetakse valitud teenusepakkujale põhifotost loodud EXIF-andmeteta JPEG. Profiili, aadressi ja teate teksti ei lisata.'
      }
    ],
    screenshotsLabel: 'Rakenduse vaated',
    screenshots: [
      { id: 'camera', label: 'Kaamera', alt: 'Pole parkla kaameravaade' },
      { id: 'review', label: 'Kontroll', alt: 'Pole parkla teate kontrollvaade' },
      { id: 'report', label: 'Teade', alt: 'Pole parkla valmis teate vaade' }
    ],
    socialImageAlt: 'Pole parkla! märk ja eestikeelne sõiduki kontrollvaade',
    rssTitle: 'Pole parkla! muudatused',
    rssDescription: 'Pole parkla avalike versioonide muudatused eesti keeles.',
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
      title: 'Pole parkla! — report vehicles blocking paths',
      description: 'An Android app for preparing evidence-based reports about vehicles parked on cycle paths or footways in Estonia.'
    },
    privacyMeta: {
      title: 'Privacy Policy — Pole parkla!',
      description: 'How Pole parkla handles photos, location, reports, and optional cloud recognition data.'
    },
    updatesMeta: {
      title: 'Updates — Pole parkla!',
      description: 'Changes in public Pole parkla releases.'
    },
    skipToContent: 'Skip to main content',
    logoAlt: 'Pole parkla! mark',
    navigationLabel: 'Main navigation',
    menuLabel: 'Open menu',
    languageSwitcherLabel: 'Choose language',
    privacyLink: 'Privacy',
    updatesLink: 'Updates',
    sourceLink: 'Source',
    googlePlayAlt: 'Get it on Google Play',
    heroDescription: 'Pole parkla helps you prepare an evidence-based report about a vehicle parked on a cycle path or footway in Estonia.',
    howItWorksTitle: 'How a report is prepared',
    howItWorksSteps: [
      'Add one to three photos of the incorrectly parked vehicle.',
      'Review the recognized plate, vehicle, location, and time.',
      'Open the prepared Estonian draft in your mail app, then edit or send it yourself.'
    ],
    recognitionTitle: 'Recognition and your control',
    recognitionItems: [
      {
        title: 'On-device recognition',
        body: 'The bundled OCR and plate models run on the device; photos are not uploaded for this processing.'
      },
      {
        title: 'Options that need a network',
        body: 'Address search, the map, and optional cloud recognition that you start require an internet connection.'
      },
      {
        title: 'You make the decision',
        body: 'Automatic recognition never confirms details or sends a report. You review everything before the mail app opens.'
      }
    ],
    privacyDetailsLink: 'Read how data is handled',
    faqTitle: 'Frequently asked questions',
    faqItems: [
      {
        question: 'Does Pole parkla send a report by itself?',
        answer: 'No. It opens an editable draft in the mail app you choose. You decide whether to edit, send, or discard it.'
      },
      {
        question: 'What works without an internet connection?',
        answer: 'Adding photos, local OCR, plate recognition, and report editing run on the device. Address search, the map, and optional cloud recognition need a network.'
      },
      {
        question: 'Where are photos and reports stored?',
        answer: 'They remain in the app\'s private storage until you delete the report or all local data.'
      },
      {
        question: 'Which languages are supported?',
        answer: 'The interface is available in Estonian, English, and Russian. The prepared official report is in Estonian.'
      },
      {
        question: 'What can be sent for cloud recognition?',
        answer: 'Only after you press the button and consent, an EXIF-free JPEG derived from the primary photo is sent to the chosen provider. Your profile, address, and report text are not included.'
      }
    ],
    screenshotsLabel: 'App views',
    screenshots: [
      { id: 'camera', label: 'Camera', alt: 'Pole parkla camera view' },
      { id: 'review', label: 'Review', alt: 'Pole parkla report review view' },
      { id: 'report', label: 'Report', alt: 'Pole parkla prepared report view' }
    ],
    socialImageAlt: 'Pole parkla! mark and an English vehicle review screen',
    rssTitle: 'Pole parkla! updates',
    rssDescription: 'Changes in public Pole parkla releases in English.',
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
      title: 'Pole parkla! — обращение о машине на дорожке',
      description: 'Android-приложение для подготовки обращения с фотографиями о машине на велодорожке или тротуаре в Эстонии.'
    },
    privacyMeta: {
      title: 'Политика конфиденциальности — Pole parkla!',
      description: 'Как Pole parkla обрабатывает фотографии, геолокацию, обращения и данные облачного распознавания.'
    },
    updatesMeta: {
      title: 'Изменения — Pole parkla!',
      description: 'Изменения в публичных версиях Pole parkla.'
    },
    skipToContent: 'Перейти к основному содержанию',
    logoAlt: 'Знак Pole parkla!',
    navigationLabel: 'Основная навигация',
    menuLabel: 'Открыть меню',
    languageSwitcherLabel: 'Выбрать язык',
    privacyLink: 'Приватность',
    updatesLink: 'Изменения',
    sourceLink: 'Исходники',
    googlePlayAlt: 'Доступно в Google Play',
    heroDescription: 'Pole parkla помогает подготовить обращение с доказательствами о машине на велодорожке или тротуаре в Эстонии.',
    howItWorksTitle: 'Как готовится обращение',
    howItWorksSteps: [
      'Добавьте от одной до трёх фотографий неправильно припаркованной машины.',
      'Проверьте распознанный номер, машину, место и время.',
      'Откройте готовый черновик на эстонском в почтовом приложении, затем измените или отправьте его сами.'
    ],
    recognitionTitle: 'Распознавание под вашим контролем',
    recognitionItems: [
      {
        title: 'Распознавание на устройстве',
        body: 'Встроенные OCR и модели номеров работают на устройстве; для этой обработки фотографии не загружаются.'
      },
      {
        title: 'Что требует подключения',
        body: 'Поиск адреса, карта и необязательное облачное распознавание, которое запускаете вы, требуют интернета.'
      },
      {
        title: 'Решение остаётся за вами',
        body: 'Автоматическое распознавание не подтверждает данные и не отправляет обращение. Перед открытием почтового приложения всё проверяете вы.'
      }
    ],
    privacyDetailsLink: 'Подробнее об обработке данных',
    faqTitle: 'Частые вопросы',
    faqItems: [
      {
        question: 'Pole parkla отправляет обращение самостоятельно?',
        answer: 'Нет. Приложение открывает редактируемый черновик в выбранном почтовом приложении. Вы решаете, изменить, отправить или удалить его.'
      },
      {
        question: 'Что работает без интернета?',
        answer: 'Добавление фотографий, локальные OCR и распознавание номера, а также редактирование обращения работают на устройстве. Для адреса, карты и облачного распознавания нужна сеть.'
      },
      {
        question: 'Где хранятся фотографии и обращения?',
        answer: 'Они остаются в закрытом хранилище приложения, пока вы не удалите обращение или все локальные данные.'
      },
      {
        question: 'Какие языки поддерживаются?',
        answer: 'Интерфейс доступен на эстонском, английском и русском. Готовое официальное обращение составляется на эстонском.'
      },
      {
        question: 'Что может уйти в облачное распознавание?',
        answer: 'Только после нажатия кнопки и согласия выбранному провайдеру отправляется JPEG основной фотографии без EXIF. Профиль, адрес и текст обращения не включаются.'
      }
    ],
    screenshotsLabel: 'Экраны приложения',
    screenshots: [
      { id: 'camera', label: 'Камера', alt: 'Экран камеры Pole parkla' },
      { id: 'review', label: 'Проверка', alt: 'Экран проверки обращения Pole parkla' },
      { id: 'report', label: 'Обращение', alt: 'Экран готового обращения Pole parkla' }
    ],
    socialImageAlt: 'Знак Pole parkla! и русскоязычный экран проверки автомобиля',
    rssTitle: 'Изменения Pole parkla!',
    rssDescription: 'Изменения в публичных версиях Pole parkla на русском языке.',
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
