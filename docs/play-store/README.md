# Google Play store assets

This directory contains the ready-to-upload graphics for the default Google Play store listing.

## Layout

- `common/app-icon-512.png`: shared 512 x 512 app icon.
- `et/`: default Estonian feature graphic and phone screenshots.
- `en-US/`: English (United States) feature graphic and phone screenshots.
- `ru-RU/`: Russian feature graphic and phone screenshots.

Each locale contains a 1024 x 500 JPEG feature graphic and three 1008 x 2244 PNG phone screenshots in display order: camera, vehicle review, and report summary.

The ready assets are derived from the tracked brand sources in `docs/brand/`, the localized social images in `site/public/social/`, and the real localized app screenshots in `site/public/screenshots/`.

## Google Play declarations

The app icon and localized feature graphics use the logo derived from the tracked ImageGen source and should be declared as created or edited using AI. The phone screenshots are captures of the real app and should not receive an AI label.

The report-summary screenshots contain visible example sender and recipient details. Review those values before reusing the screenshots for a future release.

Release bundles, signing keys, and signing credentials must not be stored in this directory or committed to Git.
