# Neogul Map

## 프로젝트 개요

Neogul Map은 Docker Compose를 사용하여 전체 스택을 실행할 수 있는 프로젝트입니다.

## 사전 요구사항

- Docker
- Docker Compose

## Docker Compose 사용법

이 프로젝트는 두 가지 Docker Compose 설정을 제공합니다:

### 1. 전체 스택 실행 (프로덕션/통합 환경)

루트 디렉토리에서 실행:

```bash
docker-compose up -d
```

**포함된 서비스:**
- **Nginx**: 리버스 프록시 (포트 80)
- **MinIO**: 로컬 S3 저장소 (포트 9001 - 관리자 UI)
- **MySQL**: 데이터베이스
- **API Server**: Spring Boot 백엔드 (내부 네트워크만)
- **Frontend**: Next.js 프론트엔드 (내부 네트워크만)

**접속 주소:**
- 웹 애플리케이션: http://localhost
- MinIO 관리자 UI: http://localhost:9001

**중지:**
```bash
docker-compose down
```

### 2. 백엔드만 개발/테스트 (개발 환경)

백엔드 디렉토리에서 실행:

```bash
cd backend/api-server
docker-compose up -d
```

**포함된 서비스:**
- **MySQL**: 데이터베이스
- **API Server**: Spring Boot 백엔드 (포트 8080)

**접속 주소:**
- API 서버: http://localhost:8080

**중지:**
```bash
docker-compose down
```

## 환경 변수 설정

### 전체 스택 실행 시

루트 `docker-compose.yml`은 다음 환경 변수를 사용합니다:
- `MINIO_ROOT_USER`: MinIO 관리자 사용자명 (기본값: admin)
- `MINIO_ROOT_PASSWORD`: MinIO 관리자 비밀번호 (기본값: password)
- `MYSQL_PASSWORD`: MySQL root 비밀번호 (기본값: password)
- `MYSQL_DATABASE`: MySQL 데이터베이스명 (기본값: mydb)
- `MYSQL_USERNAME`: MySQL 사용자명 (기본값: root)

`.env` 파일을 생성하여 환경 변수를 설정할 수 있습니다.

### 백엔드만 실행 시

`backend/api-server/.env.local` 파일을 생성하여 환경 변수를 설정할 수 있습니다.

## 유용한 명령어

### 로그 확인
```bash
# 전체 스택 로그
docker-compose logs -f

# 특정 서비스 로그
docker-compose logs -f api-server
docker-compose logs -f mysql
```

### 컨테이너 상태 확인
```bash
docker-compose ps
```

### 컨테이너 재시작
```bash
docker-compose restart api-server
```

### 볼륨 및 네트워크 정리
```bash
# 컨테이너와 네트워크만 제거
docker-compose down

# 컨테이너, 네트워크, 볼륨 모두 제거
docker-compose down -v
```

## 프로젝트 구조

```
.
├── docker-compose.yml              # 전체 스택용 Docker Compose
├── backend/
│   └── api-server/
│       ├── docker-compose.yml     # 백엔드 개발용 Docker Compose
│       └── Dockerfile
├── frontend/
│   └── Dockerfile
└── nginx/
    └── default.conf
```

## 문제 해결

### 포트 충돌
포트가 이미 사용 중인 경우, `docker-compose.yml`에서 포트 매핑을 변경하세요.

### 컨테이너가 시작되지 않음
```bash
# 로그 확인
docker-compose logs

# 컨테이너 재빌드
docker-compose up --build
```

### 데이터베이스 연결 오류
MySQL 컨테이너가 완전히 시작될 때까지 기다려주세요. Health check가 완료될 때까지 API 서버는 대기합니다.

