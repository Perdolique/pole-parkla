# Pole parkla

Pole parkla is a private, camera-first Android app for preparing reports about vehicles parked on cycle or pedestrian paths in Estonia. It helps the user capture evidence, verify the vehicle and location details, and open a ready-to-send Estonian report in a mail app.

## Features

- Capture or select one to three photos and keep local report drafts and history.
- Run bundled offline OCR plus a local plate detector and recognizer on every original photo.
- Optionally use Workers AI or OpenAI for vehicle recognition.
- Record foreground location and read gallery EXIF time and GPS metadata.
- Support built-in cycle-path and pedestrian-path violations plus custom templates.
- Generate editable Estonian letters and attach photos through Android `FileProvider`.
- Provide Russian, English and Estonian UI localization.
- Store reports and photos locally with no analytics, ads or background sync.

## Technologies

- Kotlin and Jetpack Compose with the custom `Pp*` UI kit; Material 3 is limited to Android infrastructure primitives
- Navigation Compose, ViewModel, coroutines and StateFlow
- CameraX and Android Photo Picker
- Room and Preferences DataStore
- Android Keystore with AES-GCM
- ML Kit Text Recognition and ONNX Runtime
- Fused Location Provider, In-AKS address search, MapLibre Compose with OpenFreeMap, Proj4j and EXIF
- Cloudflare Workers, Workers AI, OpenAI Responses API, TypeScript, Wrangler and Vitest

## Design system

- Follow the custom Nordic field design: restrained surfaces, Forest and Signal accents, minimal elevation and compact information density.
- Screen files must use the internal `Pp*` UI kit instead of directly composing stock Material components.
- Support system light and dark themes; the camera remains dark and immersive.
- Keep interactive targets at least 48 dp and adapt report layouts from 600 dp width.
- Text and multi-field editors commit changes only through an explicit save action. Violation choices commit in one tap and close immediately; dismissing a sheet without choosing an action must not mutate the report.
- Image-generated mockups are visual references only and must not be bundled into the APK.
- When a design change affects UI shown in `site/public/screenshots/`, recapture the affected screenshots from the real app in every applicable locale; do not regenerate unchanged screenshots.

## Development workflow

- Prefix every Gradle command with `env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ANDROID_HOME='/Users/ky6uk/Library/Android/sdk'`; do not use the default Java 26.
- Never send an email from a test device or interact with a mail app's send action; email-flow testing must stop after verifying the populated draft.
- After completing and verifying Android app changes, always install the freshly built debug APK on a connected Android device when one is available.
