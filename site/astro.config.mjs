// @ts-check
import { copyFile } from 'node:fs/promises'
import { defineConfig } from 'astro/config'
import sitemap from '@astrojs/sitemap'

/** @type {import('astro').AstroIntegration} */
const localized404Pages = {
  name: 'localized-404-pages',
  hooks: {
    'astro:build:done': async ({ dir }) => {
      await Promise.all(
        ['en', 'ru'].map((locale) =>
          copyFile(new URL(`${locale}/404/index.html`, dir), new URL(`${locale}/404.html`, dir))
        )
      )
    }
  }
}

export default defineConfig({
  site: 'https://poleparkla.ee',
  output: 'static',

  i18n: {
    locales: ['et', 'en', 'ru'],
    defaultLocale: 'et',

    routing: {
      prefixDefaultLocale: false
    }
  },

  integrations: [sitemap(), localized404Pages],

  devToolbar: {
    enabled: false
  },

  scopedStyleStrategy: 'class'
})
