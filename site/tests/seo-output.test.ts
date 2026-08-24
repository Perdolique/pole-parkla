import assert from 'node:assert/strict'
import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import test, { type TestContext } from 'node:test'

type Locale = 'et' | 'en' | 'ru'
type PageName = 'home' | 'privacy' | 'updates'

interface LocaleData {
  readonly htmlLanguage: string
  readonly languageTag: string
  readonly openGraphLocale: string
  readonly prefix: string
  readonly rss: string
  readonly socialImage: string
}

interface PageData {
  readonly output: string
  readonly pageType: 'CollectionPage' | 'WebPage'
  readonly segment: string
}

type HtmlAttributes = Map<string, string>
type ExpectedAttributes = Readonly<Record<string, string>>
type StructuredDataNode = Record<string, unknown>

interface StructuredData {
  readonly '@context': 'https://schema.org'
  readonly '@graph': readonly StructuredDataNode[]
}

const siteRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const distRoot = path.join(siteRoot, 'dist')
const siteUrl = 'https://poleparkla.ee'
const personId = `${siteUrl}/#person`
const screenshotSizes = '(max-width: 387px) calc(78vw - 2px), 300px'
const localeConfig = {
  et: {
    htmlLanguage: 'et',
    languageTag: 'et-EE',
    openGraphLocale: 'et_EE',
    prefix: '',
    socialImage: `${siteUrl}/social/pole-parkla-et.jpg`,
    rss: `${siteUrl}/updates.xml`
  },
  en: {
    htmlLanguage: 'en',
    languageTag: 'en-EE',
    openGraphLocale: 'en_EE',
    prefix: 'en/',
    socialImage: `${siteUrl}/social/pole-parkla-en.jpg`,
    rss: `${siteUrl}/en/updates.xml`
  },
  ru: {
    htmlLanguage: 'ru',
    languageTag: 'ru-EE',
    openGraphLocale: 'ru_EE',
    prefix: 'ru/',
    socialImage: `${siteUrl}/social/pole-parkla-ru.jpg`,
    rss: `${siteUrl}/ru/updates.xml`
  }
} satisfies Record<Locale, LocaleData>
const pageConfig = {
  home: {
    output: 'index.html',
    pageType: 'WebPage',
    segment: ''
  },
  privacy: {
    output: 'privacy/index.html',
    pageType: 'WebPage',
    segment: 'privacy/'
  },
  updates: {
    output: 'updates/index.html',
    pageType: 'CollectionPage',
    segment: 'updates/'
  }
} satisfies Record<PageName, PageData>
const locales = ['et', 'en', 'ru'] as const satisfies readonly Locale[]
const pages = ['home', 'privacy', 'updates'] as const satisfies readonly PageName[]

function parseAttributes(tag: string): HtmlAttributes {
  const attributes: HtmlAttributes = new Map()
  const attributePattern = /([:@\w-]+)(?:="([^"]*)")?/g

  for (const match of tag.matchAll(attributePattern)) {
    const name = match[1]

    if (name !== undefined) {
      attributes.set(name, match[2] ?? '')
    }
  }

  return attributes
}

function getTags(html: string, tagName: string): HtmlAttributes[] {
  const pattern = new RegExp(`<${tagName}\\b[^>]*>`, 'gi')

  return [...html.matchAll(pattern)].map((match) => parseAttributes(match[0]))
}

function findTag(
  html: string,
  tagName: string,
  expectedAttributes: ExpectedAttributes
): HtmlAttributes | undefined {
  const tags = getTags(html, tagName)

  return tags.find((attributes) =>
    Object.entries(expectedAttributes).every(([name, value]) => attributes.get(name) === value)
  )
}

function getMetaContent(html: string, selectorName: string, selectorValue: string): string {
  const meta = findTag(html, 'meta', { [selectorName]: selectorValue })

  assert.ok(meta, `Missing meta ${selectorName}="${selectorValue}"`)
  const content = meta.get('content')

  if (content === undefined) {
    throw new Error(`Meta ${selectorName}="${selectorValue}" has no content`)
  }

  return content
}

function getLinkHref(html: string, expectedAttributes: ExpectedAttributes): string {
  const link = findTag(html, 'link', expectedAttributes)

  assert.ok(link, `Missing link ${JSON.stringify(expectedAttributes)}`)
  const href = link.get('href')

  if (href === undefined) {
    throw new Error(`Link ${JSON.stringify(expectedAttributes)} has no href`)
  }

  return href
}

function getTitle(html: string): string {
  const match = html.match(/<title>([^<]+)<\/title>/)

  assert.ok(match, 'Missing title')
  const title = match[1]

  assert.notEqual(title, undefined, 'Title is empty')

  return title
}

function isStructuredDataNode(value: unknown): value is StructuredDataNode {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function getStructuredData(html: string): StructuredData {
  const match = html.match(/<script type="application\/ld\+json">([^<]+)<\/script>/)

  assert.ok(match, 'Missing JSON-LD')
  const json = match[1]

  assert.notEqual(json, undefined, 'JSON-LD is empty')
  const value: unknown = JSON.parse(json)

  assert.ok(isStructuredDataNode(value), 'JSON-LD must be an object')
  assert.equal(value['@context'], 'https://schema.org')
  const graph = value['@graph']

  assert.ok(Array.isArray(graph), 'JSON-LD @graph must be an array')
  assert.ok(graph.every(isStructuredDataNode), 'JSON-LD @graph entries must be objects')

  return {
    '@context': 'https://schema.org',
    '@graph': graph
  }
}

function getCanonicalUrl(locale: Locale, page: PageName): string {
  const localeData = localeConfig[locale]
  const pageData = pageConfig[page]

  return `${siteUrl}/${localeData.prefix}${pageData.segment}`
}

function getOutputPath(locale: Locale, page: PageName): string {
  const localeData = localeConfig[locale]
  const pageData = pageConfig[page]

  return path.join(distRoot, localeData.prefix, pageData.output)
}

test('all indexable pages expose a complete localized SEO contract', async (context: TestContext) => {
  const titles = new Set<string>()
  const descriptions = new Set<string>()

  for (const locale of locales) {
    for (const page of pages) {
      await context.test(`${locale} ${page}`, async () => {
        const localeData = localeConfig[locale]
        const pageData = pageConfig[page]
        const canonicalUrl = getCanonicalUrl(locale, page)
        const html = await readFile(getOutputPath(locale, page), 'utf8')
        const title = getTitle(html)
        const description = getMetaContent(html, 'name', 'description')
        const robots = getMetaContent(html, 'name', 'robots')

        assert.match(html, new RegExp(`<html lang="${localeData.htmlLanguage}">`))
        assert.match(robots, /^index,follow/)
        assert.match(robots, /max-image-preview:large/)
        assert.equal(getLinkHref(html, { rel: 'canonical' }), canonicalUrl)
        assert.equal(getLinkHref(html, { rel: 'describedby', type: 'text/plain' }), '/llms.txt')
        assert.equal(
          getLinkHref(html, { rel: 'alternate', type: 'application/rss+xml' }),
          localeData.rss
        )

        for (const alternateLocale of locales) {
          const alternateData = localeConfig[alternateLocale]
          const alternateUrl = getCanonicalUrl(alternateLocale, page)

          assert.equal(
            getLinkHref(html, { rel: 'alternate', hreflang: alternateData.languageTag }),
            alternateUrl
          )
        }

        assert.equal(
          getLinkHref(html, { rel: 'alternate', hreflang: 'x-default' }),
          getCanonicalUrl('et', page)
        )
        assert.equal(getMetaContent(html, 'property', 'og:title'), title)
        assert.equal(getMetaContent(html, 'property', 'og:description'), description)
        assert.equal(getMetaContent(html, 'property', 'og:url'), canonicalUrl)
        assert.equal(getMetaContent(html, 'property', 'og:locale'), localeData.openGraphLocale)
        assert.equal(getMetaContent(html, 'property', 'og:image'), localeData.socialImage)
        assert.equal(getMetaContent(html, 'property', 'og:image:secure_url'), localeData.socialImage)
        assert.equal(getMetaContent(html, 'property', 'og:image:type'), 'image/jpeg')
        assert.equal(getMetaContent(html, 'property', 'og:image:width'), '1200')
        assert.equal(getMetaContent(html, 'property', 'og:image:height'), '630')
        assert.ok(getMetaContent(html, 'property', 'og:image:alt'))
        assert.equal(getMetaContent(html, 'name', 'twitter:card'), 'summary_large_image')
        assert.equal(getMetaContent(html, 'name', 'twitter:title'), title)
        assert.equal(getMetaContent(html, 'name', 'twitter:description'), description)
        assert.equal(getMetaContent(html, 'name', 'twitter:image'), localeData.socialImage)
        assert.ok(getMetaContent(html, 'name', 'twitter:image:alt'))
        assert.equal(findTag(html, 'meta', { name: 'keywords' }), undefined)
        assert.equal(findTag(html, 'link', { rel: 'stylesheet' }), undefined)
        assert.match(html, /<style(?:\s|>)/)

        const structuredData = getStructuredData(html)
        const graph = structuredData['@graph']
        const webPage = graph.find((entry) => entry['@id'] === `${canonicalUrl}#webpage`)
        const website = graph.find((entry) => entry['@type'] === 'WebSite')
        const person = graph.find((entry) => entry['@id'] === personId)

        assert.ok(webPage, `Missing WebPage JSON-LD node for ${canonicalUrl}`)
        assert.ok(website, `Missing WebSite JSON-LD node for ${canonicalUrl}`)
        assert.ok(person, `Missing Person JSON-LD node for ${canonicalUrl}`)
        assert.equal(person['@type'], 'Person')
        assert.equal(person.name, 'Perdolique')
        assert.equal(person.url, 'https://perd.dev/')
        assert.deepEqual(website.publisher, { '@id': personId })
        assert.equal(graph.some((entry) => entry['@type'] === 'Organization'), false)
        assert.equal(webPage['@type'], pageData.pageType)
        assert.equal(webPage.name, title)
        assert.equal(webPage.description, description)
        assert.equal(webPage.inLanguage, localeData.languageTag)

        if (page === 'home') {
          const application = graph.find((entry) => entry['@type'] === 'MobileApplication')

          assert.ok(application, `Missing MobileApplication JSON-LD node for ${canonicalUrl}`)
          assert.equal(application.operatingSystem, 'Android')
          assert.equal(application.countriesSupported, 'EE')
          assert.equal(application.inLanguage, localeData.languageTag)
          assert.deepEqual(application.author, { '@id': personId })
          assert.ok(Array.isArray(application.screenshot))
          assert.equal(application.screenshot.length, 3)
          assert.equal(application.offers, undefined)
          assert.equal(application.installUrl, undefined)
          assert.match(html, /<h1[^>]*>Pole parkla!<\/h1>/)
          assert.equal((html.match(/<h1\b/g) ?? []).length, 1)
          assert.match(html, /<dl\b/)
          const images = getTags(html, 'img')
          const brandImage = images.find(
            (attributes) =>
              attributes.get('width') === '42' && attributes.get('height') === '42'
          )

          assert.ok(brandImage, `Missing responsive brand image for ${locale}`)
          assert.match(brandImage.get('src') ?? '', /^\/_astro\/brand-mark\..+\.webp$/)
          assert.match(brandImage.get('srcset') ?? '', / 1x,.* 2x,.* 3x/)
          assert.equal(brandImage.get('loading'), 'eager')

          const cameraImage = findTag(html, 'img', {
            src: `/screenshots/${locale}/camera.webp`
          })
          const reviewImage = findTag(html, 'img', {
            src: `/screenshots/${locale}/review.webp`
          })
          const reportImage = findTag(html, 'img', {
            src: `/screenshots/${locale}/report.webp`
          })

          assert.ok(cameraImage, `Missing camera screenshot for ${locale}`)
          assert.ok(reviewImage, `Missing review screenshot for ${locale}`)
          assert.ok(reportImage, `Missing report screenshot for ${locale}`)
          assert.equal(cameraImage.get('sizes'), screenshotSizes)
          assert.equal(cameraImage.get('loading'), 'eager')
          assert.equal(cameraImage.get('fetchpriority'), 'high')
          assert.match(cameraImage.get('srcset') ?? '', /camera-420\.webp 420w/)
          assert.match(cameraImage.get('srcset') ?? '', /camera-600\.webp 600w/)
          assert.equal(reviewImage.get('sizes'), screenshotSizes)
          assert.equal(reviewImage.get('loading'), 'lazy')
          assert.equal(reportImage.get('sizes'), screenshotSizes)
          assert.equal(reportImage.get('loading'), 'lazy')
        }

        if (page === 'privacy') {
          assert.equal(typeof webPage.dateModified, 'string')
        }

        assert.doesNotMatch(JSON.stringify(structuredData), /FAQPage|aggregateRating/)
        assert.ok(!titles.has(title), `Duplicate title: ${title}`)
        assert.ok(!descriptions.has(description), `Duplicate description: ${description}`)
        titles.add(title)
        descriptions.add(description)
      })
    }
  }
})

test('sitemap contains only the nine canonical localized HTML pages', async () => {
  const sitemap = await readFile(path.join(distRoot, 'sitemap-0.xml'), 'utf8')
  const sitemapIndex = await readFile(path.join(distRoot, 'sitemap-index.xml'), 'utf8')
  const locations = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((match) => match[1])
  const expectedLocations = locales.flatMap((locale) =>
    pages.map((page) => getCanonicalUrl(locale, page))
  )

  assert.deepEqual(new Set(locations), new Set(expectedLocations))
  assert.equal(locations.length, 9)
  assert.match(sitemapIndex, /sitemap-0\.xml/)
  assert.doesNotMatch(sitemap, /404|workers\.dev|updates\.xml|<priority>|<changefreq>|<lastmod>/)

  for (const languageTag of locales.map((locale) => localeConfig[locale].languageTag)) {
    assert.match(sitemap, new RegExp(`hreflang="${languageTag}"`))
  }
})

test('404 outputs are noindex and never canonicalize to the home page', async () => {
  const outputs = [
    '404.html',
    'en/404.html',
    'ru/404.html',
    'en/404/index.html',
    'ru/404/index.html'
  ]

  for (const output of outputs) {
    const html = await readFile(path.join(distRoot, output), 'utf8')

    assert.equal(getMetaContent(html, 'name', 'robots'), 'noindex,follow')
    assert.equal(findTag(html, 'link', { rel: 'canonical' }), undefined)
    assert.equal(findTag(html, 'meta', { property: 'og:url' }), undefined)
    assert.doesNotMatch(html, /application\/ld\+json/)
  }
})

test('crawler and cache policies protect discovery and static assets', async () => {
  const robots = await readFile(path.join(distRoot, 'robots.txt'), 'utf8')
  const headers = await readFile(path.join(distRoot, '_headers'), 'utf8')
  const llms = await readFile(path.join(distRoot, 'llms.txt'), 'utf8')
  const blockedCrawlers = [
    'GPTBot',
    'ClaudeBot',
    'Google-Extended',
    'Amazonbot',
    'Applebot-Extended',
    'Bytespider',
    'CCBot',
    'meta-externalagent'
  ]

  assert.match(robots, /Content-signal: search=yes, ai-input=yes, ai-train=no, use=reference/i)
  assert.match(robots, /Sitemap: https:\/\/poleparkla\.ee\/sitemap-index\.xml/)

  for (const crawler of blockedCrawlers) {
    assert.match(robots, new RegExp(`User-agent: ${crawler}\\nDisallow: /`))
  }

  for (const allowedCrawler of ['OAI-SearchBot', 'Claude-SearchBot', 'PerplexityBot']) {
    assert.doesNotMatch(robots, new RegExp(`User-agent: ${allowedCrawler}\\nDisallow: /`))
  }

  assert.match(headers, /Content-Signal: search=yes, ai-input=yes, ai-train=no, use=reference/)
  assert.match(
    headers,
    /\/_astro\/\*\n\s+Cache-Control: public, max-age=31536000, immutable/
  )
  assert.match(
    headers,
    /\/screenshots\/\*\n\s+Cache-Control: public, max-age=86400, must-revalidate/
  )
  assert.doesNotMatch(headers.split('\n\n')[0] ?? '', /Cache-Control/)
  assert.match(headers, /workers\.dev\/\*\n\s+X-Robots-Tag: noindex, nofollow/)
  assert.match(llms, /Pole parkla does not send reports/)
  assert.match(llms, /Search indexing and real-time AI input are allowed/)

  for (const locale of locales) {
    for (const page of pages) {
      assert.ok(llms.includes(getCanonicalUrl(locale, page)))
    }
  }
})

test('localized RSS feeds expose only non-draft releases', async () => {
  for (const locale of locales) {
    const localeData = localeConfig[locale]
    const feedPath = path.join(distRoot, localeData.prefix, 'updates.xml')
    const feed = await readFile(feedPath, 'utf8')
    const updatesDirectory = path.join(siteRoot, 'src/data/updates', locale)
    const updateFiles = (await readdir(updatesDirectory)).filter((file) => file.endsWith('.md'))

    assert.match(feed, new RegExp(`<language>${localeData.languageTag}</language>`))

    for (const updateFile of updateFiles) {
      const source = await readFile(path.join(updatesDirectory, updateFile), 'utf8')
      const version = path.basename(updateFile, '.md')
      const isDraft = /\ndraft:\s*true\s*\n/.test(source)

      if (isDraft) {
        assert.ok(!feed.includes(version), `Draft ${locale}/${version} leaked into RSS`)
      } else {
        assert.ok(feed.includes(version), `Published ${locale}/${version} is missing from RSS`)
        assert.ok(feed.includes(`#version-${version}`), `RSS link is missing the ${version} anchor`)
      }
    }
  }
})
