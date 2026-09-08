# Pole parkla! privacy notes

Pole parkla is designed for personal Android and iPhone use. It has no account system, analytics, advertising, crash-reporting SDK, iCloud synchronization or background synchronization. Android and iOS histories are local and independent.

Android onboarding links to an in-app copy of the material terms. iOS onboarding summarizes local processing, location, In-AKS, OpenFreeMap and mail handoff. **Privacy and data** in iOS Settings repeats that summary and links to the complete localized privacy policy on `poleparkla.ee`; Android keeps the complete copy in the app.

## Data stored on the device

The app stores the reporter's name and phone number, the configured recipient, reports, custom problem templates, report photos and per-photo plate observations. A local detector observation can include normalized plate-crop coordinates plus technical confidence and relative-area values. Reports and photos remain until the user deletes an individual report or selects **Delete all local data**. Deleting a report or photo also deletes its observations. **Delete all local data** also cancels active address requests and clears in-memory address suggestions, temporary attachment copies and MapLibre's ambient tile cache before reporting success.

Photos are held in each app's private internal directory. Android backup and device-transfer extraction are disabled. On iOS, the protected reporter-profile file, SwiftData store, original photos and temporary copies use complete file protection and are excluded from device backup. Only nonsensitive UI settings are kept in `UserDefaults`.

On Android, a configured Worker bearer token is bound to the normalized HTTPS origin for which it was entered and encrypted with AES-GCM using a non-exportable Android Keystore key. Changing the Worker origin deletes the prior token so it cannot be sent to another origin. Provider API keys are not stored in the app. iOS has no Worker token, cloud provider setting or cloud upload path.

## On-device processing

Android uses bundled ML Kit OCR; iOS uses Apple Vision in accurate mode without language correction. A YOLOv9-T plate detector and CCT-S plate recognizer process every selected original photo locally on both platforms and retain the result's photo provenance. Equal normalized plate values are aggregated without synthesizing characters across conflicting results. The plate models ship with each app and run through ONNX Runtime; photos are not uploaded for this processing.

Location is requested only while Pole parkla is open and the user is capturing or editing a report. The app stores the coordinate, reported accuracy and timestamp associated with a draft. Gallery EXIF coordinates can be used after the user selects a photo: the first photo can initialise a new draft, and the editor also offers an explicit **From photo** action.

After a new draft receives a foreground or EXIF point, Pole parkla converts it locally from WGS84 to L-EST97 and automatically sends the exact easting and northing over HTTPS to Maa- ja Ruumiamet's [In-AKS address service](https://geoportaal.maaamet.ee/est/teenused/integreeritav-aadressiotsing-in-ads-p504.html). Opening the place step with stored coordinates refreshes the suggestions; **Current**, **From photo** and map point selection resolve their selected point in the same way. The request asks for current nearby streets and buildings within a 30–100 metre radius derived from the reported accuracy. Successful address candidates are kept only in ViewModel memory; only an address explicitly committed with **Confirm place** is stored in the report. A late response cannot overwrite coordinates or an address edited while the request was running. Choosing a candidate changes the buffered address text but does not move the original coordinate.

Opening **Refine on map** loads a native MapLibre map using the [OpenFreeMap](https://openfreemap.org/) Positron style and tiles based on OpenStreetMap data. These tile requests expose the device IP address and viewed map area to OpenFreeMap. MapLibre shows the required map attribution. The map has no address search, browser geolocation or automatic user-location control.

Tapping the map creates a tentative WGS84 point and sends its exact coordinates to In-AKS for nearby address suggestions. A successful response stays in memory and returns its candidates to the place step. If In-AKS has no result or is unavailable, the user can keep the existing address text and explicitly use only the selected point. Only a map tap can replace the buffered coordinates, and they are stored only after **Use this address** or **Use this point** and then **Confirm place**. Photos, registration number, reporter profile, recipient and letter text are not sent to OpenFreeMap or In-AKS.

The user can deny location access, skip the map and enter an address manually. In-AKS lookup and the public OpenFreeMap instance are best effort and have no SLA. A map loading failure leaves the current editor values untouched. An address lookup failure keeps a **Current** or **From photo** coordinate available without erasing the manually entered address; a map coordinate changes only after the user explicitly chooses **Use this point**. Address, coordinate and time remain editable. See the official [In-AKS terms of use](https://geoportaal.maaruum.ee/docs/aadress/In-AKS_kasutustingimused.pdf) and [OpenFreeMap service description](https://openfreemap.org/) for the external services' terms.

## Android-only optional cloud recognition

Cloud recognition is available only in the Android app. It runs only after pressing a provider-specific button and accepting that provider's consent. Android creates a new EXIF-free JPEG from the primary photo, limits its longest side to 2048 pixels and its size to 1 MB, and sends only:

- `provider`, either `workers_ai` or `openai`;
- the derived primary JPEG.

The reporter profile, recipient, address, coordinates, other photos and letter text are not included. There is no automatic retry through another provider. The companion Worker is stateless and does not write images to D1, KV, R2, queues or another application store. It disables AI Gateway logging and caching for these calls. Automatic Worker invocation logs and traces are disabled; custom technical logs are emitted only for failures and contain no image or model prompt. Provider processing is still governed by the selected provider and account policy: see [Workers AI data usage](https://developers.cloudflare.com/workers-ai/platform/data-usage/) and [OpenAI API data controls](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint). In particular, OpenAI `store: false` does not by itself disable abuse-monitoring retention; Zero Data Retention or Modified Abuse Monitoring is an account-side control that Pole parkla cannot verify. The iOS app performs registration-number recognition only on the device and never sends a photo to this Worker or either AI provider.

## Mail handoff

Pole parkla does not send mail itself. It always generates the saved subject and body from the currently confirmed report fields. Before handoff, it creates attachment copies limited to 2 MB and a 2560-pixel longest side per photo; the stored report photos are not modified. A photo already within both limits is copied unchanged. When an oversized image must be recompressed, its metadata is retained within a 256 KB budget, with essential GPS and capture-time metadata used when the full metadata does not fit.

Android grants the selected mail app temporary read access to those copies. iOS first uses `MFMailComposeViewController`, filling recipient, subject, body and attachments. Cancelling or saving a successfully opened composer records only `HANDED_OFF_TO_MAIL`; even the `.sent` callback is not stored as proof of delivery. If MessageUI is unavailable, iOS opens a share sheet with subject, body, files and iOS 18 recipient metadata. Third-party apps may ignore metadata, so the status changes only after the user explicitly confirms that a mail draft opened. iOS removes attachment copies when the handoff UI returns; both platforms also remove temporary copies when their photo or report is deleted or by **Delete all local data**. The external mail app controls whether a message is edited, sent or discarded.

## Android-only Google Play review

After the first completed mail handoff, Pole parkla may ask Google Play to show its native in-app review card when the user returns to the app. Google Play decides whether the card is shown and handles any rating and optional review text under the Google Play terms. Pole parkla cannot read whether the card was shown, dismissed or submitted and does not store the rating or review text. It stores only a local setting that prevents another review request; **Delete all local data** removes that setting.

## Safety

Do not take photos while riding. Stop safely first. Pole parkla is not an emergency service; call 112 when urgent assistance is needed.
