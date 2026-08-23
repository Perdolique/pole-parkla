import { getCollection, type CollectionEntry } from 'astro:content'
import { isLocale, locales, type Locale } from '../i18n/config'

type PrivacyEntry = CollectionEntry<'privacy'>
type UpdateEntry = CollectionEntry<'updates'>

function getUpdateLocale(entry: UpdateEntry): Locale {
  const [locale] = entry.id.split('/')

  if (!isLocale(locale)) {
    throw new Error(`Unsupported locale in update entry "${entry.id}"`)
  }

  return locale
}

function assertCompletePublishedTranslations(entries: UpdateEntry[]): void {
  const translationsByVersion = new Map<string, Set<Locale>>()

  for (const entry of entries) {
    if (entry.data.draft) {
      continue
    }

    const locale = getUpdateLocale(entry)
    const translatedLocales = translationsByVersion.get(entry.data.version) ?? new Set<Locale>()

    translatedLocales.add(locale)
    translationsByVersion.set(entry.data.version, translatedLocales)
  }

  for (const [version, translatedLocales] of translationsByVersion) {
    const missingLocales = locales.filter((locale) => !translatedLocales.has(locale))

    if (missingLocales.length > 0) {
      throw new Error(`Published update ${version} is missing locales: ${missingLocales.join(', ')}`)
    }
  }
}

export async function getPrivacyEntry(locale: Locale): Promise<PrivacyEntry> {
  const entries = await getCollection('privacy')
  const entry = entries.find((candidate) => candidate.id === locale)

  if (!entry) {
    throw new Error(`Missing privacy policy for locale "${locale}"`)
  }

  return entry
}

export async function getPublishedUpdates(locale: Locale): Promise<UpdateEntry[]> {
  const entries = await getCollection('updates')

  assertCompletePublishedTranslations(entries)

  return entries
    .filter((entry) => !entry.data.draft && getUpdateLocale(entry) === locale)
    .sort((left, right) => right.data.releasedAt.valueOf() - left.data.releasedAt.valueOf())
}

export function formatDate(date: Date, locale: Locale): string {
  const localeNames = {
    et: 'et-EE',
    en: 'en-GB',
    ru: 'ru-RU'
  } as const satisfies Record<Locale, string>

  return new Intl.DateTimeFormat(localeNames[locale], {
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  }).format(date)
}
