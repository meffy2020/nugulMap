/**
 * API 설정 및 환경 변수 관리
 * Nginx 80 포트 기준으로 설정됨
 */

/**
 * API 베이스 URL
 * 환경 변수 NEXT_PUBLIC_API_BASE_URL이 설정되어 있으면 사용하고,
 * 없으면 기본값으로 상대 경로 '/api'를 사용 (Nginx를 통해 자동 프록시됨)
 */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || '/api'

/**
 * API 엔드포인트 경로
 */
export const API_ENDPOINTS = {
  // 인증 관련
  AUTH: {
    ME: `${API_BASE_URL}/auth/me`,
    SIGNUP: `${API_BASE_URL}/auth/signup`,
    LOGOUT: `${API_BASE_URL}/auth/logout`,
    REFRESH: `${API_BASE_URL}/auth/refresh`,
    VALIDATE: `${API_BASE_URL}/auth/validate`,
  },
  // OAuth2 관련
  OAUTH2: {
    AUTHORIZATION: (provider: string) => `${API_BASE_URL}/oauth2/authorization/${provider}`,
  },
  // 사용자 관련
  USERS: {
    BASE: `${API_BASE_URL}/users`,
    BY_ID: (userId: number) => `${API_BASE_URL}/users/${userId}`,
    PROFILE_IMAGE: (userId: number) => `${API_BASE_URL}/users/${userId}/profile-image`,
  },
  // Zone 관련
  ZONES: {
    BASE: `${API_BASE_URL}/zones`,
    SEARCH: `${API_BASE_URL}/zones/search`,
    MY: `${API_BASE_URL}/zones/my`,
    NEARBY: `${API_BASE_URL}/zones/nearby`,
    BY_ID: (zoneId: number) => `${API_BASE_URL}/zones/${zoneId}`,
    QUERY: (params: { lat?: number; lon?: number; radius?: number }) => {
      const queryParams = new URLSearchParams()
      if (params.lat !== undefined) queryParams.append('latitude', params.lat.toString())
      if (params.lon !== undefined) queryParams.append('longitude', params.lon.toString())
      if (params.radius !== undefined) queryParams.append('radius', params.radius.toString())
      return `${API_BASE_URL}/zones?${queryParams.toString()}`
    },
  },
  // 이미지 관련
  IMAGES: {
    BASE: `${API_BASE_URL}/images`,
    BY_FILENAME: (filename: string) => `${API_BASE_URL}/images/${filename}`,
  },
  // 테스트 API
  TEST: {
    BASE: `${API_BASE_URL}/test`,
    HEALTH: `${API_BASE_URL}/test/health`,
    USERS: `${API_BASE_URL}/test/users`,
    ZONES: `${API_BASE_URL}/test/zones`,
    OAUTH2: {
      ENV_CHECK: `${API_BASE_URL}/test/oauth2/env-check`,
      LOGIN_URLS: `${API_BASE_URL}/test/oauth2/login-urls`,
    },
  },
} as const
