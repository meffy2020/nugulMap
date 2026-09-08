# NugulMap Android/iOS 공개 출시 준비 현황

> 기준일: 2026-09-07 KST
> 현재 판정과 새 검증 근거: [흡연구역 전용 출시 검증](./smoking-only-release-verification.md). 아래 과거 STATIC PASS는 현재 제출 완료를 뜻하지 않는다.
> 목표: `android-native/`와 `ios-native/`가 각각 독립적으로 심사 제출 가능한 기준을 충족하게 만들기
> 비범위: 신규 기능 확장. 단, 스토어 공개 출시 필수 compliance는 예외.

## 1. 현재 판정 요약

| Lane | 현재 상태 | 출시 blocker |
| --- | --- | --- |
| Expo `mobile/` | `release:verify` baseline 존재 | native Android/iOS 출시를 대체하지 않음 |
| Android `android-native/` | targetSdk 36, OAuth deeplink, minSdk 26 adaptive launcher icon, encrypted auth/PKCE backup exclusion, process-restart-safe PKCE, production API/Kakao config, mandatory release signing config 존재 | upload-key credential injection, signed AAB, Play Console, Kakao key hash, 16KB device validation, real-device OAuth/map smoke, Data Safety |
| iOS `ios-native/` | SwiftUI 앱, URL scheme, production API, opaque 1024px AppIcon, app `PrivacyInfo.xcprivacy`와 UserDefaults reason `CA92.1`, KakaoMapsSDK, Sign in with Apple, 앱 내 계정 삭제 진입점 존재 | Apple Team/provisioning, signed archive/TestFlight, App Store Connect App Privacy/metadata, real-device smoke |

## 2. Store-blocker matrix

| 항목 | Android | iOS | 상태 |
| --- | --- | --- | --- |
| App ID | `com.nugulmap.nativeapp` | `com.nugulmap.native` | 저장소 값 존재, 콘솔 소유권 확인 MANUAL |
| Version | `versionCode=1`, `versionName=1.0` | `CURRENT_PROJECT_VERSION=1`, `MARKETING_VERSION=1.0` | 정책 필요 |
| Target policy | targetSdk 36 | deployment target 17.0 | Android pass, iOS signing 필요 |
| Signing | release build가 `ANDROID_KEYSTORE_*`/`ANDROID_KEY_*` 및 외부 keystore 없이는 Gradle 설정 단계에서 중단 | `NUGUL_DEVELOPMENT_TEAM`은 ignored `Secrets.xcconfig`/CI에서 주입 | credential-gated |
| OAuth | `nugulmap://oauth/callback`, pending `client_state` 10분 | `nugulmap://oauth/callback`, pending `client_state` 10분 | 서버 OAuth 세션도 10분으로 정렬, real-device 필요 |
| Map SDK | Kakao native key 필요 | KakaoMapsSDK native key 필요 | console/key hash 및 실기기 smoke 필요 |
| Social login | Provider console 확인 | Sign in with Apple + Kakao/Naver/Google | provider console 및 Apple capability 필요 |
| Account deletion | Account > 계정 삭제에서 `/api/users/me` 호출 | Profile > 계정 삭제에서 `/api/users/me` 호출 | real-device smoke 필요 |
| UGC 신고/차단 | 장소 상세의 장소·사진 신고, 리뷰 신고, 작성자 차단이 인증 API에 연결 | 장소 상세의 장소·사진 신고, 리뷰 신고, 작성자 차단이 인증 API에 연결 | static pass, 로그인 계정 runtime smoke 필요 |
| Privacy | Play Data Safety + 정책 링크 4종, 암호화 인증 prefs backup 제외 | App Privacy + 정책 링크 4종 + app Privacy Manifest | 공개 URL 배포·지원 이메일·processor/retention 확인·console 입력 manual |
| Device smoke | Android physical device | iPhone/TestFlight OAuth → callback → token persistence | blocker/manual |

## 3. 즉시 실행 순서

1. Android 단독은 `cd android-native && python3 scripts/check-launch-readiness.py`로 targetSdk/signing/Kakao/16KB/deeplink blocker를 갱신한다.
2. 전체 native matrix는 `python3 scripts/check-native-public-launch-readiness.py`로 갱신한다. 한 플랫폼만 먼저 제출할 때는 `--platform android` 또는 `--platform ios`를 사용하며, 공통 개인정보·백엔드 gate는 두 경우 모두 포함된다. 선택한 lane에 Repository hard FAIL이 있으면 종료코드 `1`, 외부 gate만 남으면 `MANUAL`과 종료코드 `0`으로 구분된다.
3. Expo baseline `cd mobile && npm run release:verify`를 통과시켜 기존 모바일 회귀를 잠근다.
4. Android `testDebugUnitTest`, `lintRelease`, `checkReleaseSigning`, `bundleRelease`, `scripts/smoke-oauth-deeplink.sh`를 확인한다. `bundleRelease`는 upload key 없이는 성공하면 안 된다.
5. iOS simulator build와 plist/XML 검증 후 Apple Team ID/Archive/TestFlight blocker를 분리한다.
6. Play/App Store console에서 package/bundle id, signing/provisioning, privacy, metadata를 채운다.
7. Android/iOS 실제 기기에서 OAuth → callback → token persistence, map, zone/review 작성, 장소·사진/리뷰 신고, 작성자 차단 smoke를 수행한다.

## 4. Go/no-go 기준

- Go: 두 플랫폼 모두 signed/TestFlight 또는 internal artifact가 있고, real-device smoke와 privacy/store matrix가 통과한다.
- Hold: 하나라도 OAuth, map, account deletion, privacy declaration, signing이 불완전하다.
- Staged fallback: 한 플랫폼이 runtime smoke fail이면 해당 플랫폼만 internal-test-only로 남기고 별도 go/no-go review를 연다.

## 5. Store/privacy readiness inventory

- 상세 데이터 인벤토리와 제출 전 체크리스트: [`docs/store-privacy-readiness.md`](./store-privacy-readiness.md)
- 스토어 설명, 심사 메모, Play App access, 등급 사실관계와 증거 캡처 목록: [`docs/store-privacy-readiness.md`](./store-privacy-readiness.md)의 Sections 9–13
- 공개 라우트: `/privacy`, `/support`, `/account-deletion`, `/connected-accounts`, `/terms`. 저장소 빌드는 검증할 수 있지만 실제 배포 접근성과 지원 폼의 운영 API·회신 체계는 수동 게이트다.
- `nugulmap.com` 프런트는 기존 Vercel Git 연동이 배포 주체다. API EC2 Compose에 프런트를 중복 배포하지 않는다. `Verify Frontend`가 다섯 정책 라우트의 정적 산출물을 확인하고, 성공 후 `Monitor Public Runtime Contract`가 최대 약 6분 동안 공개 URL/API 계약을 재시도한다. 이 재시도는 일반적인 Vercel 전파 경합을 흡수할 뿐 배포 성공을 보장하지 않는다. 6분을 넘기거나 Vercel 프로젝트 연결·production alias가 잘못된 경우 workflow는 실패하며 외부 배포 상태를 확인해야 한다.
- 이번 출시 검사에서는 핫플·팝업·추천·방명록을 제외한다. 공개 흡연구역 영역 조회, 검색, 상세와 리뷰, 정책 URL, 지원 연락처를 검사한다. 알려진 출시 지역의 흡연구역이 비어 있거나 좌표/주소가 잘못되면 실패한다. 현장 존재 여부는 API 형식 검사와 별도로 확인한다.
- 현재 판정: 공개 정책 URL 배포, 실제 개발자 표시명·지원 이메일, Play Data Safety/App Privacy console forms, 서명과 실기기 smoke가 public-launch HOLD gate다.

## 6. iOS lane 세부 blocker evidence

| iOS gate | Repository evidence | 판정 | Required before App Store/TestFlight release |
| --- | --- | --- | --- |
| Development Team / signing | project 값 `$(NUGUL_DEVELOPMENT_TEAM)`은 placeholder이며 `Config/Release.xcconfig` 뒤에 ignored `Secrets.xcconfig`가 override하도록 구성 | MANUAL | Apple Developer Team과 provisioning profile을 ignored 로컬 설정/CI/App Store Connect에서 설정합니다. readiness 검사는 빈 값과 unresolved placeholder를 통과시키지 않습니다. |
| Archive/TestFlight | shared archive scheme과 unsigned simulator Release build는 통과, 계정 서명값은 없음 | MANUAL | `xcodebuild archive -destination 'generic/platform=iOS'` 후 Organizer/App Store Connect validate/upload와 TestFlight internal smoke를 수행합니다. |
| App icon | `AppIcon.appiconset/AppIcon-1024.png`가 opaque 1024px이고 asset catalog build에서 생성됨 | STATIC PASS | App Store Connect listing에서 최종 표시를 확인합니다. |
| Privacy Manifest | app target resource에 `PrivacyInfo.xcprivacy`, UserDefaults reason `CA92.1` 포함 | STATIC PASS | archive validation에서 병합 manifest와 App Privacy 답변을 다시 확인합니다. |
| OAuth URL scheme | `Info.plist` URL scheme `nugulmap`, `AppConfig.oauthCallbackURL`, `ASWebAuthenticationSession(callbackURLScheme:)` 연결 존재 | STATIC PASS | `ios-native/scripts/smoke-oauth-deeplink.sh`와 실제 provider OAuth/device token persistence smoke를 모두 실행합니다. |
| Apple Guideline 4.8 | iOS login UI에 Sign in with Apple 버튼과 `/api/auth/apple/mobile` identity token 교환 경로 존재 | STATIC PASS | Apple Developer capability, App ID 설정, 실제 Apple 로그인 smoke를 수행합니다. |
| Account deletion | Profile > 계정 삭제에서 `/api/users/me` 호출, 백엔드는 본인 계정 삭제만 허용 | STATIC PASS | 실제 로그인 계정으로 삭제 확인, 토큰 정리, 재접근 차단을 smoke합니다. |
| UGC 신고/차단·처리 | `ZoneDetailView`에 장소·사진 신고, 리뷰별 신고·차단이 있고 `/zones/{zoneId}/reports`, 리뷰 `/reports`, `/users/{authorId}/block`에 연결. 운영자 대기열과 `PATCH /operator/moderation/reports/{reportId}`, `PATCH /operator/moderation/reports/zones/{reportId}`의 `RESOLVE`/`DISMISS`/`REMOVE_CONTENT` 처리도 로컬 구현됨 | STATIC PASS | 서로 다른 두 실계정으로 장소·리뷰 신고 접수, 차단 즉시 숨김, 운영자 대기열 유입을 smoke합니다. 배포된 운영자 키로 처리 상태 전환과 `REMOVE_CONTENT`의 콘텐츠·연결 신고 행 제거를 검증합니다. |
| App Privacy | `https://nugulmap.com/privacy`, `https://nugulmap.com/support`, `https://nugulmap.com/account-deletion`, `https://nugulmap.com/terms` 라우트가 저장소에 존재 | MANUAL | 실제 배포 도메인 접근성, 지원 폼 접수·회신, processor/retention 문구를 확인한 뒤 App Store Connect App Privacy 값을 입력합니다. |

## 7. 공식 기준 참조

- Google Play target API: https://developer.android.com/google/play/requirements/target-sdk
- Play Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Android 16KB page size: https://developer.android.com/guide/practices/page-sizes
- Apple App Privacy: https://developer.apple.com/app-store/app-privacy-details/
- Apple App Review Guidelines: https://developer.apple.com/app-store/review/guidelines/

공식 기준:

- Apple App Review Guideline 4.8 Login Services: https://developer.apple.com/app-store/review/guidelines/#login-services
- Apple account deletion support: https://developer.apple.com/support/offering-account-deletion-in-your-app/
- Apple App Privacy details: https://developer.apple.com/app-store/app-privacy-details/
