# NugulMap Native Kakao Map Key Setup

## Kakao Developers에서 받을 키

- Web 지도(`NEXT_PUBLIC_KAKAOMAP_APIKEY`): JavaScript key. 현재 웹/Next.js 지도용이며 네이티브 앱 SDK 키가 아닙니다.
- Android/iOS 네이티브 지도 SDK: 같은 Kakao Developers 앱의 **Native app key**를 사용합니다.

## Android

현재 Android 네이티브 앱은 Kakao Map SDK가 이미 연결되어 있고 `BuildConfig.KAKAO_NATIVE_APP_KEY`를 읽습니다.

1. Kakao Developers > 내 애플리케이션 > 앱 > 플랫폼 키 > Native app key에서 Android 플랫폼을 등록합니다.
2. 패키지명: `com.nugulmap.nativeapp`
3. 디버그/릴리즈 키 해시를 등록합니다.
   - 디버그 키 해시 예시:
     ```bash
     keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android | openssl sha1 -binary | openssl base64
     ```
   - 릴리즈 키 해시는 배포용 keystore alias/path로 같은 방식으로 생성합니다.
4. 로컬 개발에서는 `android-native/local.properties`에 넣습니다.
   ```properties
   KAKAO_NATIVE_APP_KEY=발급받은_NATIVE_APP_KEY
   ```
5. CI/배포 빌드에서는 환경변수로 넣습니다.
   ```bash
   export KAKAO_NATIVE_APP_KEY=발급받은_NATIVE_APP_KEY
   ```

키가 비어 있으면 Android 앱은 오류 문구 대신 조용한 지도 셸 fallback을 표시합니다.

## iOS

현재 iOS 네이티브 앱은 `MapKit` 기반입니다. Kakao iOS 지도 SDK로 전환하려면 별도 SDK 의존성과 지도 뷰 교체가 필요합니다.

1. Kakao Developers > 내 애플리케이션 > 앱 > 플랫폼 키 > Native app key에서 iOS 플랫폼을 등록합니다.
2. Bundle ID: `com.nugulmap.native`
3. Kakao iOS SDK 초기화에는 같은 **Native app key**를 사용합니다.
4. Kakao SDK 로그인/딥링크 기능까지 쓰는 경우에는 Info.plist URL Scheme에 `kakao{NATIVE_APP_KEY}` 형식도 추가해야 합니다.

> 정리: Android는 지금 바로 `KAKAO_NATIVE_APP_KEY`만 채우면 Kakao 지도 SDK 경로를 탑니다. iOS는 아직 MapKit이라 Kakao 지도를 쓰려면 SDK 전환 작업이 추가로 필요합니다.
