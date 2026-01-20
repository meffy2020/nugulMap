#!/bin/bash

# API 기본 URL
API_URL="http://localhost:8080"
# Nginx 사용 시
# API_URL="http://localhost"

echo "=== 너굴맵 API 시나리오 테스트 시작 ==="
echo "Target URL: $API_URL"

# 1. 사용자 목록 조회
echo -e "\n[1] 사용자 목록 조회 (/api/test/users)"
curl -s "$API_URL/api/test/users" | grep -o '"success":true' > /dev/null && echo "✅ 성공" || echo "❌ 실패"

# 2. 테스트 사용자 생성
echo -e "\n[2] 테스트 사용자 생성 (/api/test/users)"
USER_PAYLOAD='{"email":"test_user@example.com", "nickname":"테스트유저", "oauthProvider":"google", "role":"USER"}'
curl -s -X POST "$API_URL/api/test/users" \
  -H "Content-Type: application/json" \
  -d "$USER_PAYLOAD" | grep -o '"success":true' > /dev/null && echo "✅ 성공" || echo "❌ 실패 (이미 존재할 수 있음)"

# 3. 흡연구역 생성 (이미지 없이)
echo -e "\n[3] 흡연구역 생성 (/api/test/zones)"
# Multipart 요청을 위해 curl -F 사용
curl -s -X POST "$API_URL/api/test/zones" \
  -F "address=서울시 중구 세종대로 110" \
  -F "description=서울시청 앞 흡연구역" \
  -F "region=서울특별시" \
  -F "type=실외" \
  -F "subtype=흡연부스" \
  -F "latitude=37.5665" \
  -F "longitude=126.9780" \
  -F "creator=test_user@example.com" | grep -o '"success":true' > /dev/null && echo "✅ 성공" || echo "❌ 실패"

# 4. 흡연구역 목록 조회
echo -e "\n[4] 흡연구역 목록 조회 (/api/test/zones)"
curl -s "$API_URL/api/test/zones" | grep -o '"success":true' > /dev/null && echo "✅ 성공" || echo "❌ 실패"

# 5. 반경 검색 테스트
echo -e "\n[5] 반경 검색 테스트 (/api/test/zones/nearby)"
curl -s "$API_URL/api/test/zones/nearby?lat=37.5665&lon=126.9780&radius=500" | grep -o '"success":true' > /dev/null && echo "✅ 성공" || echo "❌ 실패"

echo -e "\n=== 테스트 완료 ==="
