# Barkodu

Scan carrier barcodes with a camera or handheld scanner, organize them by carrier, and export Excel or TXT lists.

## v0.1-beta

The first beta release includes an Android app and a browser interface.

- CameraX and offline ML Kit scanning on Android 6+.
- Carrier tabs, quantities, undo, and manual entry.
- Miuix light/dark themes and portrait/landscape layouts.
- Repeat scan protection with an optional persistent warning.
- Excel/TXT export and sharing; leading zeroes are preserved.

Download `Barkodu-v0.1-beta-phone.apk` for ARM phones, or `Barkodu-v0.1-beta-universal.apk` for all supported architectures from [Releases](https://github.com/omertrans678/Barkodu/releases).

## Source and build

`KargoAndroid/` contains the Android application. Open it in Android Studio with JDK 17+, Android SDK 36 and Gradle 9.1.0. Run `gradle testDebugUnitTest assembleRelease`; add `-PphoneApk` for the ARM build.

Local SDK paths, caches and signing keys are excluded from Git. When the local signing key is absent, release builds use a debug key. Updates must use the same signing key.

`BarkodListe/` contains the web application. Serve it over HTTPS for camera access. Camera and Excel libraries require an internet connection.

The Android suite includes 13 model, scan gate and UI tests. Real phone camera testing is pending.

Miuix is licensed under Apache 2.0; see `KargoAndroid/app/src/main/assets/miuix-license.txt`.
