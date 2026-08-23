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
  googlePlay: null,
  sourceUrl: null
}
