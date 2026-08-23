# Pole parkla site

- Apply every user-facing site change consistently across all supported locales (`et`, `en`, and `ru`), including localized copy, routes, metadata, and locale-specific assets.
- Capture website screenshots from the real app on a connected Android device; do not generate, redraw, or manually recreate app UI.
- Use `../app/src/androidTest/assets/street_plate_test_image.png` as the shared camera/gallery fixture. Its approved plate is `003 PUK`; do not introduce a separate fake vehicle image.
- A temporary debug-only camera-preview substitution is allowed for screenshot capture, but remove it and reinstall the clean debug APK before finishing.
- Preserve the real translucent camera tray rendered by the app (`CameraScrim`, alpha `0xB8`); do not replace it with an opaque black panel.
- Keep one screenshot set per locale under `public/screenshots/<locale>/`: `camera.webp`, `review.webp`, and `report.webp`.
- Use the approved screenshot data consistently: `Pier Dolique`, `+37256789012`, `003 PUK`, `Lastekodu tn 42, Tallinn`, and `13.08.2026 16:58`.
- Keep Google Play and source links hidden until their real public URLs are configured.

## SEO contract

- Treat visible copy, titles, descriptions, alt text, Open Graph, JSON-LD, RSS, and `llms.txt` as one localized ET/EN/RU contract.
- When adding, removing, or renaming a page, update routes, navigation, internal links, canonical and hreflang URLs, sitemap filtering, JSON-LD page type, `llms.txt`, relevant RSS links, and SEO tests. An indexable page also requires unique title/description, self-canonical, ET `x-default`, social metadata, and truthful structured data.
- When app purpose, data handling, recognition, or mail handoff changes, review the home page, FAQ, privacy pages, JSON-LD, social-card copy, and `llms.txt` together. For data changes, also update `lastUpdated`, privacy `dateModified`, the repository privacy document, and Google Play Data safety when applicable.
- Do not add crawler-only copy, `meta keywords`, unsupported schema claims, fake reviews/prices, or location doorway pages. Add external URLs, accounts, install/offer/rating fields, and `sameAs` only after verifying the public source.

## Generated assets and releases

- Treat locale `camera.webp`, `review.webp`, and `report.webp` files as sources. Recapture only affected real app screens; never edit responsive variants, social cards, the touch icon, or the image manifest independently.
- After a source screenshot, brand asset, social-card copy, or generator changes, run `pnpm run images:generate`, then `pnpm run images:check` and `pnpm run test:seo`. Repeat mobile and desktop Lighthouse when the hero, first screenshot, layout, CSS, or font changes.
- When the name, logo, or brand colors change, review favicon, touch icon, all social cards, Open Graph alt text, JSON-LD, and `llms.txt` together.
- Publish a release with the same version and `draft: false` in all locales; verify home, updates, RSS, the version anchor, and any `softwareVersion`. A domain change must update Astro `site`, URLs and alternates, sitemap, robots, RSS, social metadata, JSON-LD, `llms.txt`, Cloudflare routes/redirects, and tests.

## Crawlers and deployment

- Keep versioned `workers.dev` previews on HTTP `X-Robots-Tag: noindex, nofollow`; production must never inherit it.
- Do not weaken the approved policy without explicit user approval: search and real-time AI input are allowed, model training is denied, and reuse is limited to reference.
- Keep Cloudflare Managed `robots.txt` disabled while `public/robots.txt` exists. After crawler-policy changes, verify search/assistant crawlers remain allowed and documented training crawlers remain blocked.
- Do not add browser analytics, cookies, or tracking scripts as SEO work without a separate product decision and privacy update.

## Required verification

- After every site change, run `pnpm test:typecheck`, `pnpm run build`, and `pnpm run test:seo` from `site/`.
- After route changes, verify sitemap, canonical/hreflang, redirects, and a real 404. After metadata/assets changes, inspect built HTML and real share previews when a deployment is reachable.
- After preview deploy, verify HTTP `noindex`. After production deploy, verify its absence plus the canonical host, robots, sitemap, RSS, JSON-LD, and social-image responses.
- Do not report SEO work complete when an applicable check was skipped; name every unverified external or browser check and why.
