---
title: Privacy Policy
description: How Pole parkla handles photos, location, reports, mail handoff, and optional cloud recognition data.
lastUpdated: 2026-08-23
---

## Controller and contact

Pole parkla is developed and operated by **Perdolique**. Questions about privacy or this policy can be sent to [hello@poleparkla.ee](mailto:hello@poleparkla.ee).

This policy covers the Pole parkla Android application and the website at `poleparkla.ee`. Pole parkla has no user accounts, advertising, analytics, crash-reporting SDK, or background synchronisation.

## Data stored on the device

The app can store the reporter's name and phone number, the selected report recipient, reports, custom problem templates, photos, location and time information, and plate-recognition observations. An observation can include the recognised value, the source photo, technical confidence values, and the coordinates of a plate crop inside a photo.

This data remains in the app's private storage until the user deletes an individual report or chooses **Delete all local data**. Android backup and device-transfer extraction are disabled for app data.

If the user configures a bearer token for optional cloud recognition, the token is encrypted with AES-GCM using a non-exportable Android Keystore key. It is bound to the configured HTTPS origin. Changing that origin clears the previous consent and prevents the token from being sent to another origin. Provider API keys are not stored in the app.

## Processing on the device

Pole parkla runs bundled ML Kit text recognition, a YOLOv9-T plate detector, and a CCT-S plate recogniser on selected original photos. These models run locally through ONNX Runtime. Photos are not uploaded for this processing.

The app reads photo time and GPS metadata after the user selects a photo. Location is requested only while the app is open and the user is creating or editing a report. The user can deny location permission and enter the place and time manually.

## Location, addresses, and maps

When a report receives coordinates from foreground location, photo metadata, or a map selection, Pole parkla converts the point locally from WGS84 to L-EST97 and sends the exact easting and northing over HTTPS to Maa- ja Ruumiamet's [In-AKS address service](https://geoportaal.maaamet.ee/est/teenused/integreeritav-aadressiotsing-in-ads-p504.html). The purpose is to suggest nearby streets and buildings within a radius derived from the reported location accuracy.

Address candidates are kept in memory. Only an address that the user explicitly confirms is stored in the report. A late service response cannot replace a coordinate or address edited while the request was running. Photos, registration numbers, reporter details, the recipient, and letter text are not sent to In-AKS.

Opening **Refine on map** loads an OpenFreeMap style and map tiles based on OpenStreetMap data. Requests to [OpenFreeMap](https://openfreemap.org/) expose the device IP address and the viewed map area to that service. Tapping the map sends the selected coordinates to In-AKS for nearby address suggestions. The map can be skipped and the address can always be entered manually.

## Optional cloud recognition

Cloud recognition starts only after the user presses a provider-specific button and accepts the disclosure for that provider. Pole parkla creates a new EXIF-free JPEG from the primary photo, limits its longest side to 2048 pixels and its size to 1 MB, and sends only:

- the selected provider, either Workers AI or OpenAI;
- the derived primary JPEG.

The reporter profile, recipient, address, coordinates, other photos, and letter text are not included. Pole parkla does not automatically retry a request through another provider.

The companion Cloudflare Worker is stateless and does not write images to D1, KV, R2, queues, or another application store. AI Gateway logging and caching are disabled for these requests. Automatic Worker invocation logs and traces are disabled; custom failure logs do not contain images or model prompts.

The selected AI provider still processes the submitted image under its own terms and account controls. See [Workers AI data usage](https://developers.cloudflare.com/workers-ai/platform/data-usage/) and [OpenAI API data controls](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint). For OpenAI, `store: false` does not by itself disable abuse-monitoring retention. Zero Data Retention or Modified Abuse Monitoring is an OpenAI account setting that Pole parkla cannot verify.

## Mail handoff

Pole parkla does not send reports itself. It generates a recipient, subject, body, and temporary attachment copies, then opens the mail app selected by the user. That external app controls whether the message is edited, sent, or discarded.

Attachment copies are limited to 2 MB and a 2560-pixel longest side per photo. A photo already within both limits is copied unchanged and can therefore retain all metadata present in the original file. When an oversized JPEG is recompressed, its EXIF/APP1 segments are retained in full within a 256 KB metadata budget. If that budget is exceeded, or if another supported image format is converted, the copy retains only GPS coordinates, capture dates, display orientation, camera make, and camera model when available. Android grants the selected mail app temporary read access through `FileProvider`. Pole parkla records only that the mail app was opened; it cannot determine whether the message was sent.

## Google Play review

After the first completed mail handoff, Pole parkla may ask Google Play to display its native in-app review card when the user returns to the app. Google Play decides whether the card is shown and processes any rating or review text. Pole parkla cannot read the rating or review and stores only a local setting that prevents another request.

## Retention and deletion

Deleting a report also deletes its stored photos, plate observations, and temporary attachment copies. **Delete all local data** cancels unfinished writes and address requests, then removes reports, photos, custom templates, the reporter profile, settings, locale choice, encrypted cloud token, temporary files, in-memory address suggestions, and the MapLibre ambient tile cache.

Data sent to In-AKS, OpenFreeMap, an AI provider, a mail app, Google Play, or another external service is governed by that service's retention and deletion rules after it leaves the device.

## Website and email

The Pole parkla website is generated as static HTML and served through Cloudflare. The site code does not add analytics, cookies, forms, advertising, or browser storage. Cloudflare necessarily receives network request data, such as an IP address, to deliver the site and applies its own [Privacy Policy](https://www.cloudflare.com/privacypolicy/).

The contact link opens the visitor's mail application; the website does not submit a form. Messages sent to `hello@poleparkla.ee` are processed by an email service provider. Correspondence is retained only while it is needed to answer the inquiry, maintain necessary records, or meet applicable legal obligations. A sender can request deletion by writing to the same address.

## Security and user choices

Network requests made by Pole parkla use HTTPS. Local app data is stored in the application's private storage, Android backup is disabled, and the optional cloud token is protected by Android Keystore encryption. No method of storage or transmission can provide an absolute security guarantee.

The user can deny location access, avoid the map, avoid cloud recognition, edit every generated report before handoff, discard the mail draft, delete individual reports, or erase all local app data. Privacy questions and requests concerning email correspondence can be sent to [hello@poleparkla.ee](mailto:hello@poleparkla.ee).

## Changes to this policy

This page shows its last-updated date above. Material changes to data handling will be reflected here and in the app's privacy information before the changed behaviour is released.
