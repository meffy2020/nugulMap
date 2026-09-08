# Production database changes

The production deployment workflow first creates a verified database backup, then applies the canonical
`schema.sql` followed by these guarded MySQL scripts before starting an API build whose production profile
uses `ddl-auto: validate`. This lets a fresh database receive the base tables while existing databases receive
only guarded additive changes. For a deployment outside that workflow, preserve the same order manually.

Current order:

1. `20260710_launch_safety.sql`
2. `20260711_apple_token_storage.sql`
3. `20260711_ugc_publication_safety.sql`
4. `20260712_support_request_retention.sql`
5. `20260712_moderation_report_retention.sql`
6. `20260901_recommendation_data_foundation.sql`
7. `20260901_recommendation_data_limits.sql`
8. `20260901_location_letters.sql`
9. `20260907_quarantine_demo_zones.sql`

흡연구역 전용 출시에서는 추천·편지 API를 기본 비활성화한다. 기존 데이터의 보관기간과
계정 삭제 처리를 유지하기 위해 해당 스키마 마이그레이션은 계속 적용한다.

`20260907_quarantine_demo_zones.sql`은 알려진 테스트 자료와 주소·좌표·설명이 정확히 일치하는
공개 장소만 `PENDING`으로 전환한다. 행과 리뷰는 삭제하지 않는다. 백업 뒤 실행하고 마지막
조회 결과가 `0`인지 확인한다. ID만으로 판별하지 않으며, 재실행해도 이미 격리한 행을 변경하지 않는다.

격리된 로컬 MySQL 8에서 전체 순서와 반복 실행, 운영 프로필 기동 및 계정/신고/문의 흐름을
재현하려면 저장소 루트에서 `python3 scripts/verify-local-release-stack.py`를 실행한다.
Java 21로 새 `bootJar`를 먼저 만들고 Docker와 로컬 `mysql:8.0` 이미지가 있어야 한다.
이 검사는 기존 DB나 운영 API를 사용하지 않는다.

`20260901_location_letters.sql`은 등록된 장소가 없어도 현재 좌표에 편지를 남기고 주변에서 읽을 수 있는
편지·좋아요·신고 테이블을 추가한다. 저장 좌표는 약 10m 단위로 낮춰 보관하고, 신고가 처리되면 기존 리뷰·장소
신고와 같은 보관 주기에 따라 정리할 수 있도록 `resolved_at`과 조회 인덱스를 함께 보장한다.

`20260901_recommendation_data_foundation.sql`은 개인화를 명시적으로 켠 사용자의 선호 설정,
저장 장소, 추천 행동을 보관하는 세 테이블을 추가한다. 추천 행동에는 사용자의 정밀 위치를 저장하지
않으며, 계정 삭제 시 세 테이블의 사용자 데이터도 함께 삭제된다. 개인화를 유지하더라도 추천 행동은
기본 180일 보관 후 매일 자동 삭제한다.

`20260712_support_request_retention.sql`은 기존 완료·반려 요청의 `resolved_at`을 최초 접수 시각으로
보정하고, 이후 애플리케이션이 완료일로부터 30일이 지난 이메일과 문의 원문을 자동 삭제할 수 있게 한다.

`20260712_moderation_report_retention.sql`은 기존 해결·기각 신고의 `resolved_at`을 최초 신고 시각으로
보정하고, 이후 애플리케이션이 처리일로부터 30일이 지난 리뷰·장소 신고 원문을 매일 자동 삭제할 수 있게 한다.
이 스크립트는 신고 테이블을 만드는 `20260710_launch_safety.sql` 뒤에 실행해야 한다.

For `20260711_ugc_publication_safety.sql`, take a database backup first, then verify:

- `zone.publication_status` exists and existing rows are `PUBLISHED`.
- `zone_review.author_id` is nullable and `fk_zone_review_author` uses `ON DELETE SET NULL` as a direct-DB-delete fallback.
- The application account-deletion flow deletes every review authored by the user before deleting the user row.
- New app-created or edited zones enter `PENDING` and appear in the operator submission queue before they can be published.

Rolling application code back does not require dropping `zone.publication_status`. Do not restore the old `ON DELETE CASCADE` review-author rule unless deleting anonymous legacy reviews is explicitly intended.
