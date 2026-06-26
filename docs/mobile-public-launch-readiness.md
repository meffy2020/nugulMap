# NugulMap Android/iOS 공개 출시 준비 현황

> 기준일: 2026-05-22 KST  
> 목표: `android-native/`와 `ios-native/`를 동시에 공개 출시 가능 상태로 만들기  
> 비범위: 신규 기능 확장. 단, 스토어 공개 출시 필수 compliance는 예외.

## 1. 현재 판정 요약

| Lane | 현재 상태 | 출시 blocker |
| --- | --- | --- |
| Expo `mobile/` | `release:verify` baseline 존재 | native Android/iOS 출시를 대체하지 않음 |
| Android `android-native/` | targetSdk 36, OAuth deeplink, Kakao map SDK, production API 기본값, optional upload-key signing hook, Android-only readiness script 존재 | signed AAB credential injection, Play Console, Kakao key hash, 16KB AAB/device validation, real-device OAuth/map smoke, Data Safety |
| iOS `ios-native/` | SwiftUI 앱, URL scheme, production API, location permission string, KakaoMapsSDK, Sign in with Apple, 앱 내 계정 삭제 진입점 존재 | `DEVELOPMENT_TEAM` empty, TestFlight/archive, App Store Connect App Privacy/metadata, real-device smoke |

## 2. Store-blocker matrix

| 항목 | Android | iOS | 상태 |
| --- | --- | --- | --- |
| App ID | `com.nugulmap.nativeapp` | `com.nugulmap.native` | 값 존재 |
| Version | `versionCode=1`, `versionName=1.0` | `CURRENT_PROJECT_VERSION=1`, `MARKETING_VERSION=1.0` | 정책 필요 |
| Target policy | targetSdk 36 | deployment target 17.0 | Android pass, iOS signing 필요 |
| Signing | `NUGUL_RELEASE_*` upload-key hook 존재, 실제 keystore/secret 필요 | `DEVELOPMENT_TEAM=""` | blocker |
| OAuth | `nugulmap://oauth/callback` | `nugulmap://oauth/callback` | static pass, real-device 필요 |
| Map SDK | Kakao native key 필요 | KakaoMapsSDK native key 필요 | console/key hash 및 실기기 smoke 필요 |
| Social login | Provider console 확인 | Sign in with Apple + Kakao/Naver/Google | provider console 및 Apple capability 필요 |
| Account deletion | Account > 계정 삭제에서 `/api/users/me` 호출 | Profile > 계정 삭제에서 `/api/users/me` 호출 | real-device smoke 필요 |
| Privacy | Play Data Safety + policy URL | App Privacy + policy URL (`/privacy`) | console 입력/manual |
| Device smoke | Android physical device | iPhone/TestFlight OAuth → callback → token persistence | blocker/manual |

## 3. 즉시 실행 순서

1. Android 단독은 `cd android-native && python3 scripts/check-launch-readiness.py`로 targetSdk/signing/Kakao/16KB/deeplink blocker를 갱신한다.
2. 전체 native matrix는 `python3 scripts/check-native-public-launch-readiness.py`로 정적 blocker 목록을 갱신한다.
3. Expo baseline `cd mobile && npm run release:verify`를 통과시켜 기존 모바일 회귀를 잠근다.
4. Android `assembleDebug`, `lintDebug`, `bundleRelease`, `scripts/smoke-oauth-deeplink.sh`를 확인한다.
5. iOS simulator build와 plist/XML 검증 후 Apple Team ID/Archive/TestFlight blocker를 분리한다.
6. Play/App Store console에서 package/bundle id, signing/provisioning, privacy, metadata를 채운다.
7. Android/iOS 실제 기기에서 OAuth → callback → token persistence, map, location, zone detail/review smoke를 수행한다.

## 4. Go/no-go 기준

- Go: 두 플랫폼 모두 signed/TestFlight 또는 internal artifact가 있고, real-device smoke와 privacy/store matrix가 통과한다.
- Hold: 하나라도 OAuth, map, account deletion, privacy declaration, signing이 불완전하다.
- Staged fallback: 한 플랫폼이 runtime smoke fail이면 해당 플랫폼만 internal-test-only로 남기고 별도 go/no-go review를 연다.

## 5. Store/privacy readiness inventory

- 상세 데이터 인벤토리와 제출 전 체크리스트: [`docs/store-privacy-readiness.md`](./store-privacy-readiness.md)
- 현재 판정: privacy policy URL, Play Data Safety/App Privacy console forms, account deletion path, store metadata, iOS social-login review risk가 모두 public-launch HOLD gate다.

## 6. 공식 기준 참조

- Google Play target API: https://developer.android.com/google/play/requirements/target-sdk
- Play Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Android 16KB page size: https://developer.android.com/guide/practices/page-sizes
- Apple App Privacy: https://developer.apple.com/app-store/app-privacy-details/
- Apple App Review Guidelines: https://developer.apple.com/app-store/review/guidelines/


## 6. iOS lane 세부 blocker evidence

| iOS gate | Repository evidence | 판정 | Required before App Store/TestFlight release |
| --- | --- | --- | --- |
| Development Team / signing | `ios-native/NeogulMapNative.xcodeproj/project.pbxproj` Debug/Release target configs have `DEVELOPMENT_TEAM = ""` | FAIL | Apple Developer Team과 provisioning profile을 로컬/Xcode/App Store Connect에서 설정합니다. Team ID 자체는 secret은 아니지만 개인/조직 계정값이므로 자동 커밋하지 않습니다. |
| Archive/TestFlight | `ios-native/NeogulMapNative.xcodeproj/xcshareddata/xcschemes/NeogulMapNative.xcscheme` exists, but signing team is empty | FAIL | `xcodebuild archive -destination 'generic/platform=iOS'` 후 Organizer/App Store Connect validate/upload와 TestFlight internal smoke를 수행합니다. |
| OAuth URL scheme | `Info.plist` URL scheme `nugulmap`, `AppConfig.oauthCallbackURL`, `ASWebAuthenticationSession(callbackURLScheme:)` 연결 존재 | STATIC PASS | `ios-native/scripts/smoke-oauth-deeplink.sh`와 실제 provider OAuth/device token persistence smoke를 모두 실행합니다. |
| Apple Guideline 4.8 | iOS login UI에 Sign in with Apple 버튼과 `/api/auth/apple/mobile` identity token 교환 경로 존재 | STATIC PASS | Apple Developer capability, App ID 설정, 실제 Apple 로그인 smoke를 수행합니다. |
| Account deletion | Profile > 계정 삭제에서 `/api/users/me` 호출, 백엔드는 본인 계정 삭제만 허용 | STATIC PASS | 실제 로그인 계정으로 삭제 확인, 토큰 정리, 재접근 차단을 smoke합니다. |
| App Privacy | `https://nugulmap.com/privacy`, `https://nugulmap.com/account-deletion` 라우트가 저장소에 존재 | MANUAL | 실제 배포 도메인 접근성 확인 후 App Store Connect App Privacy 값을 입력합니다. |

공식 기준:

- Apple App Review Guideline 4.8 Login Services: https://developer.apple.com/app-store/review/guidelines/#login-services
- Apple account deletion support: https://developer.apple.com/support/offering-account-deletion-in-your-app/
- Apple App Privacy details: https://developer.apple.com/app-store/app-privacy-details/
