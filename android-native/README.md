# NugulMap Android Native

Kotlin + Jetpack Compose 기반 너굴맵 Android 네이티브 앱입니다.

## 현재 범위

- 기본 Compose 앱 셸
- 운영 API `GET /api/zones/bounds` 호출
- Kakao Maps Android SDK 기반 네이티브 지도 패널
- Zone 목록/좌표 카드 + 상세 패널/리뷰 요약 표시
- OAuth 딥링크 스킴 `nugulmap://oauth/callback` 등록
- PKCE 생성 + `/api/auth/mobile/exchange` 교환 skeleton
- AndroidX Security `EncryptedSharedPreferences` 기반 access/refresh token 저장
- `KAKAO_NATIVE_APP_KEY`가 없으면 안전한 설정 안내 화면으로 fallback

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


## 보안 토큰 저장

`AuthTokenStore`는 AndroidX Security `MasterKey(AES256_GCM)`와 `EncryptedSharedPreferences`를 사용합니다. access/refresh token은 평문 SharedPreferences에 쓰지 않습니다.

## OAuth 딥링크 로컬 smoke test

로컬에서 바로 확인 가능한 범위는 Android intent-filter가 `nugulmap://oauth/callback`을 앱으로 전달하고, 앱이 잘못된/임시 code에도 크래시하지 않고 오류 메시지를 표시하는지입니다. 실제 로그인 성공은 앱의 로그인 버튼으로 PKCE verifier를 만든 뒤 제공자 OAuth가 반환한 실제 code로만 검증할 수 있습니다.

### Android Emulator URL scheme smoke

아래 스크립트는 merged manifest에 `nugulmap://oauth/callback` intent-filter가 있는지 확인하고, 연결된 adb device/emulator가 있으면 실제 intent launch까지 수행합니다.

```bash
cd android-native
scripts/smoke-oauth-deeplink.sh
```

기대 결과: static manifest 검증은 항상 PASS해야 합니다. adb device가 있으면 NugulMap 앱이 열리고 크래시하지 않습니다. 로그인 버튼으로 시작한 PKCE 세션이 없으면 `로그인 세션이 만료되었습니다.` 메시지가 정상입니다.

### 실제 OAuth end-to-end smoke

- 앱의 Google/Kakao/Naver 로그인 버튼을 눌러 브라우저 OAuth를 시작합니다.
- 제공자 인증 후 `nugulmap://oauth/callback?code=...` intent가 앱으로 돌아오면 앱이 `/api/auth/mobile/exchange`에 code + PKCE verifier를 전송합니다.
- 기대 결과: `로그인 완료` 또는 `프로필 설정이 필요합니다.` 메시지가 표시되고 토큰이 저장됩니다.

## 다음 구현 순서

1. Kakao 개발자 콘솔의 Android 패키지/키 해시 실기기 smoke test
2. OAuth code + PKCE 로그인 실기기 smoke test
3. 지도 marker/label 기반 Zone 선택 UX 고도화
4. 리뷰 작성/프로필/내 구역/구역 등록 UI 완성도 개선

> 현재 토큰 저장은 AndroidX Security Crypto 기반 EncryptedSharedPreferences를 사용합니다. 장기적으로는 DataStore+암호화 계층 전환을 검토하세요.
