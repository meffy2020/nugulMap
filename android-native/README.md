# NugulMap Android Native

Kotlin + Jetpack Compose 기반 너굴맵 Android 네이티브 앱입니다.

## 현재 범위

첫 공개 출시는 흡연구역 지도에 집중합니다.

- 흡연구역 지도, 현재 위치 이동(선택 권한), 주소·지역 기반 흡연구역 검색
- 선택한 구역 상세, 길찾기 연결, 리뷰, 신규 구역 제보와 잘못된 정보 신고
- 로그인, 계정 삭제, 개인정보·이용약관·커뮤니티 정책
- 지도 이동·앱 복귀·수동 갱신은 흡연구역 API만 호출
- 핫플·팝업·추천·주변 편지는 홈 진입점과 자동 요청에서 제외
- 위치 권한이 없어도 지도 이동과 검색 사용 가능

## 실행

```bash
cd android-native
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug
```

API 기본값은 `local.properties`의 `NUGUL_API_BASE_URL`에서 읽습니다.

```properties
NUGUL_API_BASE_URL=https://api.nugulmap.com
KAKAO_NATIVE_APP_KEY=카카오_네이티브_앱_키
```

로컬 mock API를 Android Emulator에서 확인할 때는 운영 설정 파일을 바꾸지 않고 debug 전용 환경 변수를 사용합니다. debug manifest만 cleartext를 허용하며 release manifest에는 적용되지 않습니다.

```bash
NUGUL_DEBUG_API_BASE_URL=http://10.0.2.2:18080 \
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
ANDROID_HOME=$HOME/Library/Android/sdk \
./gradlew :app:installDebug
```

`local.properties.example`을 복사해 로컬 SDK/운영 키/릴리즈 서명 값을 채울 수 있습니다. 실제 키와 keystore는 커밋하지 않습니다.

## Android 공개 출시 readiness

공개 출시 전 Android lane에서 로컬로 증명 가능한 항목은 targetSdk/패키지/버전, 릴리즈 AAB 생성, OAuth deeplink manifest, Kakao key 주입 여부, 16KB page-size 검증 준비 상태입니다. Play Console 등록, Kakao 콘솔 key hash, 실제 OAuth 성공, Data Safety는 계정/실기기 수동 gate로 남깁니다.

```bash
cd android-native
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
ANDROID_HOME=$HOME/Library/Android/sdk \
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleRelease
python3 scripts/check-launch-readiness.py
scripts/smoke-oauth-deeplink.sh
```

릴리즈 AAB를 Play 업로드 후보로 만들려면 `local.properties` 또는 환경 변수에 upload key 정보를 채웁니다.

```properties
ANDROID_KEYSTORE_PATH=release-upload-key.jks
ANDROID_KEYSTORE_PASSWORD=...
ANDROID_KEY_ALIAS=nugulmap-upload
ANDROID_KEY_PASSWORD=...
```

Kakao Developers에는 Android 플랫폼 패키지명 `com.nugulmap.nativeapp`, Kakao Native App Key와 실제 설치본을 서명한 인증서의 key hash를 같은 조합으로 등록합니다. 서명 주체별 hash의 용도는 다릅니다.

- **debug key hash**: Android Studio/Gradle의 debug keystore로 설치한 개발 빌드에만 사용합니다.
- **direct-release 또는 upload-key hash**: upload/release keystore로 직접 설치한 APK 검증에 사용합니다. upload key는 AAB 업로드자를 확인하는 키이며 Play가 사용자에게 배포하는 앱의 최종 서명 키가 아닙니다.
- **Play App Signing certificate hash**: Google Play 내부·비공개·공개 트랙에서 설치되는 앱에 사용합니다. Play Console의 `설정 > 앱 무결성(App integrity) > 앱 서명 키 인증서`에서 인증서를 확인해 Kakao Developers에 별도로 등록해야 합니다.

따라서 개발 중에는 debug hash를, 직접 배포 APK smoke에는 direct-release/upload-key hash를, Play 설치본에는 **Play App Signing certificate hash**를 등록합니다. 2026-08-31부터 Google Play 신규 앱/업데이트는 targetSdk 36 이상이 필요하고([공식 안내](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)), Android 15(API 35)+ 대상 앱은 16KB page-size 지원을 검증해야 하므로 release AAB를 `bundletool dump config --bundle=...` 또는 Play pre-launch report로 확인합니다.

서명 값이 없거나 keystore 파일이 없으면 `assembleRelease`와 `bundleRelease`는 설정 단계에서 즉시 실패합니다. `./gradlew :app:checkReleaseSigning`과 `scripts/check-launch-readiness.py`도 이 상태를 실패 처리합니다. 실제 Play 업로드용 AAB는 upload key로 빌드하고 `jarsigner -verify` 또는 Play Console에서 서명을 확인해야 합니다.


## 보안 토큰 저장

`AuthTokenStore`는 AndroidX Security `MasterKey(AES256_GCM)`와 `EncryptedSharedPreferences`를 사용합니다. access/refresh token은 평문 SharedPreferences에 쓰지 않습니다.

## OAuth 딥링크 로컬 smoke test

로컬에서 바로 확인 가능한 범위는 Android intent-filter가 `nugulmap://oauth/callback`을 앱으로 전달하고, 앱이 잘못된/임시 code에도 크래시하지 않고 오류 메시지를 표시하는지입니다. 실제 로그인 성공은 앱의 로그인 버튼으로 PKCE verifier와 CSPRNG `client_state`를 만든 뒤, 제공자 OAuth가 동일 `client_state`와 실제 code를 반환한 경우에만 검증할 수 있습니다. pending verifier/state/생성 시각은 같은 Android 암호화 저장소에 보관되며 callback 검증 즉시 한 번만 소모됩니다.

### Android Emulator URL scheme smoke

아래 스크립트는 merged manifest에 `nugulmap://oauth/callback` intent-filter가 있는지 확인하고, 연결된 adb device/emulator가 있으면 실제 intent launch까지 수행합니다.

```bash
cd android-native
scripts/smoke-oauth-deeplink.sh
```

기대 결과: static manifest 검증은 항상 PASS해야 합니다. adb device가 있으면 NugulMap 앱이 열리고 크래시하지 않습니다. 로그인 버튼으로 시작한 pending OAuth 요청이 없거나 callback의 `client_state`가 누락되면 재로그인 안내가 표시되는 것이 정상입니다.

### 실제 OAuth end-to-end smoke

- 앱의 Google/Kakao/Naver 로그인 버튼을 눌러 브라우저 OAuth를 시작합니다.
- 제공자 인증 후 `nugulmap://oauth/callback?code=...&client_state=...` intent가 앱으로 돌아오면 앱이 저장된 state를 상수시간 비교하고, 일치하는 요청을 먼저 소모한 뒤 `/api/auth/mobile/exchange`에 code + PKCE verifier를 전송합니다.
- 기대 결과: `로그인 완료` 또는 `프로필 설정이 필요합니다.` 메시지가 표시되고 토큰이 저장됩니다.

## 다음 구현 순서

1. Kakao 개발자 콘솔의 Android 패키지/키 해시 실기기 smoke test
2. OAuth code + PKCE 로그인 실기기 smoke test
3. 지도 marker/label 기반 Zone 선택 UX 고도화
4. 리뷰 작성/프로필/내 구역/구역 등록 UI 완성도 개선

> 현재 토큰 저장은 AndroidX Security Crypto 기반 EncryptedSharedPreferences를 사용합니다. 장기적으로는 DataStore+암호화 계층 전환을 검토하세요.

## 흡연구역 첫 출시 검증

현재 로컬 검증과 남은 출시 조건은 `docs/release-evidence/README.md`에 기록합니다. 스토어 후보 스크린샷은 `store-assets/google-play/screenshots-smoking/`의 네 장만 사용하며, 과거 `screenshots/`는 제외합니다.


2026-09-08: [실제16KB 에뮬레이터 검증](docs/release-evidence/isolated-release-verification.md)에서 `PAGE_SIZE=16384`와 release 변형 APK의 카카오 지도·상세 화면 동작을 확인했습니다. 기존 Debug 인증서로 격리 빌드한 결과이며, Play 업로드 서명이나 검증된 장소 데이터의 증거는 아닙니다.
