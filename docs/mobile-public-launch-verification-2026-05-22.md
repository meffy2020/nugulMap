# NugulMap Native Public Launch Verification Evidence

> Date: 2026-05-22 KST
> Lane owner: worker-4 / Verification lane
> Scope: local static/build/test evidence only. No secrets were added and no store submission was attempted.

## Go/no-go summary

**NO-GO for public Android/iOS launch today.** Local compile/test gates are mostly healthy, but store/account/device blockers remain:

- Android Kakao native key/key-hash validation is not configured in this checkout (`android-native/local.properties` has no production `KAKAO_NATIVE_APP_KEY`).
- Android 16KB page-size compatibility for the native Kakao Maps dependency still needs signed AAB / bundletool / Play pre-launch validation.
- iOS `DEVELOPMENT_TEAM` is empty, so archive/TestFlight/App Store signing is not ready.
- iOS social login review risk remains because Kakao/Naver/Google login are present and Apple-equivalent login was not detected by the static audit.
- Play Data Safety, Apple App Privacy, public privacy policy URL, and real-device OAuth/map/location smoke remain manual/account-gated gates.

## Verification evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Native launch readiness audit | PASS command / **NO-GO product result** | `python3 scripts/check-native-public-launch-readiness.py` exited `0`; summary `PASS=9`, `MANUAL=3`, `FAIL=3`; result `NOT PUBLIC-LAUNCH READY`. |
| Expo/mobile strict release gate | FAIL / blocked by missing secret-like public key | `cd mobile && npm run release:verify` failed at `check:release:strict:prod` with `extra.kakaoJavascriptKey is missing`. This is expected in this worker checkout because no production Kakao JS key was added. |
| Expo/mobile TypeScript | PASS | `cd mobile && NODE_ENV=production npx tsc --noEmit` exited `0`. |
| Expo/mobile Jest suite | PASS after rerun | First full run had one transient `ProfileModal.test.tsx` timeout; direct rerun of that file passed, then full `npm run test -- --runInBand` passed `6` suites / `34` tests. Watchman reported an environment warning and fell back to node crawler. |
| Android native debug build | PASS | `cd android-native && ANDROID_HOME=~/Library/Android/sdk ./gradlew assembleDebug` completed `BUILD SUCCESSFUL`. The first attempt without `ANDROID_HOME` failed because SDK location was unset; the SDK exists at `~/Library/Android/sdk`. |
| Android native lint | PASS | `cd android-native && ANDROID_HOME=~/Library/Android/sdk ./gradlew lintDebug` completed `BUILD SUCCESSFUL`; HTML lint report generated under `android-native/app/build/reports/lint-results-debug.html`. |
| iOS native simulator build | PASS | XcodeBuildMCP `build_sim` with project `ios-native/NeogulMapNative.xcodeproj`, scheme `NeogulMapNative`, simulator `iPhone 17 Pro`, and `CODE_SIGNING_ALLOWED=NO` succeeded with no diagnostics. |
| Backend API compile/tests | PASS | `cd backend/api-server && ./gradlew compileJava test` completed `BUILD SUCCESSFUL`. |

## Remaining blockers before public launch

1. Configure production keys outside git:
   - Android `KAKAO_NATIVE_APP_KEY` in local/CI secrets.
   - Expo `EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY` for strict release verification.
2. Validate Android signed release artifact:
   - Generate signed AAB with upload key.
   - Confirm package id in Play Console.
   - Verify Kakao console Android key hash and OAuth callback on a physical device.
   - Run bundletool/Play pre-launch checks for 16KB page-size compatibility.
3. Complete iOS distribution setup:
   - Set Apple `DEVELOPMENT_TEAM`/provisioning outside git.
   - Archive and upload to TestFlight.
   - Resolve Apple Guideline 4.8 risk by adding Sign in with Apple or documenting a valid exception.
4. Complete store/privacy gates:
   - Publish privacy policy URL.
   - Fill Play Data Safety and Apple App Privacy from the current data inventory.
   - Confirm account deletion is discoverable in native app flows.
5. Run real-device smoke on both platforms:
   - Launch app.
   - OAuth login and callback.
   - Token persistence.
   - Map/location rendering.
   - Zone detail/review/account deletion path.

## Stop condition reached

The verification lane has enough local evidence to make a go/no-go decision: **hold public launch** until the listed store, signing, secret, policy, and real-device gates are resolved.
