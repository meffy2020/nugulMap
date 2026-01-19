# Postman 테스트 가이드

## 기본 설정

### Base URL
- **로컬 개발 환경**: `http://localhost:8080/api` (백엔드 직접 접근)
- **Docker 환경**: `http://localhost/api` (Nginx를 통한 접근)

### Context Path
모든 API는 `/api`로 시작합니다.

---

## 테스트 시나리오

### 1. 로그인 (OAuth2)

**주의**: OAuth2는 브라우저 리다이렉트 방식이므로 Postman에서 직접 테스트하기 어렵습니다.

#### 방법 1: 브라우저에서 로그인 후 쿠키 복사
1. 브라우저에서 `http://localhost/api/oauth2/authorization/kakao` 접속
2. OAuth 로그인 완료 후 브라우저 개발자 도구에서 쿠키 확인
3. `JSESSIONID` 또는 JWT 토큰 쿠키를 Postman에 추가

#### 방법 2: Postman에서 OAuth2 인증 사용
1. Postman의 **Authorization** 탭 선택
2. Type: **OAuth 2.0** 선택
3. Grant Type: **Authorization Code** 선택
4. Auth URL: `http://localhost/api/oauth2/authorization/kakao`
5. Access Token URL: `http://localhost/api/login/oauth2/code/kakao`
6. Client ID: 카카오 클라이언트 ID 입력
7. **Get New Access Token** 클릭하여 로그인

---

### 2. My Page 조회 (현재 사용자 정보)

#### Request
```
GET http://localhost:8080/api/auth/me
```

#### Headers
```
Cookie: JSESSIONID=your-session-id
```
또는
```
Authorization: Bearer your-jwt-token
```

#### Response 예시
```json
{
  "success": true,
  "message": "현재 사용자 정보 조회 성공",
  "data": {
    "user": {
      "id": 1,
      "email": "user@example.com",
      "nickname": "테스트유저",
      "profileImage": "profile123.jpg",
      "createdAt": "2024-01-01T00:00:00"
    }
  }
}
```

#### 특정 사용자 조회
```
GET http://localhost:8080/api/users/{userId}
```

---

### 3. Zone 생성 (POST)

#### Request
```
POST http://localhost:8080/api/zones
Content-Type: multipart/form-data
```

#### Headers
```
Cookie: JSESSIONID=your-session-id
```
또는
```
Authorization: Bearer your-jwt-token
```

#### Body (form-data)
| Key | Type | Value |
|-----|------|-------|
| `data` | Text | JSON 문자열 (아래 참고) |
| `image` | File | (선택) 이미지 파일 |

**data 필드의 JSON 예시:**
```json
{
  "region": "서울특별시",
  "type": "흡연구역",
  "subtype": "실외",
  "description": "테스트 흡연구역입니다",
  "latitude": 37.5665,
  "longitude": 126.9780,
  "address": "서울특별시 중구 세종대로 110",
  "user": "테스트유저"
}
```

#### Postman 설정 방법
1. **Body** 탭 선택
2. **form-data** 선택
3. `data` 키 추가 → Type: **Text** → 위 JSON 문자열 입력
4. `image` 키 추가 → Type: **File** → 이미지 파일 선택 (선택사항)

#### Response 예시
```json
{
  "success": true,
  "message": "흡연구역 생성 성공",
  "data": {
    "zone": {
      "id": 1,
      "region": "서울특별시",
      "type": "흡연구역",
      "subtype": "실외",
      "description": "테스트 흡연구역입니다",
      "latitude": 37.5665,
      "longitude": 126.9780,
      "address": "서울특별시 중구 세종대로 110",
      "user": "테스트유저",
      "image": "zone123.jpg"
    },
    "image": "uploaded"
  }
}
```

---

### 4. My Page 조회 (생성된 Zone 확인)

생성된 Zone의 `user` 필드가 현재 사용자와 일치하는지 확인합니다.

#### Request
```
GET http://localhost:8080/api/users/{userId}
```

#### Headers
```
Cookie: JSESSIONID=your-session-id
```

#### Response에서 확인할 사항
- 사용자 정보와 생성한 Zone의 `user` 필드가 일치하는지 확인
- (참고: 현재 UserResponse에는 Zone 목록이 포함되지 않을 수 있음)

---

### 5. Zone 삭제

#### Request
```
DELETE http://localhost:8080/api/zones/{zoneId}
```

#### Headers
```
Cookie: JSESSIONID=your-session-id
```
또는
```
Authorization: Bearer your-jwt-token
```

#### Response 예시
```json
{
  "success": true,
  "message": "흡연구역 삭제 성공",
  "data": {
    "deletedZoneId": 1
  }
}
```

---

### 6. Zone 조회 (삭제 확인)

#### Request
```
GET http://localhost:8080/api/zones/{zoneId}
```

#### Headers
```
(인증 불필요 - GET /zones/**는 공개 엔드포인트)
```

#### Response 예시 (존재하는 경우)
```json
{
  "success": true,
  "message": "흡연구역 조회 성공",
  "data": {
    "zone": {
      "id": 1,
      ...
    }
  }
}
```

#### Response 예시 (삭제된 경우)
```json
{
  "success": false,
  "message": "흡연구역을 찾을 수 없습니다",
  "error": "ZONE_NOT_FOUND"
}
```

---

## 전체 테스트 플로우 요약

1. **로그인** → 브라우저에서 OAuth 로그인 후 쿠키/토큰 복사
2. **My Page 조회** → `GET /api/auth/me` 또는 `GET /api/users/{id}`
3. **Zone 생성** → `POST /api/zones` (multipart/form-data)
4. **My Page 조회** → 생성된 Zone 확인
5. **Zone 삭제** → `DELETE /api/zones/{zoneId}`
6. **Zone 조회** → 삭제 확인 (`GET /api/zones/{zoneId}`)

---

## Postman Collection 설정 팁

### Environment Variables 설정
1. Postman에서 **Environments** 생성
2. 변수 추가:
   - `base_url`: `http://localhost:8080/api`
   - `session_id`: (로그인 후 업데이트)
   - `user_id`: (조회 후 업데이트)
   - `zone_id`: (생성 후 업데이트)

### Pre-request Script 예시
```javascript
// 쿠키 자동 추가
pm.request.headers.add({
    key: 'Cookie',
    value: 'JSESSIONID=' + pm.environment.get('session_id')
});
```

### Tests Script 예시 (Zone 생성 후)
```javascript
// 응답에서 zone ID 추출하여 환경 변수에 저장
if (pm.response.code === 201) {
    const response = pm.response.json();
    pm.environment.set('zone_id', response.data.zone.id);
}
```

---

## 주의사항

1. **인증**: 대부분의 엔드포인트는 인증이 필요합니다 (쿠키 또는 JWT 토큰)
2. **Content-Type**: Zone 생성 시 `multipart/form-data` 사용 필수
3. **CORS**: 브라우저에서 직접 접근 시 CORS 설정 확인 필요
4. **OAuth2**: Postman에서 직접 테스트하기 어려우므로 브라우저에서 로그인 후 쿠키 복사 권장

---

## 문제 해결

### 401 Unauthorized
- 쿠키 또는 토큰이 유효한지 확인
- 브라우저에서 다시 로그인하여 새로운 세션 ID 획득

### 404 Not Found
- URL에 `/api` prefix가 포함되어 있는지 확인
- Context path가 올바르게 설정되어 있는지 확인

### 400 Bad Request (Zone 생성 시)
- `data` 필드가 JSON 문자열 형식인지 확인
- 필수 필드(`address`, `latitude`, `longitude` 등)가 모두 포함되어 있는지 확인
