# Pole parkla website

Static Astro website for <https://poleparkla.ee>. It contains the public app landing page, localized release notes, and the public privacy policy required by Google Play.

## Local development

```sh
pnpm install
pnpm dev
pnpm test:typecheck
pnpm run images:check
pnpm build
pnpm run test:seo
pnpm preview
```

`pnpm preview` builds the site and serves the generated `dist/` directory through Wrangler.

## Localized routes

Estonian is the default locale and has no URL prefix. English and Russian use `/en/` and `/ru/`.

| Page | Estonian | English | Russian |
| --- | --- | --- | --- |
| Home | `/` | `/en/` | `/ru/` |
| Privacy | `/privacy/` | `/en/privacy/` | `/ru/privacy/` |
| Updates | `/updates/` | `/en/updates/` | `/ru/updates/` |

The language switcher preserves the current page.

## Publishing a release note

Add one Markdown file for every locale using the same version as the filename:

```text
src/data/updates/et/1.0.0.md
src/data/updates/en/1.0.0.md
src/data/updates/ru/1.0.0.md
```

Required frontmatter:

```yaml
---
version: 1.0.0
releasedAt: 2026-09-01
title: First public release
highlights:
  - Add one to three photos
  - Review the location and vehicle details
  - Open a prepared Estonian report in a mail app
draft: false
---
```

Published versions must exist in all three locales. The build fails when a non-draft translation is missing. The newest non-draft release is displayed on the home page.

## Store and source links

External links live in `src/config.ts`. `googlePlay` and `sourceUrl` are `null` until the public destinations exist, so the site does not render placeholder links.

When the Google Play listing is public:

1. Download unmodified localized badges from the official Google Play badge generator.
2. Store them under `public/google-play/`.
3. Configure the listing URL and the ET, EN, and RU badge paths in `src/config.ts`.

## Screenshots

The site expects three real Android app screenshots per locale under `public/screenshots/<locale>/`:

- `camera.webp`
- `review.webp`
- `report.webp`

Use only approved demonstration values and remove unapproved personal data. Do not commit image-generated UI mockups.

The committed screenshots use UI captured from a connected Android device. Camera and report imagery comes from the Android instrumentation fixture at `../app/src/androidTest/assets/street_plate_test_image.png`, whose main plate reads `003 PUK`. No generated interface elements are included.

The three locale source screenshots generate responsive `420w` and `600w` WebP variants, localized `1200x630` social cards, and the Apple touch icon. After changing a source screenshot, brand asset, card text, or generation script, run:

```sh
pnpm run images:generate
pnpm run images:check
```

Do not edit generated image outputs or `scripts/site-images-manifest.json` by hand. The production build checks that generated files still match their sources and generator.

## SEO and machine discovery

The site publishes these stable discovery endpoints:

- `/robots.txt` for classic and AI crawler policy;
- `/llms.txt` as a compact agent-oriented site index;
- `/sitemap-index.xml` for the nine localized canonical HTML pages;
- `/updates.xml`, `/en/updates.xml`, and `/ru/updates.xml` for localized public release feeds.

Search indexing and real-time AI input are allowed. Model-training crawlers are blocked in the repository policy. Versioned `workers.dev` preview URLs receive `X-Robots-Tag: noindex, nofollow` through `public/_headers`; the production domain must not receive that header.

`pnpm run test:seo` builds the site and verifies canonical URLs, hreflang, Open Graph, X/Twitter metadata, JSON-LD, sitemap, 404 behavior in generated HTML, crawler files, RSS draft filtering, and responsive image references.

After connecting the production Cloudflare zone, keep Managed `robots.txt` disabled, allow AI Search and AI Assistant crawlers in AI Crawl Control, block documented model-training crawlers, and enable Crawler Hints. Enable Markdown for Agents only if the existing plan includes it; do not upgrade solely for that feature.

## Privacy content

The public translations are stored in `src/data/privacy/`. Review them together with the app disclosures and the repository-level `../PRIVACY.md` whenever app data handling changes. Before a Google Play submission, confirm that the developer identity, privacy policy, and Data safety declaration agree.

## Cloudflare Workers Builds

Create or connect a Worker named `pole-parkla-site` and use these settings:

| Setting | Value |
| --- | --- |
| Production branch | `main` |
| Root directory | `site` |
| Build command | `pnpm run build` |
| Deploy command | `pnpm exec wrangler deploy` |
| Non-production deploy command | `pnpm exec wrangler versions upload` |

Attach `poleparkla.ee` as the custom domain and create a Cloudflare redirect from `www.poleparkla.ee` to the apex domain. No SSR adapter or Worker script is required; Wrangler uploads `dist/` as static assets.
