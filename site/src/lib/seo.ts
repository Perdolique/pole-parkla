import { siteConfig } from '../config'
import {
  getLocalizedPath,
  getLocalizedRssPath,
  localeLanguageTags,
  locales,
  openGraphLocales,
  type Locale,
  type RouteName
} from '../i18n/config'

export type SeoPageKind = 'home' | 'privacy' | 'updates' | 'not-found'

export interface SeoAlternate {
  href: string
  hreflang: string
  locale: Locale
}

export interface SeoImage {
  alt: string
  height: number
  mimeType: 'image/jpeg'
  url: string
  width: number
}

export interface SeoMetadataInput {
  dateModified?: string
  description: string
  imageAlt: string
  locale: Locale
  pageKind: SeoPageKind
  robots?: string
  route: RouteName
  title: string
}

export interface SeoMetadata {
  alternateUrls: readonly SeoAlternate[]
  canonicalUrl: string
  dateModified?: string
  defaultUrl: string
  description: string
  image: SeoImage
  locale: Locale
  openGraphLocale: string
  pageKind: SeoPageKind
  robots: string
  route: RouteName
  rssUrl: string
  title: string
}

const indexRobots = 'index,follow,max-image-preview:large,max-snippet:-1,max-video-preview:-1'

const socialImagePaths = {
  et: '/social/pole-parkla-et.jpg',
  en: '/social/pole-parkla-en.jpg',
  ru: '/social/pole-parkla-ru.jpg'
} as const satisfies Record<Locale, string>

export function createSeoMetadata(input: SeoMetadataInput): SeoMetadata {
  const canonicalPath = getLocalizedPath(input.locale, input.route)
  const canonicalUrl = new URL(canonicalPath, siteConfig.siteUrl).toString()
  const alternateUrls = locales.map((locale) => {
    const path = getLocalizedPath(locale, input.route)

    return {
      href: new URL(path, siteConfig.siteUrl).toString(),
      hreflang: localeLanguageTags[locale],
      locale
    }
  })
  const defaultPath = getLocalizedPath('et', input.route)
  const imageUrl = new URL(socialImagePaths[input.locale], siteConfig.siteUrl).toString()
  const rssPath = getLocalizedRssPath(input.locale)

  return {
    alternateUrls,
    canonicalUrl,
    dateModified: input.dateModified,
    defaultUrl: new URL(defaultPath, siteConfig.siteUrl).toString(),
    description: input.description,
    image: {
      alt: input.imageAlt,
      height: 630,
      mimeType: 'image/jpeg',
      url: imageUrl,
      width: 1200
    },
    locale: input.locale,
    openGraphLocale: openGraphLocales[input.locale],
    pageKind: input.pageKind,
    robots: input.robots ?? indexRobots,
    route: input.route,
    rssUrl: new URL(rssPath, siteConfig.siteUrl).toString(),
    title: input.title
  }
}

export function isIndexableSeo(seo: SeoMetadata): boolean {
  return !seo.robots.toLowerCase().includes('noindex')
}

/** Builds the shared entity graph while keeping every claim tied to visible site content. */
export function buildStructuredData(seo: SeoMetadata): string {
  const personId = `${siteConfig.siteUrl}/#person`
  const websiteId = `${siteConfig.siteUrl}/#website`
  const applicationId = `${siteConfig.siteUrl}/#application`
  const pageId = `${seo.canonicalUrl}#webpage`
  const person = {
    '@type': 'Person',
    '@id': personId,
    name: siteConfig.developerName,
    url: 'https://perd.dev/'
  }
  const website = {
    '@type': 'WebSite',
    '@id': websiteId,
    url: `${siteConfig.siteUrl}/`,
    name: 'Pole parkla',
    inLanguage: locales.map((locale) => localeLanguageTags[locale]),
    publisher: {
      '@id': personId
    }
  }
  const pageTypes = {
    home: 'WebPage',
    privacy: 'WebPage',
    updates: 'CollectionPage',
    'not-found': 'WebPage'
  } as const satisfies Record<SeoPageKind, string>
  const webPage: Record<string, unknown> = {
    '@type': pageTypes[seo.pageKind],
    '@id': pageId,
    url: seo.canonicalUrl,
    name: seo.title,
    description: seo.description,
    inLanguage: localeLanguageTags[seo.locale],
    isPartOf: {
      '@id': websiteId
    }
  }

  if (seo.dateModified) {
    webPage.dateModified = seo.dateModified
  }

  const graph: Record<string, unknown>[] = [person, website, webPage]

  if (seo.pageKind === 'home') {
    webPage.about = {
      '@id': applicationId
    }

    graph.push({
      '@type': 'MobileApplication',
      '@id': applicationId,
      name: 'Pole parkla',
      url: `${siteConfig.siteUrl}/`,
      description: seo.description,
      applicationCategory: 'UtilitiesApplication',
      operatingSystem: 'Android',
      countriesSupported: 'EE',
      inLanguage: localeLanguageTags[seo.locale],
      screenshot: ['camera', 'review', 'report'].map((id) =>
        new URL(`/screenshots/${seo.locale}/${id}.webp`, siteConfig.siteUrl).toString()
      ),
      author: {
        '@id': personId
      }
    })
  }

  const structuredData = {
    '@context': 'https://schema.org',
    '@graph': graph
  }

  return JSON.stringify(structuredData).replaceAll('<', '\\u003c')
}
