# Android launch UI cleanup review

> Date: 2026-05-22 KST
> Scope: Android launch UI cleanup only. No backend/API contracts, dependency versions, generated assets, or secrets were changed.

## Review target

- Goal context: `.omx/context/android-launch-ui-cleanup-20260522T151433Z.md`
- Goal ledger: `.omx/ultragoal/goals.json`
- Android UI files in scope:
  - `android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/KakaoZoneMap.kt`
  - `android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt`
  - `android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapViewModel.kt`
  - `android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/UserFacingMessages.kt`

## Final release-polish status

| Gate | Final evidence | Status |
| --- | --- | --- |
| Remove no-key fallback copy | Static scan for `지도 미리보기`, `표시 중`, and `운영 API` in the Android map launch files returns no matches. | Pass |
| Hide raw launch-map errors | `KakaoZoneMap` no longer accepts or renders `errorMessage`/`localizedMessage`; Kakao SDK init/map errors switch to the quiet fallback shell. | Pass |
| Hide raw bottom-toast errors | `MapScreen` no longer feeds `uiState.errorMessage` into launch bottom chrome; auth/action messages are sanitized before toast display. | Pass |
| Normalize Android UI error state | `MapViewModel` now writes fixed user-facing fallback messages for throwable failures instead of raw `localizedMessage`; map error setter also sanitizes technical strings. | Pass |
| Quiet no-key fullscreen shell | No-key fallback is a fullscreen map-like canvas without a centered diagnostic card or preview/counter copy. | Pass |
| Fallback marker-count consistency | Zero zones returns before drawing markers; marker dots are derived from the actual non-negative `zoneCount`. | Pass |
| Backend/API contract safety | No backend/API files or Gradle dependency files were changed for this cleanup. | Pass |
| Local Kakao key path | Local `KAKAO_NATIVE_APP_KEY` is blank/missing, so real Kakao rendering remains credential-gated; no-key fallback was the locally verifiable path. Nonblank Kakao init/runtime failures keep the UI quiet while writing a safe Logcat failure category. | Credential-gated |

## Verification evidence

- `rg -n "지도 미리보기|표시 중|운영 API|Unable to resolve|No address associated|\\*\\*\\* End Patch" android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/KakaoZoneMap.kt android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt -S` returned no matches.
- `rg -n "= throwable.localizedMessage|throwable.message|exception.localizedMessage|exception.message" android-native/app/src/main/java/com/nugulmap/nativeapp/ui/map -S` returned no matches, so throwable failures no longer enter UI state as raw text.
- `git diff --check` passed.
- `ANDROID_HOME=$HOME/Library/Android/sdk ANDROID_SDK_ROOT=$HOME/Library/Android/sdk ./gradlew :app:compileDebugKotlin --console=plain` passed.
- `ANDROID_HOME=$HOME/Library/Android/sdk ANDROID_SDK_ROOT=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug --console=plain` passed.
- Debug APK generated at `android-native/app/build/outputs/apk/debug/app-debug.apk`.

## Remaining risk

Real Kakao map visual validation still requires a valid local `KAKAO_NATIVE_APP_KEY`. The current pass intentionally makes the no-key state release-presentable and quiet, but it does not prove Kakao's live map tiles/labels visually match iOS on this machine.
