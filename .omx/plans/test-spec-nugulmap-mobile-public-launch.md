# Test Spec: NugulMap Android/iOS 공개 출시 준비 검증

## 1. Static verification

### Commands

```bash
python3 scripts/check-native-public-launch-readiness.py
cd mobile && npm run release:verify
cd android-native && ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug lintDebug
xcodebuild -quiet -project ios-native/NeogulMapNative.xcodeproj -scheme NeogulMapNative -destination 'generic/platform=iOS Simulator' build
plutil -lint ios-native/NeogulMapNative/Info.plist ios-native/NeogulMapNative.xcodeproj/project.pbxproj
xmllint --noout ios-native/NeogulMapNative.xcodeproj/project.xcworkspace/contents.xcworkspacedata ios-native/NeogulMapNative.xcodeproj/xcshareddata/xcschemes/NeogulMapNative.xcscheme ios-native/NeogulMapNative/Info.plist
```

### Pass criteria

- Android `targetSdk >= 35`.
- Android deeplink `nugulmap://oauth/callback` exists in manifest.
- iOS URL scheme `nugulmap` exists in Info.plist.
- iOS location usage description exists.
- Known manual/store blockers are printed with owner/action instead of hidden.

## 2. Android native device smoke

Required device matrix:

| Scenario | Expected result |
| --- | --- |
| Fresh install launch | App opens without crash |
| Production API reachability | Bounds/zone request succeeds or user-visible network error appears |
| Kakao map render | Map SDK renders with production native key |
| Location permission allow | Current/nearby map behavior works |
| Location permission deny | App remains usable with fallback/default region |
| OAuth provider-started login | Provider returns to `nugulmap://oauth/callback`; token is stored |
| App restart | Auth state is preserved or intentional logout is explained |
| Zone detail/review read | Existing zone detail and reviews load without crash |
| Signed AAB/internal artifact | Installable via Play internal track or release-like path |

## 3. iOS native device smoke

Required device/TestFlight matrix:

| Scenario | Expected result |
| --- | --- |
| Archive/TestFlight install | Build installs on tester device |
| Fresh launch | App opens without crash |
| Production API reachability | Bounds/search request succeeds or user-visible network error appears |
| Map render | MapKit map and markers render |
| Location permission allow/deny | Both paths are non-crashing and understandable |
| ASWebAuthenticationSession OAuth | Provider returns to `nugulmap://oauth/callback`; token is stored in Keychain |
| App restart | Auth state persists as intended |
| Zone detail/review read | Detail/review path loads without crash |

## 4. Store/compliance validation

### Android

- Play Console app record exists for `com.nugulmap.nativeapp`.
- Signed AAB uses approved upload key.
- 16KB page-size / native SDK validation is clean, especially with Kakao Maps SDK.
- Data Safety form is filled from actual code/API behavior.
- Privacy policy URL is available and matches data usage.
- Content rating, screenshots, descriptions, contact info are complete.

### iOS

- `DEVELOPMENT_TEAM` and provisioning are configured outside source secrets.
- Bundle id `com.nugulmap.native` is registered.
- Archive upload/TestFlight processing succeeds.
- App Privacy details are filled from actual code/API behavior.
- Apple Guideline 4.8 is resolved: Apple login added or a valid exception documented.
- Account deletion is discoverable in-app or a compliant account deletion route is added.
- Privacy policy URL, support URL, screenshots, category, review notes are complete.

## 5. Go/no-go rule

Go only when:

- Android and iOS both pass real-device smoke.
- Store/compliance matrix has no unresolved blocker.
- All failures have evidence, owner, and next action.

Fallback:

- If one platform fails runtime smoke, keep simultaneous target but allow the failing platform to remain internal-test-only while the passing platform may proceed after explicit go/no-go review.
- If failure is only metadata/account entry, hold both briefly and preserve simultaneous public launch target.
