import rss from '@astrojs/rss'
import { siteConfig } from '../config'
import { getLocalizedPath, localeLanguageTags, type Locale } from '../i18n/config'
import { ui } from '../i18n/ui'
import { getPublishedUpdates, getUpdateAnchor } from './content'

/** Maps published localized releases into the site's stable RSS contract. */
export async function createUpdatesFeed(locale: Locale, configuredSite: URL | undefined): Promise<Response> {
  const text = ui[locale]
  const updates = await getPublishedUpdates(locale)
  const site = configuredSite ?? new URL(siteConfig.siteUrl)
  const updatesPath = getLocalizedPath(locale, 'updates')

  const items = updates.map((entry) => ({
    title: `${entry.data.version} — ${entry.data.title}`,
    description: entry.data.highlights.join(' '),
    pubDate: entry.data.releasedAt,
    link: `${updatesPath}#${getUpdateAnchor(entry.data.version)}`
  }))

  return rss({
    title: text.rssTitle,
    description: text.rssDescription,
    site,
    items,
    customData: `<language>${localeLanguageTags[locale]}</language>`
  })
}
