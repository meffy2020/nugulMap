-- Zone 테이블의 address 컬럼에 유니크 제약조건 추가
-- 경쟁조건(race condition) 방지를 위한 DB 레벨 제약조건

-- 기존 중복 데이터가 있다면 먼저 정리 (필요시)
-- DELETE FROM zone WHERE id NOT IN (SELECT MIN(id) FROM zone GROUP BY address);

-- 유니크 인덱스 생성
CREATE UNIQUE INDEX IF NOT EXISTS ux_zone_address ON zone(address);

-- 또는 제약조건으로 추가 (인덱스와 동일한 효과)
-- ALTER TABLE zone ADD CONSTRAINT uk_zone_address UNIQUE (address);
