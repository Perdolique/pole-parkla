# Pole parkla! privacy notes

Pole parkla is designed for personal sideload use. It has no account system, analytics, advertising, crash-reporting SDK or background synchronization.

The first onboarding step states that continuing accepts this policy and opens an in-app copy of its material terms. The same policy remains available under **Privacy and data** in Settings.

## Data stored on the device

The app stores the reporter's name and phone number, the configured recipient, reports, custom problem templates, report photos and per-photo plate observations. A local detector observation can include normalized plate-crop coordinates plus technical confidence and relative-area values. Reports and photos remain until the user deletes an individual report or selects **Delete all local data**. Deleting a report or photo also deletes its observations. **Delete all local data** also cancels active address requests and clears in-memory address suggestions, temporary attachment copies and MapLibre's ambient tile cache before reporting success.

Photos are held in the app's private internal directory. Android backup and device-transfer extraction are disabled. A configured Worker bearer token is encrypted with AES-GCM using a non-exportable Android Keystore key and is bound to the HTTPS origin for which it was entered. Changing the Worker URL clears prior consent and prevents that token from being sent to a different origin. Provider API keys are never stored in the Android app.

## On-device processing

Bundled ML Kit OCR, a YOLOv9-T plate detector and a CCT-S plate recognizer process every selected original photo locally and retain the result's photo provenance. Equal normalized plate values are aggregated without synthesizing characters across conflicting results. The plate models ship inside the app and run through ONNX Runtime; photos are not uploaded for this processing.

Location is requested only while Pole parkla is open and the user is capturing or editing a report. The app stores the coordinate, reported accuracy and timestamp associated with a draft. Gallery EXIF coordinates can be used after the user selects a photo: the first photo can initialise a new draft, and the editor also offers an explicit **From photo** action.

After a new draft receives a foreground or EXIF point, Pole parkla converts it locally from WGS84 to L-EST97 and automatically sends the exact easting and northing over HTTPS to Maa- ja Ruumiamet's [In-AKS address service](https://geoportaal.maaamet.ee/est/teenused/integreeritav-aadressiotsing-in-ads-p504.html). Opening the place step with stored coordinates refreshes the suggestions; **Current**, **From photo** and map point selection resolve their selected point in the same way. The request asks for current nearby streets and buildings within a 30–100 metre radius derived from the reported accuracy. Successful address candidates are kept only in ViewModel memory; only an address explicitly committed with **Confirm place** is stored in the report. A late response cannot overwrite coordinates or an address edited while the request was running. Choosing a candidate changes the buffered address text but does not move the original coordinate.

Opening **Refine on map** loads a native MapLibre map using the [OpenFreeMap](https://openfreemap.org/) Positron style and tiles based on OpenStreetMap data. These tile requests expose the device IP address and viewed map area to OpenFreeMap. MapLibre shows the required map attribution. The map has no address search, browser geolocation or automatic user-location control.

Tapping the map creates a tentative WGS84 point and sends its exact coordinates to In-AKS for nearby address suggestions. A successful response stays in memory and returns its candidates to the place step. If In-AKS has no result or is unavailable, the user can keep the existing address text and explicitly use only the selected point. Only a map tap can replace the buffered coordinates, and they are stored only after **Use this address** or **Use this point** and then **Confirm place**. Photos, registration number, reporter profile, recipient and letter text are not sent to OpenFreeMap or In-AKS.

The user can deny location access, skip the map and enter an address manually. In-AKS lookup and the public OpenFreeMap instance are best effort and have no SLA. A map loading failure leaves the current editor values untouched. An address lookup failure keeps a **Current** or **From photo** coordinate available without erasing the manually entered address; a map coordinate changes only after the user explicitly chooses **Use this point**. Address, coordinate and time remain editable. See the official [In-AKS terms of use](https://geoportaal.maaruum.ee/docs/aadress/In-AKS_kasutustingimused.pdf) and [OpenFreeMap service description](https://openfreemap.org/) for the external services' terms.

## Optional cloud recognition

Cloud recognition runs only after pressing a provider-specific button and accepting that provider's consent. The app creates a new EXIF-free JPEG from the primary photo, limits its longest side to 2048 pixels and its size to 1 MB, and sends only:

- `provider`, either `workers_ai` or `openai`;
- the derived primary JPEG.

The reporter profile, recipient, address, coordinates, other photos and letter text are not included. There is no automatic retry through another provider. The companion Worker is stateless and does not write images to D1, KV, R2, queues or another application store. It disables AI Gateway logging and caching for these calls. Automatic Worker invocation logs and traces are disabled; custom technical logs are emitted only for failures and contain no image or model prompt. Provider processing is still governed by the selected provider and account policy: see [Workers AI data usage](https://developers.cloudflare.com/workers-ai/platform/data-usage/) and [OpenAI API data controls](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint). In particular, OpenAI `store: false` does not by itself disable abuse-monitoring retention; Zero Data Retention or Modified Abuse Monitoring is an account-side control that Pole parkla cannot verify.

## Mail handoff

Pole parkla does not send mail itself. It always generates the saved subject and body from the currently confirmed report fields; the in-app summary shows the subject but not the full body. Before handoff, it creates attachment copies limited to 2 MB and a 2560-pixel longest side per photo; the stored report photos are not modified. A photo already within both limits is copied unchanged. When an oversized JPEG must be recompressed, its EXIF segments are retained in full within a 256 KB metadata budget. If that budget is exceeded, or if an oversized HEIC, PNG or another supported non-JPEG image must be converted, the copy retains GPS coordinates, capture dates, display orientation, camera make and camera model when available. Android grants the selected mail app temporary read access to those copies and opens it with the recipient, subject and body. Attachment copies are removed when their report or all local data is deleted. The external app controls whether the message is edited, sent or discarded. Pole parkla records only that the mail app was opened.

## Google Play review

After the first completed mail handoff, Pole parkla may ask Google Play to show its native in-app review card when the user returns to the app. Google Play decides whether the card is shown and handles any rating and optional review text under the Google Play terms. Pole parkla cannot read whether the card was shown, dismissed or submitted and does not store the rating or review text. It stores only a local setting that prevents another review request; **Delete all local data** removes that setting.

## Safety

Do not take photos while riding. Stop safely first. Pole parkla is not an emergency service; call 112 when urgent assistance is needed.
