# Pole parkla site

- Capture website screenshots from the real app on a connected Android device; do not generate, redraw, or manually recreate app UI.
- Use `../app/src/androidTest/assets/street_plate_test_image.png` as the shared camera/gallery fixture. Its approved plate is `003 PUK`; do not introduce a separate fake vehicle image.
- A temporary debug-only camera-preview substitution is allowed for screenshot capture, but remove it and reinstall the clean debug APK before finishing.
- Preserve the real translucent camera tray rendered by the app (`CameraScrim`, alpha `0xB8`); do not replace it with an opaque black panel.
- Keep one screenshot set per locale under `public/screenshots/<locale>/`: `camera.webp`, `review.webp`, and `report.webp`.
- Use the approved screenshot data consistently: `Pier Dolique`, `+37256789012`, `003 PUK`, `Lastekodu tn 42, Tallinn`, and `13.08.2026 16:58`.
- Keep Google Play and source links hidden until their real public URLs are configured.
