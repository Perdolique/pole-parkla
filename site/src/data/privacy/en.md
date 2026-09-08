---
title: Privacy Policy
description: How Pole parkla handles photos, location, reports, mail handoff, and Android-only optional cloud recognition data.
lastUpdated: 2026-08-28
---

## Controller and contact

Pole parkla is developed and operated by [**Perdolique**](https://perd.dev). Questions about privacy or this policy can be sent to [hello@poleparkla.ee](mailto:hello@poleparkla.ee).

This policy covers the Pole parkla Android and iPhone applications and the website at `poleparkla.ee`. Pole parkla has no user accounts, advertising, analytics, crash-reporting SDK, or background synchronisation. Android and iPhone histories are local and independent.

## Data stored on the device

The app can store the reporter's name and phone number, the selected report recipient, reports, custom problem templates, photos, location and time information, and plate-recognition observations. An observation can include the recognised value, the source photo, technical confidence values, and the coordinates of a plate crop inside a photo.

This data remains in the app's private storage until the user deletes an individual report or chooses **Delete all local data**. Android backup and device-transfer extraction are disabled. On iPhone, the reporter profile and recipient are kept in a protected file alongside the SwiftData store, original photos, and temporary copies; all use complete file protection and are excluded from device backup. Only nonsensitive UI settings use the system settings store.

On Android, a bearer token configured for optional cloud recognition is bound to its HTTPS origin and encrypted with AES-GCM using a non-exportable Android Keystore key. Changing the origin deletes the prior token so it cannot be sent to another origin. Provider API keys are not stored in the app. iPhone has no Worker token, cloud-provider setting, or cloud upload path.

## Processing on the device

Pole parkla uses bundled ML Kit text recognition on Android and Apple Vision on iPhone. A YOLOv9-T plate detector and CCT-S plate recogniser run locally through ONNX Runtime on both platforms. Photos are not uploaded for this processing.

The app reads photo time and GPS metadata after the user selects a photo. Location is requested only while the app is open and the user is creating or editing a report. The user can deny location permission and enter the place and time manually.

## Location, addresses, and maps

When a report receives coordinates from foreground location, photo metadata, or a map selection, Pole parkla converts the point locally from WGS84 to L-EST97 and sends the exact easting and northing over HTTPS to Maa- ja Ruumiamet's [In-AKS address service](https://geoportaal.maaamet.ee/est/teenused/integreeritav-aadressiotsing-in-ads-p504.html). The purpose is to suggest nearby streets and buildings within a radius derived from the reported location accuracy.

Address candidates are kept in memory. Only an address that the user explicitly confirms is stored in the report. A late service response cannot replace a coordinate or address edited while the request was running. Photos, registration numbers, reporter details, the recipient, and letter text are not sent to In-AKS.

Opening **Refine on map** loads an OpenFreeMap style and map tiles based on OpenStreetMap data. Requests to [OpenFreeMap](https://openfreemap.org/) expose the device IP address and the viewed map area to that service. Tapping the map sends the selected coordinates to In-AKS for nearby address suggestions. The map can be skipped and the address can always be entered manually.

## Android-only optional cloud recognition

Cloud recognition is available only in the Android app. It starts only after the user presses a provider-specific button and accepts the disclosure for that provider. Android creates a new EXIF-free JPEG from the primary photo, limits its longest side to 2048 pixels and its size to 1 MB, and sends only:

- the selected provider, either Workers AI or OpenAI;
- the derived primary JPEG.

The reporter profile, recipient, address, coordinates, other photos, and letter text are not included. Pole parkla does not automatically retry a request through another provider.

The companion Cloudflare Worker is stateless and does not write images to D1, KV, R2, queues, or another application store. AI Gateway logging and caching are disabled for these requests. Automatic Worker invocation logs and traces are disabled; custom failure logs do not contain images or model prompts.

The selected AI provider still processes the submitted image under its own terms and account controls. See [Workers AI data usage](https://developers.cloudflare.com/workers-ai/platform/data-usage/) and [OpenAI API data controls](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint). For OpenAI, `store: false` does not by itself disable abuse-monitoring retention. Zero Data Retention or Modified Abuse Monitoring is an OpenAI account setting that Pole parkla cannot verify. iPhone performs registration-number recognition only on the device and never sends photos to the Worker or either AI provider.

## Mail handoff

Pole parkla does not send reports itself. It generates a recipient, subject, body, and temporary attachment copies, then opens the mail app selected by the user. That external app controls whether the message is edited, sent, or discarded.

Attachment copies are limited to 2 MB and a 2560-pixel longest side per photo. A photo already within both limits is copied unchanged and can therefore retain all metadata present in the original file. Recompressed copies retain metadata only within a 256 KB budget, prioritising GPS coordinates and capture dates. Android grants the selected mail app temporary read access through `FileProvider`. iPhone first opens the system MessageUI composer; if it is unavailable, the app opens a share sheet and asks the user to confirm that a mail draft opened. Pole parkla records only the handoff and cannot determine whether the message was sent. iPhone removes the temporary attachment copies when the composer or handoff confirmation returns.

## Android-only Google Play review

After the first completed mail handoff, Pole parkla may ask Google Play to display its native in-app review card when the user returns to the app. Google Play decides whether the card is shown and processes any rating or review text. Pole parkla cannot read the rating or review and stores only a local setting that prevents another request.

## Retention and deletion

Deleting a photo or report also deletes its stored originals, plate observations, and related temporary copies. **Delete all local data** cancels unfinished writes and address requests, then removes reports, photos, custom templates, the reporter profile, settings, locale choice, temporary files, in-memory address suggestions, and the MapLibre ambient tile cache. On Android it also cancels cloud recognition and removes the encrypted cloud token.

Data sent to In-AKS, OpenFreeMap, an AI provider, a mail app, Google Play, or another external service is governed by that service's retention and deletion rules after it leaves the device.

## Website and email

The Pole parkla website is generated as static HTML and served through Cloudflare. The site code does not add analytics, cookies, forms, advertising, or browser storage. Cloudflare necessarily receives network request data, such as an IP address, to deliver the site and applies its own [Privacy Policy](https://www.cloudflare.com/privacypolicy/).

The contact link opens the visitor's mail application; the website does not submit a form. Messages sent to `hello@poleparkla.ee` are processed by an email service provider. Correspondence is retained only while it is needed to answer the inquiry, maintain necessary records, or meet applicable legal obligations. A sender can request deletion by writing to the same address.

## Security and user choices

Network requests made by Pole parkla use HTTPS. Local app data is stored in the application's private storage. Android backup is disabled and its optional cloud token is protected by Android Keystore encryption. iPhone data uses complete file protection without backup and stores no cloud-recognition token. No method of storage or transmission can provide an absolute security guarantee.

The user can deny location access, avoid the map, avoid Android cloud recognition, edit every generated report before handoff, discard the mail draft, delete individual reports, or erase all local app data. Privacy questions and requests concerning email correspondence can be sent to [hello@poleparkla.ee](mailto:hello@poleparkla.ee).

## Changes to this policy

This page shows its last-updated date above. Material changes to data handling will be reflected here and in the app's privacy information before the changed behaviour is released.
