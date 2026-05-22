# NugulMap Android/iOS 공개 출시 준비 현황

> 기준일: 2026-05-22 KST  
> 목표: `android-native/`와 `ios-native/`를 동시에 공개 출시 가능 상태로 만들기  
> 비범위: 신규 기능 확장. 단, 스토어 공개 출시 필수 compliance는 예외.

## 1. 현재 판정 요약

| Lane | 현재 상태 | 출시 blocker |
| --- | --- | --- |
| Expo `mobile/` | `release:verify` baseline 존재 | native Android/iOS 출시를 대체하지 않음 |
| Android `android-native/` | targetSdk 36, OAuth deeplink, Kakao map SDK, production API 기본값 존재 | signed AAB, Play Console, Kakao key hash, real-device OAuth/map smoke, Data Safety |
| iOS `ios-native/` | SwiftUI 앱, URL scheme, production API, location permission string 존재 | `DEVELOPMENT_TEAM` empty, TestFlight/archive, Apple social login policy, account deletion, App Privacy |

## 2. Store-blocker matrix

| 항목 | Android | iOS | 상태 |
| --- | --- | --- | --- |
| App ID | `com.nugulmap.nativeapp` | `com.nugulmap.native` | 값 존재 |
| Version | `versionCode=1`, `versionName=1.0` | `CURRENT_PROJECT_VERSION=1`, `MARKETING_VERSION=1.0` | 정책 필요 |
| Target policy | targetSdk 36 | deployment target 17.0 | Android pass, iOS signing 필요 |
| Signing | upload key/signed AAB 필요 | `DEVELOPMENT_TEAM=""` | blocker |
| OAuth | `nugulmap://oauth/callback` | `nugulmap://oauth/callback` | static pass, real-device 필요 |
| Map SDK | Kakao native key 필요 | MapKit | Android console/key hash blocker |
| Social login | Provider console 확인 | Kakao/Naver/Google, Apple login risk | iOS review blocker |
| Account deletion | native UX 확인 필요 | native UX 확인 필요 | blocker/manual |
| Privacy | Play Data Safety + policy URL | App Privacy + policy URL | blocker/manual |
| Device smoke | Android physical device | iPhone/TestFlight | blocker/manual |

## 3. 즉시 실행 순서

1. `python3 scripts/check-native-public-launch-readiness.py`로 정적 blocker 목록을 갱신한다.
2. Expo baseline `cd mobile && npm run release:verify`를 통과시켜 기존 모바일 회귀를 잠근다.
3. Android `assembleDebug`, `lintDebug`, `assembleRelease` 또는 AAB 생성 가능 여부를 확인한다.
4. iOS simulator build와 plist/XML 검증 후 Apple Team ID/Archive/TestFlight blocker를 분리한다.
5. Play/App Store console에서 package/bundle id, signing/provisioning, privacy, metadata를 채운다.
6. Android/iOS 실제 기기에서 OAuth → callback → token persistence, map, location, zone detail/review smoke를 수행한다.

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
