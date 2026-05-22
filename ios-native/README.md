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

## 포함된 기능

- 웹 버전처럼 전체 화면 지도 첫 화면
- 상단 플로팅 검색바와 프로필 버튼
- 하단 현재 위치 버튼과 `제보하기` 버튼
- MapKit 지도에 흡연구역 마커 표시
- 웹 버전의 상세 바텀시트 스타일: 장소명, 타입 배지, 이미지, 설명, 길찾기
- 웹 add 화면의 슬림 등록 바텀시트 스타일: 주소, 타입 선택, 사진 선택, 팁 입력, 등록 버튼
- 제보 이미지 1280px JPEG 압축 및 30초 중복 등록 제한
- 장소 상세 리뷰 조회
- 네이티브 장소 검색 후 주변 흡연구역 조회, 실패 시 DB 검색 fallback
- Google, Kakao, Naver OAuth 로그인
- Keychain 기반 access token 저장
- OAuth 프로필 미완료 사용자의 닉네임/프로필 이미지 설정
- 내 프로필과 내가 등록한 장소 목록
- 내가 등록한 장소 삭제
- App Shortcuts/App Intents: 지도 열기, 제보하기, 흡연구역 검색
- 키워드 검색: `GET /api/zones/search`
- 주변 영역 조회: `GET /api/zones/bounds`
- 제보 등록: `POST /api/zones`
- 현재 사용자: `GET /api/auth/me`
- 프로필 설정: `POST /api/users/profile-setup`
- 내 등록 장소: `GET /api/zones/my`
- 장소 삭제: `DELETE /api/zones/{id}`
- 네트워크 실패 시 서울 기본 샘플 데이터 표시


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

## 현재 검증 상태

아래 명령으로 iOS Simulator 대상 빌드와 프로젝트 설정 검증을 통과했습니다.

```bash
xcodebuild -quiet -project ios-native/NeogulMapNative.xcodeproj -scheme NeogulMapNative -destination 'generic/platform=iOS Simulator' build
plutil -lint ios-native/NeogulMapNative.xcodeproj/project.pbxproj ios-native/NeogulMapNative/Info.plist
xmllint --noout ios-native/NeogulMapNative.xcodeproj/project.xcworkspace/contents.xcworkspacedata ios-native/NeogulMapNative.xcodeproj/xcshareddata/xcschemes/NeogulMapNative.xcscheme ios-native/NeogulMapNative/Info.plist
```
