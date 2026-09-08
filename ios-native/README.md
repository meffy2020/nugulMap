# NugulMap Native iOS

SwiftUI로 만든 너굴맵 iOS 네이티브 앱입니다. 기존 `mobile/` Expo 앱은 그대로 두고, 웹 버전의 주요 지도, 검색, 제보, 로그인, 프로필 흐름을 네이티브로 옮기기 위해 별도 프로젝트로 구성했습니다.

## 열기

```bash
open ios-native/NeogulMapNative.xcodeproj
```

Xcode에서 실행하기 전에 Signing Team을 본인 Apple Developer 계정으로 지정하세요.

## API 설정

기본 API 주소는 `https://api.nugulmap.com`입니다. Xcode의 target build settings에서 `NUGUL_API_BASE_URL` 값을 바꾸면 앱 실행 시 해당 주소를 사용합니다.

로컬 백엔드를 iOS 시뮬레이터에서 붙일 때는 보통 아래처럼 둡니다.

```text
NUGUL_API_BASE_URL = http://127.0.0.1:8080
```

운영 제출 전에는 공개 HTTPS 도메인으로 되돌리세요.

## 출시 범위

흡연구역만 제공한다. 첫 화면은 흡연구역 지도이며 레이어 선택, 핫플, 팝업, 추천, 주변 편지 진입점은 제공하지 않는다. 시작·로그인·지도 이동·검색에서 해당 기능의 백그라운드 API를 호출하지 않는다. 기존 보조 모델/API 코드는 보존하지만 출시 흐름에서 사용하지 않는다.

## 포함된 기능

- 전체 화면 흡연구역 지도와 장소·주소 검색
- 하단 현재 위치 버튼과 `제보하기` 버튼
- KakaoMapsSDK 기반 지도에 기존 흡연구역 너구리 마커 표시
- 지도 위 선택 카드 스타일: 장소명, 타입 배지, 주소, 좌표, 길찾기
- 웹 add 화면의 슬림 등록 바텀시트 스타일: 주소, 타입 선택, 사진 선택, 팁 입력, 등록 버튼
- 제보 이미지 1280px JPEG 압축 및 30초 중복 등록 제한
- 장소 상세 리뷰 조회
- 네이티브 장소 검색 후 주변 흡연구역 조회, 실패 시 DB 검색 fallback
- Sign in with Apple, Google, Kakao, Naver OAuth 로그인
- Keychain 기반 access token 저장
- OAuth 프로필 미완료 사용자의 닉네임/프로필 이미지 설정
- 내 프로필과 내가 등록한 장소 목록
- 내가 등록한 장소 삭제
- 내 계정 삭제
- App Shortcuts/App Intents: 지도 열기, 제보하기, 흡연구역 검색
- 키워드 검색: `GET /api/zones/search`
- 주변 영역 조회: `GET /api/zones/bounds`
- 제보 등록: `POST /api/zones`
- 현재 사용자: `GET /api/auth/me`
- 프로필 설정: `POST /api/users/profile-setup`
- 내 등록 장소: `GET /api/zones/my`
- 장소 삭제: `DELETE /api/zones/{id}`
- 네트워크 실패 시 직전 정상 결과는 유지하고 기술 용어 없는 오류 상태 표시

### Simulator smoke

로컬 백엔드에서 장소 검색을 바로 실행할 때 process environment를 사용한다. 검색어는 일반 장소·주소로 처리하며 부가 지도 모드를 활성화하지 않는다.

```bash
SIMCTL_CHILD_NUGUL_API_BASE_URL=http://127.0.0.1:18080 \
SIMCTL_CHILD_NUGUL_INITIAL_SEARCH_QUERY=서울역 \
xcrun simctl launch booted com.nugulmap.native
```

회귀 검증: `sh ios-native/scripts/run-contract-tests.sh`. `SmokingReleaseNetworkContractTests`는 시작·위치 이동·검색 초기화에서 인사이트, 추천, 편지 요청이 발생하지 않는지 검증한다. 과거 시즌3 검색 테스트는 현 출시 계약에서 제외한다.

## KakaoMap 전환 상태 (2026-06-13)

현재 iOS 프로젝트는 KakaoMapsSDK SPM 의존성과 `KAKAO_NATIVE_APP_KEY` 주입 경로를 포함합니다. 실제 키는 저장소에 커밋하지 않고 `ios-native/Config/Secrets.xcconfig`, Xcode build setting, 또는 CI secret으로 주입합니다.

KakaoMap 실기기 검증 전에는 아래 setup을 확인합니다.

1. Kakao Developers iOS platform 등록 및 bundle id `com.nugulmap.native`용 native app key 발급
2. secret-safe build setting 또는 xcconfig로 Kakao 지도 키 주입
3. 실제 iPhone/TestFlight에서 지도 표시, 마커 동기화, 선택 카드, 카메라 이동 이벤트 검증


## OAuth 딥링크 로컬 smoke test

로컬에서 확인할 수 있는 범위는 "앱이 `nugulmap://oauth/callback` URL scheme을 받아도 크래시 없이 앱으로 복귀하는지"입니다. 실제 토큰 발급은 서버가 발급한 OAuth `code`와 앱 안에서 시작한 PKCE verifier가 함께 있어야 하므로, 제공자 로그인 버튼으로 시작한 실제 OAuth 흐름에서만 끝까지 검증할 수 있습니다.

### iOS Simulator URL scheme smoke

1. Xcode 또는 아래 `xcodebuild` 명령으로 앱을 빌드/실행합니다.
2. Simulator가 booted 상태일 때 아래 명령을 실행합니다.

```bash
xcrun simctl openurl booted 'nugulmap://oauth/callback?code=smoke-code&profileComplete=true&email=smoke@example.com'
```

반복 가능한 로컬 smoke 스크립트도 제공합니다. 이 스크립트는 `Info.plist` URL scheme과 `AppConfig` 콜백 URL을 검증하고, booted Simulator가 있으면 위 딥링크를 실제로 엽니다.

```bash
ios-native/scripts/smoke-oauth-deeplink.sh
```

기대 결과: Simulator가 NugulMap 앱을 foreground로 열고 앱이 크래시하지 않습니다. booted Simulator가 없으면 설정 검증만 `PASS`하고 URL open 단계는 `SKIP`합니다. 이 명령은 `ASWebAuthenticationSession`에서 진행 중인 로그인 세션을 만들지 않기 때문에 access token 저장까지 검증하지는 않습니다.

### 실제 OAuth end-to-end smoke

- 앱의 Google/Kakao/Naver 로그인 버튼을 눌러 브라우저 OAuth를 시작합니다.
- 제공자 인증 후 `nugulmap://oauth/callback?code=...`로 돌아오면 앱이 `/api/auth/mobile/exchange`에 code + PKCE verifier를 전송합니다.
- 기대 결과: 로그인 완료 후 프로필 버튼이 사용자 상태로 전환되고, 프로필 미완료 계정은 프로필 설정 시트가 열립니다.


## App Store / TestFlight launch readiness

2026년 4월 28일 이후 제출에 필요한 Xcode 26+iOS 26 SDK에서, 서명과 무관한 Release 아카이브 코드 검증은 다음 스크립트로 반복할 수 있습니다. 결과는 임시 폴더에만 생성되며 App Store 서명·검증·업로드를 대신하지 않습니다.

```bash
ios-native/scripts/verify-release-archive-code.sh
```

정적 확인 기준으로 iOS 앱은 아직 App Store 공개 출시 준비가 완료되지 않았습니다. 로컬에서 확인된 핵심 blocker는 아래와 같습니다.

| 항목 | 현재 근거 | 판정 | 다음 조치 |
| --- | --- | --- | --- |
| Signing Team | Debug/Release가 `$(NUGUL_DEVELOPMENT_TEAM)`을 참조하고 ignored `Config/Secrets.xcconfig`에서만 실값을 주입 | blocker | Xcode Signing & Capabilities 또는 ignored 설정/CI에서 Apple Developer Team과 프로비저닝을 지정하고 secret은 커밋하지 않습니다. |
| Archive/TestFlight | Xcode 26+iOS 26 SDK의 unsigned arm64 Release archive는 통과, signing team은 비어 있음 | blocker | Team 설정 후 signed archive, App Store Connect validate/upload, TestFlight 내부 테스트를 수행합니다. |
| Bundle ID | `com.nugulmap.native` | 확인 필요 | App Store Connect에 동일 bundle id를 등록합니다. |
| OAuth callback | `nugulmap://oauth/callback`이 `Info.plist`, `AppConfig`, `ASWebAuthenticationSession`에 연결됨 | static pass | 실제 iPhone/TestFlight에서 provider OAuth → callback → token persistence를 검증합니다. |
| App Review Guideline 4.8 | Sign in with Apple 버튼과 `/api/auth/apple/mobile` 교환 경로 존재 | static pass | Apple Developer capability/App ID 설정 후 실제 기기에서 Apple 로그인 smoke를 수행합니다. |
| Account deletion | 프로필 화면의 `계정 삭제`가 `/api/users/me`를 호출함 | static pass | 실제 로그인 계정으로 삭제, 토큰 정리, 재접근 차단을 검증합니다. |
| App Privacy / Support | `/privacy`, `/account-deletion`, `/support`, `/connected-accounts`, `/terms` 웹 라우트와 앱 링크 존재 | manual | 다섯 공개 URL의 HTTPS 200과 실제 지원 접수·회신을 확인한 뒤 App Store Connect 값을 최종 입력합니다. |

반복 가능한 정적 확인은 저장소 루트에서 실행합니다.

```bash
python3 scripts/check-native-public-launch-readiness.py
ios-native/scripts/smoke-oauth-deeplink.sh
```

공식 기준 확인용 링크:

- App Review Guideline 4.8 Login Services: https://developer.apple.com/app-store/review/guidelines/#login-services
- In-app account deletion support: https://developer.apple.com/support/offering-account-deletion-in-your-app/
- App Privacy details: https://developer.apple.com/app-store/app-privacy-details/

## 현재 검증 상태

아래 명령으로 iOS Simulator 대상 빌드와 프로젝트 설정 검증을 통과했습니다.

```bash
xcodebuild -quiet -project ios-native/NeogulMapNative.xcodeproj -scheme NeogulMapNative -destination 'generic/platform=iOS Simulator' build
plutil -lint ios-native/NeogulMapNative.xcodeproj/project.pbxproj ios-native/NeogulMapNative/Info.plist
xmllint --noout ios-native/NeogulMapNative.xcodeproj/project.xcworkspace/contents.xcworkspacedata ios-native/NeogulMapNative.xcodeproj/xcshareddata/xcschemes/NeogulMapNative.xcscheme ios-native/NeogulMapNative/Info.plist
```
