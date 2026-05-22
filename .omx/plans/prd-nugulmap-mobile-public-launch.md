# PRD: NugulMap Android/iOS 동시 공개 출시 준비

## 1. 목표

`android-native/`와 `ios-native/`를 동시에 공개 출시 가능(public launch ready) 상태로 만들기 위한 실행 기준을 고정한다. 신규 기능 확장은 제외하지만, 스토어 공개 출시를 막는 signing, privacy, OAuth, account deletion, real-device smoke, store metadata 항목은 출시 필수 범위로 포함한다.

## 2. 현재 상태 근거

| 영역 | 근거 | 판정 |
| --- | --- | --- |
| Android SDK | `android-native/app/build.gradle.kts`의 `compileSdk = 36`, `targetSdk = 36` | Play target API 기준은 현재 충족 |
| Android app id/version | `applicationId = "com.nugulmap.nativeapp"`, `versionCode = 1`, `versionName = "1.0"` | 첫 출시 기본값 존재, 운영 version policy 필요 |
| Android API/key config | `NUGUL_API_BASE_URL` 기본값 `https://api.nugulmap.com`, `KAKAO_NATIVE_APP_KEY`는 `local.properties` 주입 | 운영 key/console 검증 필요 |
| Android deeplink | `nugulmap://oauth/callback` intent-filter 등록 | static 설정 존재, 실기기 OAuth 검증 필요 |
| iOS app id/version | bundle id `com.nugulmap.native`, deployment target 17.0, version 1.0/1 | 기본값 존재 |
| iOS signing | `DEVELOPMENT_TEAM = ""` | 공개 출시 blocker |
| iOS URL/location | URL scheme `nugulmap`, location usage string 존재 | static 설정 존재, 실기기 검증 필요 |
| Expo baseline | `mobile/package.json`의 `release:verify` | regression baseline으로만 사용, native 출시 대체 불가 |

## 3. 범위

### 포함

- Expo baseline release verification 재사용
- Android native signed AAB / Play internal testing 준비
- iOS archive / TestFlight 준비
- Android/iOS 실기기 OAuth, 지도, 위치, auth persistence smoke
- Play Data Safety, Apple App Privacy, privacy policy URL, store metadata 준비
- Apple social login Guideline 4.8 및 account deletion gate 점검
- Store-blocker matrix 유지

### 제외

- 스토어 출시와 무관한 신규 기능 확장
- 대규모 UI redesign
- 실제 Play/App Store production 제출 실행
- 계정/서명/콘솔 credential을 repo에 저장하는 작업

## 4. Store-blocker matrix

| Blocker | Android | iOS | 완료 기준 |
| --- | --- | --- | --- |
| Store account/app | Play Console app 필요 | App Store Connect app 필요 | 앱 레코드가 생성되고 package/bundle id가 일치 |
| Signing | upload key/signed AAB 필요 | Team ID/provisioning/archive 필요 | 내부 트랙/TestFlight 설치 가능 |
| Target/platform policy | targetSdk >= 35; 현재 36 | current Xcode/App Store validation | 제출 시점 정책 충족 |
| 16KB page-size | Kakao native SDK 포함 AAB 검증 필요 | N/A | bundletool/Play pre-launch에서 blocker 없음 |
| OAuth callback | Android package/key hash/provider redirect 확인 | URL scheme/provider redirect 확인 | 실제 provider-started OAuth 성공 |
| Social login review | N/A | Apple-equivalent login 필요 여부 판정 | Guideline 4.8 위반 아님 |
| Account deletion | 앱 내 discoverable route 필요 | 앱 내 discoverable route 필요 | 사용자 스스로 계정 삭제/탈퇴 가능 |
| Privacy | Data Safety + policy URL | App Privacy + policy URL | 코드/API 기반 선언 완료 |
| Metadata | 설명, 스크린샷, content rating | 설명, 스크린샷, support URL | 내부 심사 제출 전 필수 항목 완료 |

## 5. 실행 단계

1. **Baseline evidence**
   - `mobile` release baseline, Android debug/lint, iOS simulator build/plist 검증을 실행하고 실패를 blocker로 기록한다.
2. **Static launch readiness audit**
   - `scripts/check-native-public-launch-readiness.py`로 repo 내 확인 가능한 store blocker를 점검한다.
3. **Account-gated blocker resolution**
   - iOS `DEVELOPMENT_TEAM`, Play/App Store app records, signing/provisioning, OAuth provider console 설정을 운영 값으로 맞춘다.
4. **Native real-device smoke**
   - Android/iOS 각각 실제 기기에서 OAuth, map, location, auth persistence, zone detail/review read path를 검증한다.
5. **Privacy/review package**
   - 실제 data inventory를 기반으로 Play Data Safety와 Apple App Privacy 초안을 완성한다.
6. **RC build and go/no-go**
   - Android signed AAB와 iOS TestFlight build에 smoke evidence를 연결한다.
   - 두 플랫폼 모두 gate 통과 시 동시 공개 출시 go.
   - runtime smoke failure가 한쪽에만 있으면 failed platform은 internal-test-only fallback을 허용한다.

## 6. Acceptance criteria

- `.omx/plans/prd-nugulmap-mobile-public-launch.md`와 `.omx/plans/test-spec-nugulmap-mobile-public-launch.md`가 존재한다.
- `docs/mobile-public-launch-readiness.md`가 current blocker matrix와 실행 순서를 설명한다.
- `scripts/check-native-public-launch-readiness.py`가 Android/iOS 정적 blocker를 재현 가능하게 출력한다.
- Android target SDK 기준은 pass로 확인된다.
- iOS signing, Apple social login, account deletion, privacy/store metadata 등 credential 또는 policy blocker는 명확히 fail/manual로 추적된다.
- 신규 기능 확장 없이 공개 출시 blocker 제거 중심으로 실행된다.
