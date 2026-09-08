-- Smoking-only release: quarantine exact historical mock fixtures from data.sql.
-- Run only after a verified backup and publication_status migration.
-- Preserve rows/reviews; do not match IDs, delete records, or hide modified places.
-- Re-running is safe: only currently PUBLISHED exact signatures become PENDING.
UPDATE zone SET publication_status = 'PENDING'
WHERE publication_status = 'PUBLISHED' AND (
    (address = '서울특별시 강남구 강남대로 396' AND latitude = 37.4979 AND longitude = 127.0276
     AND description = '강남역 근처 실외 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 강남구 강남대로 396')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('강남역 근처 실외 흡연구역입니다.'))
 OR
    (address = '서울특별시 마포구 홍익로 3' AND latitude = 37.5563 AND longitude = 126.9226
     AND description = '홍대입구역 실내 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 마포구 홍익로 3')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('홍대입구역 실내 흡연구역입니다.'))
 OR
    (address = '서울특별시 중구 명동길 26' AND latitude = 37.5636 AND longitude = 126.9826
     AND description = '명동역 근처 실외 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 중구 명동길 26')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('명동역 근처 실외 흡연구역입니다.'))
 OR
    (address = '서울특별시 종로구 종로 69' AND latitude = 37.57 AND longitude = 126.982
     AND description = '종각역 실내 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 종로구 종로 69')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('종각역 실내 흡연구역입니다.'))
 OR
    (address = '서울특별시 용산구 이태원로 177' AND latitude = 37.5347 AND longitude = 126.9947
     AND description = '이태원역 근처 실외 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 용산구 이태원로 177')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('이태원역 근처 실외 흡연구역입니다.'))
 OR
    (address = '서울특별시 광진구 능동로 110' AND latitude = 37.5403 AND longitude = 127.0692
     AND description = '건대입구역 실내 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 광진구 능동로 110')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('건대입구역 실내 흡연구역입니다.'))
 OR
    (address = '서울특별시 서대문구 신촌로 83' AND latitude = 37.5551 AND longitude = 126.9368
     AND description = '신촌역 근처 실외 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 서대문구 신촌로 83')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('신촌역 근처 실외 흡연구역입니다.'))
 OR
    (address = '서울특별시 강남구 압구정로 113' AND latitude = 37.5275 AND longitude = 127.0286
     AND description = '압구정역 실내 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 강남구 압구정로 113')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('압구정역 실내 흡연구역입니다.'))
 OR
    (address = '서울특별시 송파구 올림픽로 240' AND latitude = 37.5133 AND longitude = 127.1028
     AND description = '잠실역 근처 실외 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 송파구 올림픽로 240')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('잠실역 근처 실외 흡연구역입니다.'))
 OR
    (address = '서울특별시 영등포구 여의대로 24' AND latitude = 37.5219 AND longitude = 126.9242
     AND description = '여의도역 실내 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 영등포구 여의대로 24')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('여의도역 실내 흡연구역입니다.'))
);

-- Deployment must require this result to be zero before restarting the API.
SELECT COUNT(*) AS remaining_published_demo_zones FROM zone
WHERE publication_status = 'PUBLISHED' AND (
    (address = '서울특별시 강남구 강남대로 396' AND latitude = 37.4979 AND longitude = 127.0276
     AND description = '강남역 근처 실외 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 강남구 강남대로 396')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('강남역 근처 실외 흡연구역입니다.'))
 OR
    (address = '서울특별시 마포구 홍익로 3' AND latitude = 37.5563 AND longitude = 126.9226
     AND description = '홍대입구역 실내 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 마포구 홍익로 3')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('홍대입구역 실내 흡연구역입니다.'))
 OR
    (address = '서울특별시 중구 명동길 26' AND latitude = 37.5636 AND longitude = 126.9826
     AND description = '명동역 근처 실외 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 중구 명동길 26')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('명동역 근처 실외 흡연구역입니다.'))
 OR
    (address = '서울특별시 종로구 종로 69' AND latitude = 37.57 AND longitude = 126.982
     AND description = '종각역 실내 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 종로구 종로 69')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('종각역 실내 흡연구역입니다.'))
 OR
    (address = '서울특별시 용산구 이태원로 177' AND latitude = 37.5347 AND longitude = 126.9947
     AND description = '이태원역 근처 실외 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 용산구 이태원로 177')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('이태원역 근처 실외 흡연구역입니다.'))
 OR
    (address = '서울특별시 광진구 능동로 110' AND latitude = 37.5403 AND longitude = 127.0692
     AND description = '건대입구역 실내 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 광진구 능동로 110')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('건대입구역 실내 흡연구역입니다.'))
 OR
    (address = '서울특별시 서대문구 신촌로 83' AND latitude = 37.5551 AND longitude = 126.9368
     AND description = '신촌역 근처 실외 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 서대문구 신촌로 83')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('신촌역 근처 실외 흡연구역입니다.'))
 OR
    (address = '서울특별시 강남구 압구정로 113' AND latitude = 37.5275 AND longitude = 127.0286
     AND description = '압구정역 실내 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 강남구 압구정로 113')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('압구정역 실내 흡연구역입니다.'))
 OR
    (address = '서울특별시 송파구 올림픽로 240' AND latitude = 37.5133 AND longitude = 127.1028
     AND description = '잠실역 근처 실외 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 송파구 올림픽로 240')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('잠실역 근처 실외 흡연구역입니다.'))
 OR
    (address = '서울특별시 영등포구 여의대로 24' AND latitude = 37.5219 AND longitude = 126.9242
     AND description = '여의도역 실내 흡연구역입니다.'
     AND OCTET_LENGTH(address) = OCTET_LENGTH('서울특별시 영등포구 여의대로 24')
     AND OCTET_LENGTH(description) = OCTET_LENGTH('여의도역 실내 흡연구역입니다.'))
);
