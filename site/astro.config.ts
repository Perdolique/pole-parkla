import { copyFile } from 'node:fs/promises'
import type { AstroIntegration } from 'astro'
import { defineConfig } from 'astro/config'
import sitemap from '@astrojs/sitemap'

const localized404Pages: AstroIntegration = {
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

  build: {
    inlineStylesheets: 'always'
  },

  i18n: {
    locales: ['et', 'en', 'ru'],
    defaultLocale: 'et',

    routing: {
      prefixDefaultLocale: false
    }
  },

  integrations: [
    sitemap({
      filter: (page) => {
        const pathname = new URL(page).pathname

        return !pathname.includes('/404/') && !pathname.endsWith('.xml')
      },

      i18n: {
        defaultLocale: 'et',

        locales: {
          et: 'et-EE',
          en: 'en-EE',
          ru: 'ru-EE'
        }
      }
    }),
    localized404Pages
  ],

  devToolbar: {
    enabled: false
  },

  scopedStyleStrategy: 'class'
})
