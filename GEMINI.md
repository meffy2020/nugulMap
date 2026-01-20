# Nugulmap(너굴맵) 프로젝트 컨텍스트

## 프로젝트 개요
**Nugulmap**(또는 **NeogulMap**)은 특정 구역(에셋 파일들로 미루어 볼 때 주로 흡연 구역)을 지도에 표시하고 관리하는 위치 기반 웹 애플리케이션입니다. 이 프로젝트는 Java Spring Boot 백엔드, Next.js 프론트엔드, 그리고 데이터 처리를 위한 Python 스크립트를 포함하는 모노레포 구조로 되어 있습니다.

## 디렉토리 구조

### `/backend`
백엔드 로직 및 데이터 처리를 담당합니다.
*   **`api-server/`**: 핵심 애플리케이션 서버입니다.
    *   **프레임워크**: Spring Boot 3.5.4 (Java 21).
    *   **빌드 도구**: Gradle.
    *   **주요 기술**: Spring Security, OAuth2, JWT, Spring Data JPA, H2 데이터베이스(런타임), AWS S3 SDK.
    *   **아키텍처**: 표준 계층형 아키텍처 (Controller -> Service -> Repository -> Domain).
*   **`data-scripts/`**: 데이터 처리 및 Firebase/Firestore 연동을 위한 Python 스크립트입니다.

### `/frontend`
사용자 인터페이스를 담당합니다.
*   **프레임워크**: Next.js 15.2.4 (App Router).
*   **언어**: TypeScript.
*   **스타일링**: Tailwind CSS v4, Shadcn/ui (Radix primitives).
*   **지도**: `react-kakao-maps-sdk`, `leaflet`.

---

## 🚀 기술 전략 및 UX 가이드라인

### 1. 모바일 앱 확장 전략
*   **프레임워크**: **React Native (Expo)** 채택.
*   **운영 방식**: `frontend`와 별도로 `mobile` 폴더를 생성하여 관리.

### 2. 데이터베이스 및 스키마 전략
*   **목표**: EC2 내 Docker MySQL 운영 및 데이터 영속성(Volume) 확보.
*   **테이블 설계 (`Zone`)**: `id(BIGINT)`, `user_id(BIGINT)` FK 참조, `Spatial Index` 고려.

### 3. 사용자 경험 (UX) 설계
*   **등록**: 지도 핀 고정 -> 사진 촬영 -> 실내/실외 선택(조건부 노출).
*   **조회**: 하단 모달(Bottom Sheet), **1:1 (정사각형)** 이미지 비율 유지.

---

## 🔗 연동 및 배포 로드맵

### 1단계: 로컬 연동 테스트 (진행 중 🏃)
*   **목표**: `localhost:3000` (FE) ↔ `localhost:8080` (BE) 간의 완전한 로그인 및 API 호출 성공.

### 2단계: 백엔드 배포 가이드 (AWS EC2) ☁️
**본인이 직접 배포 시 따라할 단계별 가이드입니다.**

#### 1. 인스턴스 생성
*   **OS**: Ubuntu 24.04 LTS
*   **Type**: t3.small (권장) 또는 t2.micro (+스왑 메모리)
*   **보안 그룹**: 22(SSH-내IP), 80(HTTP-전체), 443(HTTPS-전체)

#### 2. 서버 환경 세팅 (SSH 접속 후)
```bash
# Docker 설치
curl -fsSL https://get.docker.com -o get-docker.sh && sudo sh get-docker.sh
sudo usermod -aG docker $USER
# (재접속 필수)
```

#### 3. 코드 배포 및 빌드
```bash
git clone https://github.com/meffy2020/nugulMap.git
cd nugulMap/backend/api-server

# 메모리 부족 방지 (스왑 설정)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile

# 빌드
chmod +x gradlew
./gradlew build -x test
```

#### 4. 실행 (Docker Compose)
```bash
# 실행
docker compose up -d --build
```

#### 5. HTTPS 적용 (Nginx + Certbot)
*   **DNS**: `api.nugulmap.com` A 레코드를 EC2 IP로 설정.
*   **Nginx**: Reverse Proxy 설정 (`proxy_pass http://localhost:8080`).
*   **Certbot**: `sudo certbot --nginx -d api.nugulmap.com` 명령어로 SSL 자동 발급.

### 3단계: 프로덕션 연동 (Production Integration)
*   **목표**: `nugulmap.com` (Vercel) ↔ `api.nugulmap.com` (AWS) 연동.