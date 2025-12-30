# MySQL 마이그레이션 가이드

## 개요
H2 데이터베이스에서 MySQL로 마이그레이션하는 방법입니다.

## 변경 사항

### 1. 스키마 파일 (`schema.sql`)
- `CREATE SCHEMA` → `CREATE DATABASE` + `USE`
- `CLOB` → `TEXT` (MySQL 호환)
- `DEFAULT CURRENT_DATE` → `DEFAULT (CURRENT_DATE)` (MySQL 8.0.13+)
- `CURRENT_TIMESTAMP` → `NOW()` (data.sql에서)
- 인덱스 이름 명시 (`uk_zone_address`, `uk_users_email` 등)
- 엔진 및 문자셋 명시 (`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`)

### 2. 데이터 파일 (`data.sql`)
- `CURRENT_TIMESTAMP` → `NOW()` (MySQL 호환)

### 3. 설정 파일
- `application-mysql.yml` 생성 (MySQL 전용 설정)
- `build.gradle`에서 MySQL 커넥터 활성화

## 사용 방법

### 방법 1: 프로파일 활성화 (권장)

```bash
# MySQL 프로파일 활성화
java -jar app.jar --spring.profiles.active=mysql

# 또는 환경 변수로
export SPRING_PROFILES_ACTIVE=mysql
java -jar app.jar
```

### 방법 2: 환경 변수 직접 설정

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DATABASE=mydb
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=your_password
export SPRING_PROFILES_ACTIVE=mysql

java -jar app.jar
```

### 방법 3: Docker Compose 사용

`docker-compose.yml`에 MySQL 서비스 추가:

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: mydb
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  api-server:
    # ... 기존 설정
    environment:
      SPRING_PROFILES_ACTIVE: mysql
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DATABASE: mydb
      MYSQL_USERNAME: root
      MYSQL_PASSWORD: password
```

## 데이터베이스 준비

### MySQL 데이터베이스 생성

```sql
CREATE DATABASE IF NOT EXISTS mydb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 스키마 및 데이터 초기화

애플리케이션 시작 시 자동으로 `schema.sql`과 `data.sql`이 실행됩니다.

또는 수동으로 실행:

```bash
mysql -u root -p mydb < src/main/resources/schema.sql
mysql -u root -p mydb < src/main/resources/data.sql
```

## 주의사항

1. **문자셋**: `utf8mb4` 사용 (이모지 지원)
2. **타임존**: `serverTimezone=Asia/Seoul` 설정
3. **SSL**: 개발 환경에서는 `useSSL=false` 사용
4. **H2와 MySQL 동시 사용**: 테스트는 H2, 프로덕션은 MySQL 사용 가능

## 테스트

H2를 계속 사용하려면:
- `application.yml`의 `spring.profiles.active: dev` 유지
- `build.gradle`에서 H2 주석 해제

MySQL을 사용하려면:
- `spring.profiles.active: mysql` 설정
- MySQL 서버 실행 확인

