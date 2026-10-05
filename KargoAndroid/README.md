# Barkodu Android — v0.1-beta

Native CameraX and offline ML Kit barcode scanning for Android 6 and later, with a Miuix Compose interface.

Select DHL/MNG, HepsiJet, Tex or Aras before scanning. Unmatched barcodes go to Unknown. Scan horizontally or vertically, add codes manually, undo changes, and export Excel or TXT files.

A barcode held in view is added once. Remove it from view for at least 500 ms before presenting it again to count another order. Different codes are accepted immediately. The optional repeat warning is saved between launches.

Open this directory in Android Studio. Use Android SDK 36, JDK 17+ and Gradle 9.1.0. Run `gradle testDebugUnitTest assembleRelease`; add `-PphoneApk` for ARM phones. Local signing keys and SDK paths are excluded from Git.

The test suite contains 4 model/export tests, 6 scan gate tests and 3 UI tests. Real phone camera testing is pending.

[Download the beta](https://github.com/omertrans678/Barkodu/releases/tag/v0.1-beta).

Miuix: https://github.com/compose-miuix-ui/miuix (Apache 2.0). The license is included in APK assets.
