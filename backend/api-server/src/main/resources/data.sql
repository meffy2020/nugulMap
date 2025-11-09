-- 테스트용 Mock 데이터
-- 위치 기반 반경 검색 테스트를 위한 더미 데이터

-- 사용자 테스트 데이터 (중복 방지)
INSERT INTO users (oauth_id, oauth_provider, nickname, email, profile_image_url, created_at) 
SELECT 'google_123456789', 'GOOGLE', '테스트유저1', 'test1@example.com', 'https://via.placeholder.com/150', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'test1@example.com');

INSERT INTO users (oauth_id, oauth_provider, nickname, email, profile_image_url, created_at) 
SELECT 'kakao_987654321', 'KAKAO', '테스트유저2', 'test2@example.com', 'https://via.placeholder.com/150', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'test2@example.com');

INSERT INTO users (oauth_id, oauth_provider, nickname, email, profile_image_url, created_at) 
SELECT 'naver_555666777', 'NAVER', '테스트유저3', 'test3@example.com', 'https://via.placeholder.com/150', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'test3@example.com');

-- Zone 테스트 데이터 (서울 시내 실제 좌표 기반) - 중복 방지
INSERT INTO zone (region, type, subtype, description, latitude, longitude, size, address, creator, image) 
SELECT '서울특별시', '흡연구역', '실외', '강남역 근처 실외 흡연구역입니다.', 37.4979, 127.0276, '중형', '서울특별시 강남구 강남대로 396', '테스트유저1', NULL
WHERE NOT EXISTS (SELECT 1 FROM zone WHERE address = '서울특별시 강남구 강남대로 396');

INSERT INTO zone (region, type, subtype, description, latitude, longitude, size, address, creator, image) 
SELECT '서울특별시', '흡연구역', '실내', '홍대입구역 실내 흡연구역입니다.', 37.5563, 126.9226, '소형', '서울특별시 마포구 홍익로 3', '테스트유저2', NULL
WHERE NOT EXISTS (SELECT 1 FROM zone WHERE address = '서울특별시 마포구 홍익로 3');

INSERT INTO zone (region, type, subtype, description, latitude, longitude, size, address, creator, image) 
SELECT '서울특별시', '흡연구역', '실외', '명동역 근처 실외 흡연구역입니다.', 37.5636, 126.9826, '대형', '서울특별시 중구 명동길 26', '테스트유저1', NULL
WHERE NOT EXISTS (SELECT 1 FROM zone WHERE address = '서울특별시 중구 명동길 26');

INSERT INTO zone (region, type, subtype, description, latitude, longitude, size, address, creator, image) 
SELECT '서울특별시', '흡연구역', '실내', '종각역 실내 흡연구역입니다.', 37.5700, 126.9820, '중형', '서울특별시 종로구 종로 69', '테스트유저3', NULL
WHERE NOT EXISTS (SELECT 1 FROM zone WHERE address = '서울특별시 종로구 종로 69');

INSERT INTO zone (region, type, subtype, description, latitude, longitude, size, address, creator, image) 
SELECT '서울특별시', '흡연구역', '실외', '이태원역 근처 실외 흡연구역입니다.', 37.5347, 126.9947, '소형', '서울특별시 용산구 이태원로 177', '테스트유저2', NULL
WHERE NOT EXISTS (SELECT 1 FROM zone WHERE address = '서울특별시 용산구 이태원로 177');

INSERT INTO zone (region, type, subtype, description, latitude, longitude, size, address, creator, image) 
SELECT '서울특별시', '흡연구역', '실내', '건대입구역 실내 흡연구역입니다.', 37.5403, 127.0692, '중형', '서울특별시 광진구 능동로 110', '테스트유저1', NULL
WHERE NOT EXISTS (SELECT 1 FROM zone WHERE address = '서울특별시 광진구 능동로 110');

INSERT INTO zone (region, type, subtype, description, latitude, longitude, size, address, creator, image) 
SELECT '서울특별시', '흡연구역', '실외', '신촌역 근처 실외 흡연구역입니다.', 37.5551, 126.9368, '대형', '서울특별시 서대문구 신촌로 83', '테스트유저3', NULL
WHERE NOT EXISTS (SELECT 1 FROM zone WHERE address = '서울특별시 서대문구 신촌로 83');

INSERT INTO zone (region, type, subtype, description, latitude, longitude, size, address, creator, image) 
SELECT '서울특별시', '흡연구역', '실내', '압구정역 실내 흡연구역입니다.', 37.5275, 127.0286, '소형', '서울특별시 강남구 압구정로 113', '테스트유저2', NULL
WHERE NOT EXISTS (SELECT 1 FROM zone WHERE address = '서울특별시 강남구 압구정로 113');

INSERT INTO zone (region, type, subtype, description, latitude, longitude, size, address, creator, image) 
SELECT '서울특별시', '흡연구역', '실외', '잠실역 근처 실외 흡연구역입니다.', 37.5133, 127.1028, '중형', '서울특별시 송파구 올림픽로 240', '테스트유저1', NULL
WHERE NOT EXISTS (SELECT 1 FROM zone WHERE address = '서울특별시 송파구 올림픽로 240');

INSERT INTO zone (region, type, subtype, description, latitude, longitude, size, address, creator, image) 
SELECT '서울특별시', '흡연구역', '실내', '여의도역 실내 흡연구역입니다.', 37.5219, 126.9242, '대형', '서울특별시 영등포구 여의대로 24', '테스트유저3', NULL
WHERE NOT EXISTS (SELECT 1 FROM zone WHERE address = '서울특별시 영등포구 여의대로 24');