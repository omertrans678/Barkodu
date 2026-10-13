# Changelog

## 0.6-beta

- Convert all application and test source from Java to Kotlin while retaining saved data and exports.
- Use bundled ZXing instead of ML Kit; remove network permissions and telemetry dependencies, disable backup and document privacy.
- Confirm camera results across three consistent reads; separate configurable scan delay from frame throttling and motion-resistant repeat detection.
- Vibrate once after a real addition; never vibrate for duplicate warnings or add duplicates automatically.
- Validate exactly ten characters only for DHL/JET 416/417 prefixes, preserving other lengths and leading zeros.
- Add persistent per-carrier prefix whitelist settings and a native carrier-selection bottom sheet with Clear selection.
- Add long-press multi-selection, checkbox deletion and targeted Undo with persistent item IDs.
- Rebuild carrier lists inside native Miuix TabRow/pager content with independent saved scroll positions.
- Use Miuix BasicComponent rows in Save and Settings, remove the unused Scan top-bar space, and respect disabled system animations in About.
- Fix Android 6 compatibility of navigation-bar theme resources and update dependency license notices.
- Verify with 59 automated tests, debug lint and signed ARM phone release assembly.

## 0.5-beta

- Keep the paused blur visible until CameraX reports a streaming preview, then fade to live video.
- Move Add manually from the top bar to the right of carrier totals below TabRow.
- Allow notices and repeat warnings to dismiss in either swipe direction.
- Follow the Demo pager navigation pattern: immediate tap selection, focus during swipes and animated navigation items.
- Use full-screen About navigation with quarter-width parallax, dimming, rounded entry and predictive back.
- Group native TabRow and horizontally paged carrier content in a Demo-style card.
- Add thicker green recognized / red unrecognized scan feedback with a soft inner glow.

## 0.4-beta

- Page titles slide with their pages; About uses a directional slide transition.
- Native Miuix colors for dialogs, buttons and notifications.
- Native animated carrier TabRow, sliding list content and per-carrier totals.
- Clear all action and Share as TXT labels.
- Solid red swipe deletion; swipe notices left to dismiss.
- Removed Unknown tab; optional sound and confirmation before adding unrecognized codes to the selected carrier.
- Preserved old Unknown records by moving them to the selected carrier (DHL if none is selected).
- Persisted blurred paused camera frames across app restarts.

## 0.3-beta

- Updated Miuix icons, cards and About design.
- Added paused camera blur and continuous list scrolling.
- Fixed camera ratio and left-swipe deletion.

## 0.2-beta

- Added swipe navigation and black/white themes.
- Added carrier tabs with counts and barcode cards.
- Fixed duplicate confirmation, deletion and Undo.
- Changed package to `com.omerceren.barkodu`.
- Cleaned project; kept Android sources only.
- Added Apache-2.0 license.
