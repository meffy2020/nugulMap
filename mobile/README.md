# 너굴맵 모바일 (React Native + Expo)

## 실행

```bash
cd mobile
npm install
cp .env.example .env
npm run start
```

- 코드 수정 즉시 반영: `r`(리로드), `shift + r`(강제 리로드), 기기에서 개발자 메뉴의 Fast Refresh 활성화
- iOS 시뮬레이터: `npm run ios`
- Android 에뮬레이터: `npm run android`

## 테스트

```bash
npm run test
```

## 릴리즈 준비 자동 점검

```bash
npm run check:release
npm run check:release:strict
npm run release:verify
```

- `check:release`: 필수 설정 누락/형식 점검
- `check:release:strict`: 스토어 제출 기준(로컬 URL 금지, 키 placeholder 금지)까지 점검
- `release:verify`: strict 점검 + 타입체크 + 테스트

## EAS 빌드/제출

```bash
# 최초 1회
npm install -g eas-cli
eas login

# 내부 배포(테스터)
eas build --platform ios --profile preview
eas build --platform android --profile preview

# 스토어 제출용
eas build --platform ios --profile production
eas build --platform android --profile production
eas submit --platform ios --profile production
eas submit --platform android --profile production
```

## 앱스토어/플레이스토어 체크리스트

- `APP_IOS_BUILD_NUMBER`, `APP_ANDROID_VERSION_CODE`를 제출마다 증가
- `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_KAKAO_WEBVIEW_BASE_URL`는 공개 `https` 도메인 사용
- 카카오 콘솔 허용 도메인과 앱 `.env` 값 일치 확인
- 위치 권한 문구/개인정보처리방침/고객지원 URL 최신화
- 제출 전 `npm run release:verify` 통과 확인
