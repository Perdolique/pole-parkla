# Pole parkla

Pole parkla is a private, camera-first Android app for preparing reports about vehicles parked on cycle or pedestrian paths. It stores a local draft after the first photo, runs offline OCR, lets the reporter verify every field, and opens a pre-filled Estonian letter with one to three attachments in a chosen mail app.

Package: `com.perdolique.poleparkla`

Android 8.0+ on ARM64 (`minSdk 26`); target and compile API 36.

## MVP flow

1. Stop safely and open Pole parkla.
2. Take one to three photos or select them with Android Photo Picker.
3. Verify the registration number, location, time and one of the built-in problems:
   - vehicle parked on a cycle path;
   - vehicle parked on a pedestrian path.
4. Optionally verify vehicle make/model or use an explicitly invoked cloud provider.
5. Review the generated Estonian letter.
6. Open the selected mail app with the recipient, subject, body and photos filled in.

The app records `HANDED_OFF_TO_MAIL` after Android opens the external app. It never claims that the message was sent because Android cannot reliably prove that result.

## What is included

- Kotlin and Jetpack Compose with a custom Nordic field UI system; Material 3 is limited to Android infrastructure primitives.
- Navigation Compose, ViewModel, coroutines and StateFlow.
- Room for reports, photos and custom problem templates.
- Preferences DataStore for locale, profile and app preferences.
- AES-GCM Android Keystore protection for the personal Worker bearer token.
- CameraX with back/front fallback, Photo Picker, foreground fused location and nearby address suggestions from the official In-AKS service.
- An optional full-screen native MapLibre map with OpenFreeMap Positron tiles for explicitly refining the evidence point; In-AKS resolves tapped points and choosing an address from a list does not move the recorded coordinates.
- Bundled ML Kit Latin text recognition for offline plate candidates from every photo.
- Bundled YOLOv9-T plate detection and CCT-S global plate recognition over the original stored photos through ONNX Runtime.
- Optional Workers AI and OpenAI recognition through the companion [Cloudflare Worker](worker/README.md).
- Mail attachment copies capped at 2 MB and 2560 px per photo, with bounded JPEG EXIF retention and essential evidence metadata fallback, plus `FileProvider` and `ACTION_SEND_MULTIPLE` handoff to a remembered compatible mail component.
- Russian, English and Estonian UI; report letters are always generated in Estonian.
- No analytics, ads, crash reporting, accounts, background location or background synchronization.

## Android build

Requirements:

- Android SDK 36;
- ARM64 (`arm64-v8a`) device;
- JDK 17 for the project toolchain (a newer supported Android Studio JBR is also suitable for running Gradle);
- Android Studio or command-line SDK tools.

Do not run this project with the unrelated local Java 26 installation. On a machine where Android Studio provides the Gradle JVM, a command-line build can explicitly select it:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on an attached device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Compile the instrumentation suite with:

```sh
./gradlew assembleDebugAndroidTest
```

Run it only on a disposable emulator or dedicated test device. Android Gradle Plugin's Unified Test Platform may uninstall the app after the run and remove its local data:

```sh
./gradlew connectedDebugAndroidTest
```

CameraX behavior on back-only, front-only and camera-free hardware, device-specific Photo Picker input, fused-location freshness, the live MapLibre renderer and OpenFreeMap style, on-device ONNX performance and real mail-app attachment grants still require physical-device checks. Cloud-provider smoke tests can incur provider cost and are intentionally not part of the normal test suite.

## Release signing

Copy `keystore.properties.example` to the ignored `keystore.properties`, keep the keystore outside Git, and replace all placeholders. The release build reads:

```properties
storeFile=/absolute/path/to/pole-parkla-release.jks
storePassword=replace-me
keyAlias=pole-parkla
keyPassword=replace-me
```

Then build:

```sh
./gradlew assembleRelease
```

Without `keystore.properties`, the debug build remains available and the release variant is not signed for installation.

## Local data and permissions

Pole parkla requests only camera, foreground coarse/fine location and internet access. Gallery access uses Photo Picker and needs no broad storage permission. Photos stay under the app's private files directory; Android backup and cleartext HTTP are disabled.

After a new draft receives foreground or gallery EXIF coordinates, Pole parkla automatically sends the exact point over HTTPS to Maa- ja Ruumiamet's [In-AKS service](https://geoportaal.maaamet.ee/est/teenused/integreeritav-aadressiotsing-in-ads-p504.html) to retrieve nearby streets and buildings. Opening the location editor with stored coordinates refreshes those suggestions; **Current**, **From photo** and map point selection do the same for their selected point. Opening the optional native MapLibre map loads the [OpenFreeMap](https://openfreemap.org/) Positron style and tiles based on OpenStreetMap data; the tile requests expose the device IP address and viewed map area to that service. Photos, registration numbers, the reporter profile, recipient and letter text are not sent to either map or address service. The first onboarding step links to an in-app privacy policy describing these transfers. Location permission can be denied, the map can be skipped and the address can always be entered manually. Both free services have no SLA, so network or service failure keeps manual input available and does not overwrite a later manual edit.

Deleting one report also deletes its stored photos and temporary attachment copies. **Delete all local data** cancels unfinished writes and address requests, then removes reports, photos, custom templates, profile, settings, locale choice, temporary copies, in-memory address suggestions, MapLibre's ambient tile cache and the encrypted cloud token from the app. See [PRIVACY.md](PRIVACY.md) for the complete data path.

## Repository layout

- `app/` — Android application.
- `worker/` — stateless Cloudflare Worker proxy for explicitly requested image recognition.
- `app/src/test/` — deterministic domain unit tests.
- `app/src/androidTest/` — Compose/instrumentation tests that require an Android device or emulator.
