# 너굴맵

가까운 흡연구역을 지도에서 찾는 서비스입니다. 첫 공개 출시 범위는 흡연구역 지도, 현재 위치, 장소 검색, 상세·길찾기, 제보·오류 신고입니다. 지도 조회는 로그인 없이 이용하며, 사용자 콘텐츠와 계정 기능에는 인증·정책 동의·신고·차단·계정 삭제 흐름을 유지합니다.

## 출시 준비

- [현재 출시 검증 결과](docs/smoking-only-release-verification.md)
- [정리 계획과 완료 기준](docs/smoking-only-release-plan.md)
- [플랫폼별 출시 절차](docs/mobile-public-launch-readiness.md)
- [스토어 원고와 개인정보 인벤토리](docs/store-privacy-readiness.md)
- [흡연구역 1,061행 원본 대응·품질 감사](docs/data-audit/README.md)

핫플·팝업·추천·방명록은 이번 출시 화면에서 제외합니다. 서버의 해당 API는 `app.discovery.enabled=true`로 명시적으로 켜지 않는 한 등록되지 않습니다. 운영 Compose는 이를 끄고 외부 데이터 사전 수집도 중지합니다. 이전 데이터의 보관기간 처리와 계정 삭제를 위해 기존 스키마는 보존합니다.

## 구성

| 경로 | 역할 |
| --- | --- |
| `android-native/` | Kotlin / Jetpack Compose Android 스토어 앱 |
| `ios-native/` | SwiftUI iOS 스토어 앱 |
| `frontend/` | Next.js 지도 및 공개 정책·지원 웹 페이지 |
| `mobile/` | Expo 별도 앱, 회귀 검증 대상 |
| `backend/api-server/` | Java 21 Spring Boot API |
| `backend/data-scripts/` | 이전 인사이트 수집 도구, 기본 출시 경로에서 비활성 |

## 개발 실행

백엔드는 Java 21, 프런트는 `frontend/package.json`의 pnpm 버전을 사용합니다. 실제 키는 ignored 로컬 설정이나 CI에서 주입하고 저장소에 넣지 않습니다.

```bash
# API 테스트
JAVA_HOME=$(/usr/libexec/java_home -v 21) backend/api-server/gradlew -p backend/api-server test

# 웹 개발 서버
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Android와 iOS의 키·빌드·기기 실행 절차는 각 디렉터리 README에 있습니다. Expo는 `mobile/`에서 `npm run release:verify`로 별도 확인합니다.

## Compose와 운영 배포

루트 Compose는 MySQL, MinIO와 API를 구성합니다. Next.js 프런트는 포함하지 않습니다. 운영 웹은 기존 Vercel Git 연동이 배포하고 API는 `.github/workflows/deploy.yml`이 담당합니다.

`backend/api-server/.env.sample`을 참고해 로컬 `.env.local`을 준비한 뒤 개발 스택을 실행합니다.

```bash
docker compose up -d
```

MySQL 프로필은 이제 스키마나 테스트 자료를 자동 삽입하지 않습니다. 비어 있는 개발 DB에는
[스키마·마이그레이션 순서](backend/api-server/src/main/resources/db/manual/README.md)를 먼저
적용해야 합니다. 운영 프로필은 필수 스키마가 맞지 않으면 기동을 거부합니다.

운영에는 `docker-compose.production.yml`을 함께 적용해야 하며 별도 런타임 자격 증명, 지원 이메일, DB 백업과 순서가 보장된 마이그레이션이 필요합니다. 배포 workflow에 이 검사가 포함되어 있습니다. 로컬 기본 자격 증명을 운영에 사용하지 않습니다.

```bash
python3 -m unittest discover -s scripts/tests
python3 backend/api-server/scripts/test_check_production_compose_security.py
python3 backend/api-server/scripts/check-production-compose-security.py
python3 scripts/check-native-public-launch-readiness.py
python3 scripts/check-public-launch-runtime.py
```

운영 프로필의 실제 MySQL 검증은 Java 21로 `bootJar`를 만든 뒤
`python3 scripts/verify-local-release-stack.py`로 재현합니다. Docker와 캐시된 `mysql:8.0` 이미지가
필요하며, 검사는 새 loopback 전용 DB와 합성 계정만 사용한 뒤 서버·컨테이너를 정리합니다.
배포 CI에서도 같은 검사를 이미지 배포 전에 실행합니다.

정적 검사·시뮬레이터 성공은 서명된 스토어 설치본과 운영 배포 검증을 대신하지 않습니다. 최종 결과 문서에 남은 차단 사항을 별도로 기록합니다.
