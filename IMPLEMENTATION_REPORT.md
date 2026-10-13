# Barkodu v0.6-beta implementation report

## Completed requirements

1. **Haptics:** only the committed-add path vibrates. Duplicate warnings, held camera frames and rejected lengths never vibrate. Explicit Add again adds one card and then vibrates once.
2. **Delay:** Settings accepts 0–2000 ms and persists it. Confirmed-event cooldown, 75 ms frame throttling, decode consensus and repeat identity have separate state.
3. **Carrier selection:** native Miuix SuperBottomSheet with its own handle, carrier rows and selectable Clear selection. Selection changes preserve records.
4. **Offline/privacy:** replaced ML Kit with bundled ZXing, removed Google scanning/transport dependencies, stripped network permissions, disabled backup and downloadable emoji initialization. README states local-storage behavior and user-directed external sharing exceptions.
5. **Whitelist:** editable and persistent per-carrier prefix rules, retaining existing matching semantics and defaults. Rules are separated from length validation.
6. **Multi-delete:** long press enters selection mode; checkboxes and multi-delete update local persistence. Cancel/back/navigation exits safely. Undo restores only deleted IDs, preserving subsequent additions.
7. **Duplicate Snackbar:** one consumable confirmation ID; dismiss/timeout does not block future genuine re-entry warnings. Double action taps cannot add twice. Notices dismiss in either swipe direction.
8. **Repeat tracking/border:** code identity persists across camera pauses and movement. Stable absence requires six frames over 1.2 seconds. Image movement/failures reset absence evidence. Duplicate feedback is red; normal feedback styling is preserved. No gyroscope readings trigger scan events.
9. **Length/decoding:** confirmed DHL/JET 416/417 rule is exactly ten characters. No fixed length is imposed on other prefixes/carriers. Three identical reads over 120 ms reject isolated/alternating results. Invalid variants cannot overwrite the held-code lock. Strings are never converted to numbers or truncated.
10. **About:** leftward page entry with spring-based motion and existing predictive-back behavior. Animator-scale changes are observed; disabled animations snap immediately.
11. **Scan spacing:** removed the empty top bar; native Scaffold manages system insets. Compact window handling uses actual window size.
12. **TabRow:** native Miuix 0.8.8 TabRow and constrained pager/list content. Four hoisted saveable list states preserve independent positions. Inactive pages are excluded from accessibility semantics; long codes wrap without clipping.
13. **Save/Settings:** native BasicComponent rows with titles, explanatory summaries where applicable and working click actions. Theme, switches, delay and whitelist use existing Miuix styling.
14. **Architecture:** all production/test source is Kotlin. Separate decoder, rules, gate/consensus/throttle, preferences, model, blur and export modules. Central Activity state retains the existing architecture. Camera work is serial with latest-frame backpressure, reusable luminance storage, bounded resolution and guaranteed ImageProxy closure. Export I/O uses an owned coroutine scope; camera/session callbacks are lifecycle guarded. User-facing text is in English resources.

## Root causes corrected

- Vibration previously lived in feedback rendering and could run before addition or more than once. It now occurs only after mutation and persistence scheduling.
- Single-frame acceptance and a short absence reset allowed unstable results and repeated warnings. Consensus and motion-aware absence now govern event identity.
- A rejected eleven-character variant could otherwise replace the ten-character identity lock. Length rejection precedes gate mutation.
- User delay previously throttled camera frames directly, making absence detection slower as delay increased. Event cooldown now leaves absence observation independent.
- Old carrier content separated a TabRow-like header from the real list. The native tabs and actual list pages now share one bounded card.
- A blank Scan top bar introduced unwanted padding. Removing it retains native inset handling.
- ML Kit's dependency behavior did not support a strict no-telemetry claim. The runtime scanning stack has been replaced and the merged permissions audited.
- Theme navigation-bar attributes required API 27 despite a minimum of 23. Version-qualified resources now supply those attributes only on supported Android versions.

## Changed files

- `app/build.gradle`: version 0.6-beta/code 6, ZXing, Material 3 and an explicit declaration of already-transitive AndroidX Startup for manifest/lint visibility. Miuix stays at 0.8.8.
- `app/src/main/AndroidManifest.xml`, `res/xml/data_extraction_rules.xml`: offline permission restrictions, backup exclusions and local startup configuration.
- `MainActivity.kt`, `CargoModel.kt`, `ScanGate.kt`, `RepeatConfirmation.kt`, `FrameBlur.kt`, `XlsxExport.kt`: replace the six Java implementations and fix state, scanning, haptic, persistence and export behavior.
- `CameraBarcodeDecoder.kt`, `CarrierRules.kt`, `AppPreferences.kt`: new independent decoder, carrier validation/settings and backwards-compatible storage modules.
- `MiuixUi.kt`, `res/values/strings.xml`, `res/values/styles.xml`, `res/values-night/styles.xml`, `res/values-v27/styles.xml`, `res/values-night-v27/styles.xml`: native components, selection, scroll preservation, spacing, animation and compatible theme resources.
- `assets/third-party-licenses.txt`: resolved runtime dependencies and embedded notices updated after the decoder replacement.
- Eight Kotlin test suites: retain existing UI/blur/model/confirmation coverage, replace Java tests, and add decoder/rules/reliable-scanning regression tests.
- `README.md`, `CHANGELOG.md`, this report: behavior, privacy, changes and verification limits.

## Automated verification

Command used with JDK 21, Android SDK 36, cached dependencies and the existing signing key:

```text
gradlew testDebugUnitTest lintDebug assembleRelease licenseInventory -PphoneApk -PbarkoduKeystore=<local-key> --offline --no-daemon
```

**Result: BUILD SUCCESSFUL; 59 tests, zero failures; lint zero errors; signed release APK produced.**

| Suite | Tests | Coverage |
| --- | ---: | --- |
| CameraBarcodeDecoderTest | 6 | All five formats, string fidelity, vertical rotation, blank frames, image-change detection, cropped/strided/offset camera buffers |
| CargoModelTest | 6 | Separate duplicate cards, targeted undo, delete history, TXT/XLSX strings and leading zeros |
| CarrierRulesTest | 4 | Scoped ten-character rule, whitelist scope/validation, extensible length sets |
| FrameBlurTest | 2 | Bounded blur generation and output behavior |
| MiuixScreenTest | 21 | Existing navigation, themes, feedback, dialogs, exports, notices, landscape and About regressions |
| ReliableScanningUiTest | 9 | Success-only haptics, duplicate re-entry/dismissal, rejected variants, independent delay/absence, selection/clear, whitelist persistence, multi-delete/back/Undo, scroll restoration, permission/backup checks, disabled About motion |
| RepeatConfirmationTest | 3 | One pending event, consumed IDs and stale action rejection |
| ScanGateTest | 8 | Identity/absence, consensus, camera pause and throttle independence |

Merged manifests and the runtime inventory contain no Internet/access-network-state permission, ML Kit, Play Services scanning, Firebase or Google data-transport modules. Automatic emoji initialization is removed while lifecycle/profile startup entries remain. Screenshots of Scan/Settings were visually inspected; test screenshots cover the other existing UI states.

Remaining lint warnings are advisory plural-resource/KTX suggestions and the intentionally backwards-compatible predictive-back manifest attribute. Existing Gradle/Kotlin, platform color and legacy Activity-result API deprecations remain. They were not hidden behind a lint baseline.

## Device verification still required

- Real camera startup/streaming, optical accuracy for printed labels in poor light, skew, distance and motion.
- Physical vibration/sound and timing, including rapid repeated scans on different phones.
- True disappearance/re-entry in difficult scenes: stability/absence is evidence-based but cannot guarantee presence when the decoder cannot see a label. No new undocumented carrier lengths were invented to hide that limitation.
- Physical Android 6 and modern edge-to-edge/system-inset behavior, arbitrary screen sizes, intermediate animation scales and real predictive-back gestures. Automated UI runs exercise portrait/landscape and disabled animations, not every device.
- Offline operation on a phone in airplane mode and behavior of separately chosen sharing/document-provider apps. The shipped application permission/dependency audit and offline build passed; no physical packet capture was performed.

No implementation requirement is pending a guessed business rule. The only fixed carrier length comes from the user's explicit confirmation; additional lengths can be added through the separated rules structure when authoritative rules are supplied.
