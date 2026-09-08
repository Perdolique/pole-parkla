# Pole parkla!

Pole parkla is a private, camera-first Android and iPhone app for preparing reports about vehicles parked on cycle or pedestrian paths. Each platform keeps an independent local history. The app stores a draft after the first photo, combines offline plate evidence from every photo, requires explicit verification of the vehicle and place, and opens a pre-filled Estonian letter with one to three attachments in a mail app.

Android package and iOS bundle ID: `com.perdolique.poleparkla`.

Android 8.0+ on ARM64 (`minSdk 26`, target and compile API 36); iOS 18+ on iPhone. Both apps are named **Pole parkla!**.

## MVP flow

1. Stop safely and open Pole parkla.
2. Take one to three photos or select them with the platform photo picker.
3. Check the aggregated registration-number candidates and plate crops from every photo, optionally adjust make/model, and explicitly confirm the vehicle.
4. Check the suggested address, coordinates and time, then explicitly confirm the place.
5. Choose one built-in or saved custom problem with one tap:
   - vehicle parked on a cycle path;
   - vehicle parked on a pedestrian path.
6. Review the structured summary: photos, vehicle, place and time, problem, recipient, sender and generated subject. The full letter body is not shown or edited in Pole parkla.
7. Open the selected mail app with the recipient, generated subject/body and photos filled in. Make any free-form letter edits there.

On-device OCR and the bundled plate model inspect all stored photos independently. Equal normalized values are aggregated without inventing characters between conflicting candidates. Android alone offers explicit optional cloud recognition and sends only the primary photo; iOS recognition is entirely on-device.

The app records `HANDED_OFF_TO_MAIL` after a mail draft is handed off. It never claims that the message was sent. On iOS, every non-technical MessageUI close result counts only as a handoff; the share-sheet fallback requires an explicit confirmation after returning.

## What is included

Shared product behavior is implemented natively on each platform; there is no shared Kotlin runtime layer.

### Android

- Kotlin and Jetpack Compose with a custom Nordic field UI system; Material 3 is limited to Android infrastructure primitives.
- Navigation Compose, ViewModel, coroutines and StateFlow.
- Room for reports, photos, per-photo plate observations and custom problem templates.
- Preferences DataStore for locale, profile and app preferences.
- AES-GCM Android Keystore protection for the personal Worker bearer token.
- CameraX with back/front fallback, Photo Picker, foreground fused location and nearby address suggestions from the official In-AKS service.
- An optional full-screen native MapLibre map with OpenFreeMap Positron tiles for explicitly refining the evidence point; In-AKS resolves tapped points and choosing an address from a list does not move the recorded coordinates.
- Bundled ML Kit Latin text recognition for per-photo offline plate candidates.
- Bundled YOLOv9-T plate detection and CCT-S global plate recognition over every original stored photo through ONNX Runtime, including normalized crop coordinates and deterministic cross-photo aggregation.
- Optional Workers AI and OpenAI recognition through the companion [Cloudflare Worker](worker/README.md).
- Mail attachment copies capped at 2 MB and 2560 px per photo, with bounded JPEG EXIF retention and essential evidence metadata fallback, plus `FileProvider` and `ACTION_SEND_MULTIPLE` handoff to a remembered compatible mail component.
- Russian, English and Estonian UI; report letters are always generated in Estonian.
- No analytics, ads, crash reporting, accounts, background location or background synchronization.

### iOS

- Swift 6, SwiftUI, strict concurrency and SwiftData in a standalone Xcode project under `ios/`; iPhone-only deployment target iOS 18.
- AVFoundation camera capture with back/front fallback and pinch zoom, PhotosPicker, ImageIO EXIF extraction and foreground CoreLocation.
- Apple Vision accurate text recognition and the same repository-owned YOLOv9-T and CCT-S ONNX models through ONNX Runtime `1.24.2` on the CPU execution provider.
- A small `UIViewRepresentable` around MapLibre Native `6.28.0`, using the same OpenFreeMap Positron style and native attribution.
- UserDefaults settings with no cloud credentials or cloud-recognition configuration.
- MessageUI mail composition with a share-sheet fallback, iOS 18 recipient metadata and honest `HANDED_OFF_TO_MAIL` semantics.
- English, Estonian and Russian String Catalog localization; letters and built-in violation descriptions remain Estonian.
- No accounts, iCloud synchronization, analytics, ads, background location or background work.

Both native apps link the same ONNX binaries and MIT license directly from `app/src/main/assets`; the iOS project does not duplicate those 12+ MB repository assets.

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

## iOS build

Requirements:

- Xcode 26 or later with an iOS 18+ Simulator runtime;
- an iPhone running iOS 18+ for final camera, location, map, on-device recognition and mail smoke tests;
- an Apple Personal Team or paid development team for device signing.

Open `ios/PoleParkla.xcodeproj`. The project contains `PoleParkla`, `PoleParklaTests` and `PoleParklaUITests`; SPM dependencies are pinned in `Package.resolved`. A command-line simulator run is:

```sh
xcodebuild build \
  -project ios/PoleParkla.xcodeproj \
  -scheme PoleParkla \
  -destination 'platform=iOS Simulator,name=iPhone 16'

xcodebuild test \
  -project ios/PoleParkla.xcodeproj \
  -scheme PoleParkla \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

For a physical iPhone, select a Personal Team in Xcode, enable Developer Mode on the phone and trust the Mac. Do not press **Send** during mail-flow validation: verify the populated draft and remove it. A free Personal Team build normally needs to be re-signed and reinstalled after about seven days; reinstall without deleting the app so its local container is preserved.

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

Pole parkla requests only camera, foreground location and network access. Gallery access uses the platform photo picker and needs no broad storage permission. Photos stay under each app's private directory. Android backup and cleartext HTTP are disabled; iOS excludes its SwiftData/photo directory from device backup and protects files while the device is locked.

After a new draft receives foreground or gallery EXIF coordinates, Pole parkla automatically sends the exact point over HTTPS to Maa- ja Ruumiamet's [In-AKS service](https://geoportaal.maaamet.ee/est/teenused/integreeritav-aadressiotsing-in-ads-p504.html) to retrieve nearby streets and buildings. Opening the place step with stored coordinates refreshes those suggestions; **Current**, **From photo** and map point selection do the same for their selected point. Opening the optional native MapLibre map loads the [OpenFreeMap](https://openfreemap.org/) Positron style and tiles based on OpenStreetMap data; the tile requests expose the device IP address and viewed map area to that service. Photos, registration numbers, the reporter profile, recipient and letter text are not sent to either map or address service. Onboarding describes these transfers; iOS Settings repeats the summary and links to the complete localized web policy, while Android keeps the complete policy copy in the app. Location permission can be denied, the map can be skipped and the address can always be entered manually. Suggested values stay buffered until **Confirm place**. Both free services have no SLA, so network or service failure keeps manual input available and does not overwrite a later manual edit.

Deleting one report also deletes its stored photos and temporary attachment copies. **Delete all local data** cancels unfinished writes and address requests, then removes reports, photos, custom templates, profile, settings, locale choice, temporary copies, in-memory address suggestions and MapLibre's ambient tile cache. Android also cancels cloud recognition and removes its encrypted cloud token. See [PRIVACY.md](PRIVACY.md) for the complete data path.

## Repository layout

- `app/` — Android application and repository-owned model assets.
- `ios/` — native iPhone application, unit tests and UI tests.
- `worker/` — stateless Cloudflare Worker proxy for explicitly requested image recognition.
- `app/src/test/` — deterministic domain unit tests.
- `app/src/androidTest/` — Compose/instrumentation tests that require an Android device or emulator.
