# Barkodu

Offline Android barcode scanner written entirely in Kotlin, with Jetpack Compose, Material 3 and Compose Miuix 0.8.8. Scan, Save and Settings use native Miuix components and the existing pager navigation.

- Swipe navigation, black and white themes.
- DHL, JET, TEX and ARAS tabs in a Miuix Demo-style card with paged content and carrier totals.
- Continuous barcode list, duplicate confirmation, swipe deletion, long-press multi-selection and Undo.
- Fixed 16:9 camera preview; blurred paused frames survive app restarts and stay visible until the live preview is ready.
- Green/red camera border feedback for recognized/unrecognized codes.
- Miuix icons, grouped cards and gradient About page.
- TXT/XLSX export and Share as TXT.
- Native Miuix dialogs, buttons and notices that dismiss with a swipe in either direction.
- Optional warning sound and confirmation for unrecognized barcodes. Accepted codes belong to the selected carrier.
- Carrier selection in a bottom sheet, including a selectable Clear selection row.
- Persistent scan delay (0–2000 ms) and editable per-carrier prefix whitelists in Settings.
- Success vibration only after an actual addition. Repeated camera frames never add duplicates automatically; Add again creates one separate card.
- About transitions follow the system animation scale, including disabled animations.

Package: `com.omerceren.barkodu` · Android 6.0+ (API 23).

## Build

Use JDK 21 and Android SDK 36. The Gradle wrapper downloads Gradle 9.1.0. Set `sdk.dir` in the ignored `local.properties` file.

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease -PphoneApk
```

For your release signing key, pass `-PbarkoduKeystore=/absolute/path/barkodu-local.keystore` (current signing configuration uses the local Android development key). Without this property, an available ignored local key or the debug key is used. Configure private signing credentials before a production release.

Screenshots from UI tests are saved under `build/evidence`.

## Scanning and validation

Bundled [ZXing core](https://github.com/zxing/zxing) decodes CODE_128, CODE_39, CODE_93, ITF and CODABAR locally through CameraX. Three identical results spanning at least 120 ms are required before accepting a camera event. Barcodes remain strings: leading zeros, case and meaningful characters are preserved in storage and TXT/XLSX exports.

The confirmed business rule is **exactly 10 characters for DHL/JET codes beginning with 416 or 417**. Eleven-character variants are rejected, never shortened. HTS, TEX, ARAS and other prefixes have no invented fixed length; existing alphanumeric validation (3–64 characters) remains. Consistent decoding reduces isolated errors but cannot prove a barcode is complete where no business length or checksum rule exists.

Whitelist rules are case-insensitive **prefix** matches, scoped to the selected carrier. Existing defaults are retained: DHL `416, HTS`; JET `416, H0, HTS`; TEX `73`; ARAS `72, P0, A0, FL0`. Add `417` to the appropriate whitelist when needed. Clearing a whitelist makes codes unrecognized for that carrier; clearing the carrier selection does not delete records.

The configurable delay limits confirmed scan events independently of frame-processing throttling (75 ms) and repeat detection. A held code stays locked until at least six stable empty frames spanning 1.2 seconds provide absence evidence. Changing images, decode failures, camera pauses and gyroscope movement alone do not unlock it. Dismissing a duplicate warning permits a later warning after genuine re-entry; only one confirmation is pending at a time. Scene-stability thresholds are heuristics requiring physical-device validation in difficult lighting and movement.

Legacy saved records, quantities and settings are retained. Quantities become separate cards with persistent IDs. Each carrier list preserves its own scroll position across navigation and Activity recreation.

## Privacy

Barkodu does not collect user data for the developer or send barcode values, camera images or settings to external servers. Its scanning, validation, record management and file generation work offline. There are no connections to Boyner or any other company's servers in these workflows.

The release contains no analytics, telemetry or tracking SDK. ML Kit and its supporting Google services/data-transport dependencies have been removed. ZXing is bundled; scanning needs no model download. The merged application manifest has neither `INTERNET` nor `ACCESS_NETWORK_STATE` permission. Automatic downloadable emoji/font initialization is disabled; system fonts are used.

Records, settings and the blurred paused-camera image are stored in the app's private local storage. Android backup is disabled, and cloud-backup/device-transfer rules exclude app data. Uninstalling the app or clearing its storage removes these local records; files deliberately exported elsewhere remain under the user's control.

Saving or sharing a file is an explicit user action. A chosen Android document provider or receiving app may upload that file under its own settings and privacy policy. Opening GitHub from About launches an external browser, which uses its own network access. These actions are separate from Barkodu's offline scanning workflow.

## Verification

The v0.6-beta build passes 59 Kotlin JUnit/Robolectric tests, debug lint with zero errors, and signed release assembly. Tests cover five decoder formats, rotation, cropped/strided camera buffers, length rules, repeat/consensus/delay state, haptics through Android shadows, persistence, carrier selection, multi-delete/Undo, export strings, scroll preservation, UI navigation, themes, disabled animations and network-permission removal.

Physical optical decoding, vibration hardware, CameraX startup on a phone, camera shake under poor lighting and real-device predictive back remain device checks. See [the implementation report](IMPLEMENTATION_REPORT.md) for exact results and limits. Existing Gradle/Kotlin and Android API deprecation warnings remain; they do not prevent this build.

## License

Copyright 2026 Omer Ceren. Licensed under [Apache-2.0](LICENSE). Dependency notices and the resolved runtime inventory are included in the app's Third Party Licenses screen. Miuix Demo-derived navigation and About layouts retain Apache-2.0 attribution.
