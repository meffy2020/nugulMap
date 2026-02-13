# mobile/DEVELOPMENT.md

## 1) 모바일 개요
`mobile`은 Expo 기반 앱으로 지도 조회, 흡연구역 목록/검색, 즐겨찾기, 등록/프로필 기능을 담당합니다.

- Framework: Expo 54 / React Native 0.81
- 지도: `react-native-maps`

## 2) 실행

### 기본 실행
```bash
cd mobile
npm install
npm run start
```

### 환경 변수
- `EXPO_PUBLIC_API_BASE_URL`
- 값이 없으면 `https://api.nugulmap.com` 폴백 사용

## 3) 진입점/구조
- 앱 진입: `App.tsx`
- 앱 상태 오케스트레이션: `src/features/zones/hooks/useZoneExplorer.ts`
- API 호출: `src/services/nugulApi.ts`
- 인증 상태: `src/hooks/useAuth.ts`
- 탭 정의: `src/navigation/tabs.ts`
- 타입: `src/types.ts`

## 4) API 연동 규약
- 백엔드 `/api` 라우트와 동일 스키마 사용
- 위치 기반 조회에서 bounds/페이지네이션 파라미터 정합성 중요
- 응답 포맷 `{success, data}` 계열을 전제로 UI 렌더링

## 5) 주요 개발 포인트
- 지도 중심 이동 시 조회 요청 과다 방지: debounce/defer 전략 필요
- 이미지 URI, 캐시, 업로드 실패 시 placeholder 처리
- 인증 토큰 보관 위치(secure store)와 만료 처리 일원화
- 앱별 권한(위치) 요청 플로우를 OS 정책에 맞게 처리
- 웹 기능과의 동등성 유지: 지도/검색/등록/프로필 기능 단위로 추적

## 6) 배포/빌드 체크
- Expo 업데이트 시 `EXPO_PUBLIC_API_BASE_URL` 번들 오염 여부
- 지도 키/맵 SDK 버전 호환성
- OTA 배포 정책 또는 앱스토어 릴리즈 노트 반영 사항

## 7) 대응 항목(문제 triage)
1. 지도 화면 빈 목록/무한 로딩
   - bounds 파라미터, 위치 권한, API 응답 필드 정합성 확인
2. 로그인 직후 사용자 정보 미반영
   - 토큰 저장/로딩 타이밍과 초기 fetch 순서 점검
3. 이미지 표시 불량
   - URL 프로토콜(http/https), S3/MinIO 접근 정책 확인
