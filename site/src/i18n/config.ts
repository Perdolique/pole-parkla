export const locales = ['et', 'en', 'ru'] as const

export type Locale = typeof locales[number]

export const defaultLocale: Locale = 'et'

export const localeLanguageTags = {
  et: 'et-EE',
  en: 'en-EE',
  ru: 'ru-EE'
} as const satisfies Record<Locale, string>

export const openGraphLocales = {
  et: 'et_EE',
  en: 'en_EE',
  ru: 'ru_EE'
} as const satisfies Record<Locale, string>

export type RouteName = 'home' | 'privacy' | 'updates'

export interface LocaleRouteProps {
  [key: string]: unknown
  locale: Locale
}

const routeSegments = {
  home: '',
  privacy: 'privacy/',
  updates: 'updates/'
} as const satisfies Record<RouteName, string>

export function isLocale(value: string | undefined): value is Locale {
  return locales.some((locale) => locale === value)
}

export function getLocalizedPath(locale: Locale, route: RouteName): string {
  const localePrefix = locale === defaultLocale ? '' : `${locale}/`
  const segment = routeSegments[route]

  return `/${localePrefix}${segment}`
}

export function getLocalizedRssPath(locale: Locale): string {
  const localePrefix = locale === defaultLocale ? '' : `${locale}/`

  return `/${localePrefix}updates.xml`
}

export function getStaticLocalePaths(): Array<{
  params: { lang: string | undefined }
  props: LocaleRouteProps
}> {
  return locales.map((locale) => ({
    params: {
      lang: locale === defaultLocale ? undefined : locale
    },
    props: {
      locale
    }
  }))
}
