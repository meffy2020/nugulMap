# Android Launch UI Cleanup Context

## Task statement
Use OMX Team plus Ultragoal to fix Android launch screen quality issues:
- remove launch copy: "지도 미리보기", "N개 구역 표시 중"
- do not expose raw API/DNS errors on map launch UI
- when Kakao key is absent, show a quiet iOS-like map shell
- fix fallback marker count vs actual zone count mismatch
- if possible, validate Android real map with Kakao key; otherwise verify no-key fallback and build/emulator behavior

## Desired outcome
Android launch screen should feel release-ready and visually closer to iOS: fullscreen, quiet, no diagnostic/debug copy, no raw technical error messages.

## Known facts/evidence
- iOS canonical target is `ios-native/NeogulMapNative/Views/ZoneMapView.swift`.
- Android key absence is detected in `KakaoZoneMap.kt` with `BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()`.
- Current fallback displays `지도 미리보기` and `서울 중심 ${zoneCount}개 구역을 표시 중`.
- Current fallback forwards `errorMessage` to visible status UI.
- `MapScreen.kt` passes `uiState.errorMessage` to both map fallback and bottom status toast.
- Local `android-native/local.properties` currently has blank/missing `KAKAO_NATIVE_APP_KEY`.

## Constraints
- No backend/API contract changes.
- No new feature expansion.
- Keep diff small and reversible.
- Do not commit generated screenshots/logs.
- Verify with Android assembleDebug and emulator/screenshot if possible.

## Unknowns/open questions
- Real Kakao map verification depends on availability of a configured local `KAKAO_NATIVE_APP_KEY`.
- Emulator networking may be unstable; raw errors must not leak even when DNS fails.

## Likely touchpoints
- `android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/KakaoZoneMap.kt`
- `android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt`
- `android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapViewModel.kt` if error sanitization belongs in state
- Android Gradle build and emulator validation scripts/commands
