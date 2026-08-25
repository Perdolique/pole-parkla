import type { Locale } from './i18n/config'

interface GooglePlayConfig {
  url: string
  badgePaths: Record<Locale, string>
}

interface SiteConfig {
  siteUrl: string
  contactEmail: string
  developerName: string
  googlePlay: GooglePlayConfig | null
  sourceUrl: string | null
}

export const siteConfig: SiteConfig = {
  siteUrl: 'https://poleparkla.ee',
  contactEmail: 'hello@poleparkla.ee',
  developerName: 'Perdolique',
  googlePlay: {
    url: 'https://play.google.com/store/apps/details?id=com.perdolique.poleparkla',
    badgePaths: {
      et: '/google-play/et_badge_web_generic.png',
      en: '/google-play/en_badge_web_generic.png',
      ru: '/google-play/ru_badge_web_generic.png'
    }
  },
  sourceUrl: null
}
