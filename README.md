# Barkodu

Native Android barcode scanner with Scan, Save and Settings pages.

- Swipe navigation, black and white themes.
- DHL, JET, TEX and ARAS tabs in a Miuix Demo-style card with paged content and carrier totals.
- Continuous barcode list, duplicate confirmation, left-swipe deletion and Undo.
- Fixed 16:9 camera preview; blurred paused frames survive app restarts and stay visible until the live preview is ready.
- Green/red camera border feedback for recognized/unrecognized codes.
- Miuix icons, grouped cards and gradient About page.
- TXT/XLSX export and Share as TXT.
- Native Miuix dialogs, buttons and notices that dismiss with a swipe in either direction.
- Optional warning sound and confirmation for unrecognized barcodes. Accepted codes belong to the selected carrier.

Package: `com.omerceren.barkodu` · Android 6.0+ (API 23).

## Build

Use JDK 21 and Android SDK 36. The Gradle wrapper downloads Gradle 9.1.0. Set `sdk.dir` in the ignored `local.properties` file.

```sh
./gradlew testDebugUnitTest assembleRelease -PphoneApk
```

For your release signing key, pass `-PbarkoduKeystore=/absolute/path/barkodu-local.keystore` (current signing configuration uses the local Android development key). Without this property, an available ignored local key or the debug key is used. Configure private signing credentials before a production release.

Screenshots from UI tests are saved under `build/evidence`.

## License

Copyright 2026 Omer Ceren. Licensed under [Apache-2.0](LICENSE). Dependency notices are included in the app's Third Party Licenses screen. Google ML Kit has separate terms.
