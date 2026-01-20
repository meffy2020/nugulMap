# 너굴맵 (NugulMap) 개발 노트

## 📅 2026-01-20: 프론트엔드/백엔드 이슈 통합 해결 및 배포 준비

### 🚀 요약
프론트엔드 UI/UX 이슈(지도, 마커, 마이페이지)를 전면 수정하고, 백엔드 테스트 API 접근 권한 문제를 해결하여 로컬 환경(Docker)에서의 통합 테스트를 완료했습니다. 모든 변경 사항은 `migration` 브랜치에 푸시되었습니다.

### ✅ 완료된 작업 (Completed)

#### 1. 프론트엔드 (Frontend)
- **지도 UI 개선**:
  - `MapContainer` 로드 시 UI 요소(검색창, 프로필 버튼)가 지도 뒤로 숨는 문제 해결 (`z-index` 조정).
  - 모바일 환경에서 입력창 포커스 시 화면이 확대되는 문제 해결 (`viewport` 메타 태그 추가).
- **기능 추가 및 수정**:
  - **주소 변환 (Geocoding)**: `AddLocationModal`에서 핀을 이동하거나 지도를 클릭할 때 Kakao Maps Geocoder를 통해 위경도를 도로명 주소로 자동 변환하는 로직 구현.
  - **마이페이지**: 우측 상단 프로필 아이콘 클릭 시 "내 정보" 탭에서 내가 등록한 장소 목록을 확인할 수 있도록 복구 및 디자인 병합.
  - **마커 이미지**: 기본 핀 대신 커스텀 이미지(`pin.png`) 적용.
  - **약관 동의**: 로그인 시 `/terms` 페이지를 거쳐가도록 흐름 확인 및 유지.
- **설정 및 빌드**:
  - `docker-compose` 빌드 시 `node_modules` 충돌 문제 해결을 위해 `.dockerignore` 파일 생성 및 설정.
  - Nginx 연동을 고려하여 `API_BASE_URL`을 `http://localhost` (80포트)로 변경.

#### 2. 백엔드 (Backend)
- **보안 설정 (SecurityConfig) 수정**:
  - `dev` 프로파일에서 `TestController` (`/api/test/**`) 접근 시 401 Unauthorized가 발생하는 문제 해결.
  - `@Profile({"dev", "prod", "mysql"})`로 프로파일 확장 및 `requestMatchers` 순서 조정하여 테스트 엔드포인트 허용.
- **테스트 환경 구축**:
  - `TestController`의 매핑 경로 문제 (`/api/test` vs Context Path `/api`) 확인 및 `@RequestMapping("/test")`로 수정하여 URL 중복 방지.
  - 포스트맨 대용 쉘 스크립트 `backend/test_api.sh` 작성 (사용자 생성, 조회, 흡연구역 등록 등 시나리오 자동화).

#### 3. 인프라 (Infra)
- **Docker Compose**:
  - 프론트엔드, 백엔드, DB, MinIO, Nginx 통합 실행 환경 테스트 완료.
  - 컨테이너 이름 충돌 및 포트 점유 문제 해결 가이드 적용.

---

### 🛠 트러블슈팅 (Troubleshooting)

#### 1. 백엔드 API 401 Unauthorized
- **증상**: `curl`로 `/api/test/users` 호출 시 401 에러 발생.
- **원인**:
  1. `SecurityConfig`가 `prod`, `mysql` 프로파일에서만 활성화되도록 설정되어 있어 `dev` 환경에서 기본 보안 설정(모두 차단)이 적용됨.
  2. `server.servlet.context-path: /api` 설정으로 인해 URL이 `/api/api/test/...`로 중복 매핑됨.
- **해결**:
  1. `SecurityConfig`에 `dev` 프로파일 추가.
  2. `TestController` 매핑을 `/test`로 변경.
  3. `SecurityConfig`에서 `/test/**`, `/api/test/**` 경로 `permitAll()` 최상단 배치.

#### 2. Docker 빌드 에러 (Frontend)
- **증상**: `cannot replace to directory .../node_modules ...` 에러 발생.
- **원인**: 로컬의 `node_modules` 폴더가 Docker 컨텍스트에 포함되어 컨테이너 내부 구조와 충돌.
- **해결**: `frontend/.dockerignore` 파일을 생성하여 `node_modules`, `.next` 등을 제외.

---

### 📝 남은 과제 (To-Do)

- [x] **Git Merge & Conflict Resolution**:
  - `migration` 브랜치의 모든 변경사항을 원격 `main` 브랜치와 병합 완료.
  - `add-location-modal.tsx` 등 주요 파일의 충돌을 `migration` 버전(최신 기능) 중심으로 해결.
- [x] **EC2 배포 (완료)**:
  - Docker Compose에서 프론트엔드/Nginx 제거 후 **백엔드 전용(api-server, mysql, minio)**으로 경량화 배포 성공.
  - EC2 자체 Nginx를 이용한 Reverse Proxy 및 SSL(Certbot) 적용 완료.
  - `https://api.nugulmap.com/api/test/health` 를 통해 정상 동작 확인.

## 📅 2026-01-20 (Phase 2): 기능 고도화 및 서비스 완성도 제고

### 🚀 요약
기본 배포 환경 구축 완료 후, 실제 서비스 운영을 위한 UI/UX 개편, 로그인 연동, 공공데이터 적재 작업을 포함한 2단계 로드맵을 수립하고 `TODO.md`를 생성했습니다.

### ✅ 완료된 작업 (Completed)
- **운영 안정화**: Vercel 환경 변수 주입 이슈 해결 및 API 엔드포인트 최적화.
- **프로젝트 관리**: `TODO.md` 생성 및 향후 우선순위 설정 완료.
- **로그인 시스템**: 
  - 프론트엔드 `useAuth` 훅 및 `AuthProvider` 구현으로 전역 로그인 상태 관리.
  - `fetch` 요청 시 `credentials: 'include'` 추가하여 HttpOnly 쿠키 전송 보장.
  - 로그인 버튼 실제 백엔드 OAuth2 엔드포인트 연결.
- **데이터 마이그레이션**:
  - `backend/data-scripts/scripts/upload_to_mysql.py` 작성 및 실행 성공 (용산구 데이터 적재 완료).
  - 백엔드 `ZoneService` 및 `UserService`의 데이터 정합성 이슈(null 허용 등) 해결.
- **UI/UX 개편**:
  - **메인 지도**: 마커 클러스터링 적용 및 선택된 마커 하단 시트(Bottom Sheet) 디자인 고도화.
  - **검색**: 플로팅 스타일의 세련된 검색바 구현 및 실제 API 연동.
  - **제보**: `AddLocationModal` 디자인 현대화 및 주소 자동 완성 기능 연동.
  - **로그인/프로필**: 브랜드 아이덴티티가 반영된 프리미엄 디자인 적용 및 실데이터 연동.
- **기능 추가**:
  - 마이페이지 내 내가 등록한 장소 삭제 기능 구현.

### 📝 진행 예정 (Next Steps)
- **로그인 시스템**: OAuth2 연동 상태 점검 및 프론트엔드 토큰 관리 구현.
- **데이터 마이그레이션**: 파이썬 스크립트를 활용한 흡연구역 데이터 DB 적재.
- **디자인**: 메인 화면 및 로그인 페이지 UI/UX 개편.

### 💡 최종 아키텍처
- **Frontend**: Vercel (Next.js) - `https://nugulmap.com`
- **Backend**: AWS EC2 (Spring Boot) - `https://api.nugulmap.com`
- **Database**: EC2 내 Docker MySQL
- **Storage**: EC2 내 Docker MinIO (S3 호환)

### 🚀 Vercel 연결 가이드
1. Vercel `NEXT_PUBLIC_API_BASE_URL`을 `https://api.nugulmap.com`으로 설정.
2. Vercel 프로젝트 Redeploy 실행.

---

### 💡 커맨드 가이드

**백엔드 API 테스트 실행:**
```bash
chmod +x backend/test_api.sh
./backend/test_api.sh
```

**로컬 Docker 전체 재시작:**
```bash
docker compose down
docker compose up -d --build
```
