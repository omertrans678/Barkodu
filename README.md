# Barkodu

Native Android barcode scanner with Scan, Save and Settings pages.

- Swipe navigation, black and white themes.
- DHL, JET, TEX, ARAS and Unknown tabs with barcode counts.
- Separate barcode cards, duplicate confirmation, swipe deletion and Undo.
- TXT/XLSX export and sharing.

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
