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
npm run check:release:strict:prod
npm run release:verify
```

- `check:release`: 필수 설정 누락/형식 점검
- `check:release:strict`: 스토어 제출 기준(로컬 URL 금지, 키 placeholder 금지)까지 점검
- `check:release:strict:prod`: `NODE_ENV=production` 기준 strict 점검
- `release:verify`: production strict 점검 + 타입체크 + 테스트

```bash
# 릴리즈 검증 전 1회 준비
cp .env.production.example .env.production
# .env.production의 실제 키/도메인 값 입력 후 실행
npm run release:verify
```

## 제보 이미지 업로드

- 제보/수정 모달에서 `사진 선택` 또는 `카메라 촬영`으로 이미지를 추가할 수 있습니다.
- iOS/Android 권한 문구는 `mobile/app.config.ts`에 반영되어 있으니, 스토어 제출 전 문안이 제품 설명과 맞는지 확인하세요.
- 이미지 없이도 제보/수정은 가능하지만, 사진이 있으면 백엔드의 `zone.image`로 함께 저장됩니다.

## 프로필 설정/수정

- OAuth 로그인 직후 닉네임이 없는 사용자는 마이페이지에서 즉시 `추가 프로필 설정` 화면으로 이어집니다.
- 마이페이지에서는 닉네임 변경과 프로필 사진 업로드/교체를 모두 지원합니다.
- 프로필 저장은 백엔드 `/api/users/profile-setup`, `/api/users/{id}` API와 연결되어 있고, 저장 후 앱 상단 메뉴/마이페이지 정보가 즉시 새로고침됩니다.

## 리뷰 기능

- 상세 모달의 `리뷰` 액션으로 장소별 리뷰 모달을 열 수 있습니다.
- 리뷰 모달에서는 기존 리뷰 목록을 조회하고, 로그인한 사용자만 새 리뷰를 등록할 수 있습니다.
- 리뷰 등록은 백엔드 `/api/zones/{zoneId}/reviews` API에 저장되며, 등록 성공 시 목록 상단에 즉시 반영됩니다.
- 리뷰 조회 실패/등록 실패는 모달 내 안내 또는 경고 메시지로 노출됩니다.

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
- 신규 기능(예: 리뷰) 릴리즈 노트 반영 여부 확인
- 제출 전 `npm run release:verify` 통과 확인
