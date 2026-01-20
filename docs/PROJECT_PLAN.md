# Nugulmap(너굴맵) 프로젝트 기획 및 아키텍처 설계서

## 1. 요구사항 정의

### 1.1 기능 요구사항
*   **사용자 관리**: OAuth2(Kakao, Google) 기반 소셜 로그인 및 사용자 프로필 관리.
*   **지도 서비스**: 
    *   현재 위치 기반 흡연 구역 및 특정 구역(Zone) 지도 표시.
    *   마커 클러스터링 및 지도 이동/확대/축소.
*   **구역(Zone) 제보**: 
    *   사용자가 직접 사진 촬영 또는 업로드하여 새로운 구역 제보.
    *   위치 좌표(GPS) 자동 추출 및 보정.
*   **조회 및 필터**: 등록된 구역의 상세 정보(사진, 주소 등) 조회.

### 1.2 비기능 요구사항
*   **사용성**: 모바일 환경에 최적화된 반응형 웹 디자인 (PWA 고려).
*   **성능**: 지도 마커 로딩 속도 최적화 및 이미지 리사이징 처리.
*   **보안**: 업로드된 이미지의 유해성 검사(추후 도입) 및 개인정보 보호.

### 1.3 프로세스
*   **기획**: 위치 기반 커뮤니티 매핑 서비스 기획.
*   **검증**: MVP(최소 기능 제품) 배포 후 사용자 피드백을 통해 데이터 정확도 및 앱 사용성 검증.

---

## 2. 시스템 아키텍처

클라이언트-서버 구조의 모노레포 프로젝트로 구성됩니다.

### 데이터 흐름
`Client (Next.js)` ↔ `API Server (Spring Boot)` ↔ `Database (MySQL/H2)` & `AWS S3`

1.  **Client (Frontend)**: 
    *   Next.js 15 (App Router) 기반의 웹 애플리케이션.
    *   Kakao Map API / Leaflet을 활용한 지도 렌더링.
2.  **API Server (Backend)**: 
    *   Spring Boot 3.5 기반 REST API 서버.
    *   Spring Security + OAuth2 Client로 인증 처리.
3.  **Storage**:
    *   **Database**: H2 (개발용) / MySQL (운영용) - 사용자 및 구역 메타데이터 저장.
    *   **Object Storage**: AWS S3 - 사용자가 업로드한 구역 이미지 파일 저장.
4.  **Data Processing (Utility)**:
    *   Python (FastAPI/Pandas) - 초기 데이터 마이그레이션 및 관리자용 데이터 일괄 처리 (Firestore 연동).

---

## 3. 데이터 모델링

### 주요 엔티티 (Entities)
*   **User**:
    *   `id`: 고유 식별자.
    *   `email`: 사용자 이메일 (계정 연동).
    *   `provider`: 가입 경로 (kakao, google).
    *   `role`: 권한 (USER, ADMIN).
*   **Zone**:
    *   `id`: 구역 고유 ID.
    *   `latitude`, `longitude`: 위경도 좌표.
    *   `address`: 주소 정보.
    *   `imageUrl`: S3 이미지 URL.
    *   `type`: 구역 타입 (예: 흡연 부스, 개방형 흡연 구역 등).
    *   `uploader`: 등록한 사용자 (User와의 관계).

### 설계 전략
*   **JPA (ORM)**: Spring Data JPA를 사용하여 객체 중심의 데이터 조작.
*   **공간 데이터**: 위경도 좌표를 활용한 반경 검색 쿼리 최적화 필요.

---

## 4. 개발 및 배포 계획

### 개발 방법론
*   **Iterative**: 핵심 기능(지도 보기, 로그인, 업로드) 우선 구현 후 고도화.
*   **Monorepo**: 백엔드와 프론트엔드를 단일 저장소에서 관리하여 일관성 유지.

### CI/CD 및 운영
*   **Infrastructure (AWS)**: 
    *   **Computing**: AWS EC2 인스턴스(t3.small 이상 권장) 단일 호스트 운영.
    *   **Orchestration**: `Docker Compose`를 사용하여 Spring Boot, MySQL, Nginx(Reverse Proxy) 컨테이너 일괄 관리.
    *   **Database**: EC2 내부에서 실행되는 MySQL 컨테이너.
        *   **주의**: `Docker Volume`을 호스트 경로와 마운트하여 컨테이너 재시작 시 데이터 소실 방지 필수.
    *   **Storage**: AWS S3 (사용자 업로드 이미지 저장).
*   **CI/CD**: GitHub Actions를 통해 빌드 후 EC2에서 `docker-compose up -d --build` 실행.
*   **Monitoring**: AWS CloudWatch (시스템) 및 Docker Logs 확인.

---

## 5. 모바일 앱 확장 전략

현재 웹(Next.js) 기반 프로젝트를 모바일 앱으로 확장하기 위한 단계별 접근 방식입니다.

### 5.1 단계 1: PWA (Progressive Web App) 도입 (최단 기간)
*   **개요**: 현재 Next.js 웹사이트를 모바일 기기에 설치 가능한 형태로 변환.
*   **장점**: 별도의 앱 개발 비용 없이 즉시 적용 가능.
*   **작업**: `next-pwa` 설정, `manifest.json` 작성, 서비스 워커 등록.

### 5.2 단계 2: 하이브리드 앱 (Webview) (스토어 배포용)
*   **개요**: 네이티브 앱 셸(Shell) 안에 기존 웹사이트를 띄우는 방식.
*   **장점**: 기존 웹 코드를 100% 재사용하면서 앱스토어/플레이스토어에 배포 가능.
*   **도구**: React Native WebView, Capacitor.

### 5.3 단계 3: 네이티브 앱 개발 (React Native) (권장)
*   **개요**: 백엔드 API는 그대로 두고, 프론트엔드만 모바일 전용으로 개발.
*   **선정 이유**: 현재 프론트엔드가 React 기반이므로, 러닝 커브가 낮은 **React Native (Expo)**가 가장 적합.
*   **아키텍처 변경**:
    *   **Backend**: 기존 Spring Boot API 재사용 (변경 없음).
    *   **Frontend**: `frontend-web`(Next.js)과 `frontend-mobile`(React Native)로 분리 운영하거나 모노레포 구조 채택.
    *   **지도 연동**: 웹용 지도 API 대신 `react-native-maps` 또는 카카오맵 네이티브 SDK 사용.