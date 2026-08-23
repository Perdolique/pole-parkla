import type { APIRoute, GetStaticPaths } from 'astro'
import type { LocaleRouteProps } from '../../i18n/config'
import { createUpdatesFeed } from '../../lib/rss'

export const getStaticPaths = (() =>
  (['en', 'ru'] as const).map((locale) => ({
    params: { lang: locale },
    props: { locale }
  }))) satisfies GetStaticPaths

export const GET: APIRoute = ({ props, site }) => {
  const { locale } = props as LocaleRouteProps

  return createUpdatesFeed(locale, site)
}
