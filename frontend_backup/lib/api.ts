// frontend/lib/api.ts

import { API_BASE_URL, API_ENDPOINTS } from './config'

// 백엔드 응답 형식 타입 정의
interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

/**
 * 이미지 파일명을 API URL로 변환합니다.
 * @param filename - 이미지 파일명 (예: "profile20251230_143056_570ea469.jpg")
 * @returns 이미지 API URL (예: "/api/images/profile20251230_143056_570ea469.jpg")
 */
export function getImageUrl(filename: string | null | undefined): string | null {
  if (!filename) {
    return null;
  }
  // 이미 전체 URL인 경우 그대로 반환
  if (filename.startsWith('http://') || filename.startsWith('https://') || filename.startsWith(API_BASE_URL)) {
    return filename;
  }
  // 파일명만 있는 경우 API 경로로 변환
  return API_ENDPOINTS.IMAGES.BY_FILENAME(filename);
}

// 백엔드의 ZoneResponse DTO와 일치하는 타입 정의
export interface SmokingZone {
  id: number;
  region: string;
  type: string;
  subtype: string;
  description: string;
  latitude: number;
  longitude: number;
  address: string;
  user: string;
  image: string | null;
}

// Zone 생성 시 요청 DTO에 맞는 타입 정의
export type CreateZonePayload = Omit<SmokingZone, 'id' | 'image'>;

/**
 * 특정 위치 주변의 흡연구역 목록을 서버에서 가져옵니다.
 * @param lat - 검색 중심의 위도
 * @param lon - 검색 중심의 경도
 * @param radius - 검색 반경 (km)
 * @returns SmokingZone 객체의 배열
 */
export async function fetchZones(lat: number, lon: number, radius: number = 1.0): Promise<SmokingZone[]> {
  const response = await fetch(API_ENDPOINTS.ZONES.QUERY({ lat, lon, radius }), {
    credentials: 'include', // 쿠키 포함
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API call failed: ${response.status} ${errorText}`);
  }

  const result: ApiResponse<{ zones: SmokingZone[]; count: number }> = await response.json();
  return result.data.zones;
}

/**
 * 새로운 흡연구역을 서버에 생성합니다.
 * @param zoneData - 생성할 흡연구역의 데이터
 * @param imageFile - 업로드할 이미지 파일 (선택 사항)
 * @returns 생성된 SmokingZone 객체
 */
export async function createZone(zoneData: CreateZonePayload, imageFile?: File): Promise<SmokingZone> {
  const formData = new FormData();

  formData.append('data', new Blob([JSON.stringify(zoneData)], { type: 'application/json' }));

  if (imageFile) {
    formData.append('image', imageFile);
  }

  const response = await fetch(API_ENDPOINTS.ZONES.BASE, {
    method: 'POST',
    body: formData,
    credentials: 'include', // 쿠키 포함
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API call failed: ${response.status} ${errorText}`);
  }

  const result: ApiResponse<{ zone: SmokingZone }> = await response.json();
  return result.data.zone;
}

// ----------------------------------------------------------------------------
// 사용자 관련 API 함수
// ----------------------------------------------------------------------------

// 백엔드의 UserResponse DTO와 일치하는 타입 정의
export interface UserProfile {
  id: number; // 백엔드는 Long이지만, JS에서는 number로 처리
  email: string;
  nickname: string;
  profileImage: string | null; // 백엔드는 profileImage, 프론트엔드는 profileImageUrl로 사용 가능
  createdAt: string;
}

/**
 * 사용자 프로필 정보를 가져옵니다.
 * @param userId - 조회할 사용자의 ID
 * @returns UserProfile 객체
 */
export async function fetchUserProfile(userId: number): Promise<UserProfile> {
  const response = await fetch(API_ENDPOINTS.USERS.BY_ID(userId), {
    credentials: 'include', // 쿠키 포함
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API call failed: ${response.status} ${errorText}`);
  }

  // UserController는 UserResponse를 직접 반환 (X-Message 헤더 사용)
  const data = await response.json();
  // UserResponse 직접 반환 형식
  return data as UserProfile;
}

/**
 * 사용자 닉네임을 업데이트합니다.
 * @param userId - 업데이트할 사용자의 ID
 * @param nickname - 새로운 닉네임
 * @returns 업데이트된 UserProfile 객체
 */
export async function updateUserNickname(userId: number, nickname: string): Promise<UserProfile> {
  // 백엔드 UserController는 multipart/form-data를 기대하므로 FormData 사용
  const formData = new FormData();
  formData.append('userData', JSON.stringify({ nickname }));

  const response = await fetch(API_ENDPOINTS.USERS.BY_ID(userId), {
    method: 'PUT',
    body: formData,
    credentials: 'include', // 쿠키 포함
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API call failed: ${response.status} ${errorText}`);
  }

  const result: ApiResponse<{ user: UserProfile }> = await response.json();
  return result.data.user;
}

/**
 * 사용자 프로필 이미지를 업데이트합니다.
 * @param userId - 업데이트할 사용자의 ID
 * @param imageFile - 새로운 프로필 이미지 파일
 */
export async function updateUserProfileImage(userId: number, imageFile: File): Promise<void> {
  const formData = new FormData();
  formData.append('profileImage', imageFile);

  const response = await fetch(API_ENDPOINTS.USERS.PROFILE_IMAGE(userId), {
    method: 'PUT',
    body: formData,
    credentials: 'include', // 쿠키 포함
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API call failed: ${response.status} ${errorText}`);
  }
  // 백엔드는 Map<String, String>을 반환하지만, 여기서는 void로 처리하고 성공 여부만 확인
  // 실제 업데이트된 정보를 얻으려면 fetchUserProfile을 다시 호출해야 함
}

// ----------------------------------------------------------------------------
// 인증 관련 API 함수
// ----------------------------------------------------------------------------

/**
 * 현재 인증된 사용자 정보를 가져옵니다.
 * @returns UserProfile 객체 또는 null (인증되지 않은 경우)
 */
export async function getCurrentUser(): Promise<UserProfile | null> {
  try {
    const response = await fetch(API_ENDPOINTS.AUTH.ME, {
      credentials: 'include', // 쿠키 포함
    });

    if (!response.ok) {
      if (response.status === 401) {
        return null; // 인증되지 않음
      }
      const errorText = await response.text();
      throw new Error(`API call failed: ${response.status} ${errorText}`);
    }

    const result: ApiResponse<{ user: UserProfile }> = await response.json();
    return result.data.user;
  } catch (error) {
    console.error('Failed to get current user:', error);
    return null;
  }
}

/**
 * 회원가입 완료 API (프로필 설정)
 * @param nickname - 닉네임
 * @param profileImage - 프로필 이미지 파일 (선택)
 * @returns 완료된 사용자 정보
 */
export async function completeSignup(nickname: string, profileImage?: File): Promise<UserProfile> {
  const formData = new FormData();
  formData.append('nickname', nickname);
  if (profileImage) {
    formData.append('profileImage', profileImage);
  }

  const response = await fetch(API_ENDPOINTS.AUTH.SIGNUP, {
    method: 'POST',
    body: formData,
    credentials: 'include', // 쿠키 포함
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API call failed: ${response.status} ${errorText}`);
  }

  const result: ApiResponse<{ user: UserProfile }> = await response.json();
  return result.data.user;
}

/**
 * 키워드로 흡연구역을 검색합니다.
 * @param keyword - 검색 키워드 (주소, 지역, 타입 등)
 * @returns SmokingZone 객체의 배열
 */
export async function searchZones(keyword: string): Promise<SmokingZone[]> {
  if (!keyword || keyword.trim().length === 0) {
    return [];
  }

  const response = await fetch(`${API_ENDPOINTS.ZONES.SEARCH}?keyword=${encodeURIComponent(keyword.trim())}`, {
    credentials: 'include', // 쿠키 포함
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API call failed: ${response.status} ${errorText}`);
  }

  // TestController의 응답 형식: { success: true, message: "...", zones: [...], keyword: "..." }
  const result = await response.json();
  if (result.success && result.zones) {
    return result.zones as SmokingZone[];
  }
  
  // ApiResponse 형식도 지원
  if (result.data && result.data.zones) {
    return result.data.zones as SmokingZone[];
  }
  
  return [];
}

/**
 * 현재 인증된 사용자가 등록한 흡연구역 목록을 가져옵니다.
 * @returns SmokingZone 객체의 배열
 */
export async function fetchUserZones(): Promise<SmokingZone[]> {
  const response = await fetch(API_ENDPOINTS.ZONES.MY, {
    credentials: 'include', // 쿠키 포함
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API call failed: ${response.status} ${errorText}`);
  }

  const result: ApiResponse<{ zones: SmokingZone[]; count: number }> = await response.json();
  return result.data.zones || [];
}

/**
 * 로그아웃 처리
 * 쿠키에 저장된 인증 토큰을 삭제합니다.
 */
export async function logout(): Promise<void> {
  try {
    // 백엔드에 로그아웃 요청 (쿠키 삭제)
    await fetch(API_ENDPOINTS.AUTH.LOGOUT, {
      method: 'POST',
      credentials: 'include', // 쿠키 포함
    });
  } catch (error) {
    console.error('로그아웃 API 호출 실패:', error);
    // API 호출 실패해도 클라이언트 측에서는 계속 진행
  }
  
  // 클라이언트 측에서도 쿠키 삭제 시도 (만약 있다면)
  // document.cookie를 직접 조작하는 것은 보안상 권장되지 않지만,
  // 백엔드에서 쿠키를 삭제하지 못한 경우를 대비
}

// ----------------------------------------------------------------------------
// 테스트 API 함수 (TestController)
// ----------------------------------------------------------------------------

/**
 * 테스트 API 기본 URL
 */
const TEST_API_BASE = API_ENDPOINTS.TEST.BASE;

/**
 * 테스트 API 응답 타입
 */
export interface TestApiResponse<T = any> {
  success: boolean;
  message: string;
  [key: string]: any;
}

/**
 * 헬스 체크
 */
export async function testHealthCheck(): Promise<TestApiResponse> {
  const response = await fetch(API_ENDPOINTS.TEST.HEALTH, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Health check failed: ${response.status}`);
  }
  return response.json();
}

/**
 * 사용자 목록 조회 (테스트)
 */
export async function testGetUsers(): Promise<TestApiResponse> {
  const response = await fetch(API_ENDPOINTS.TEST.USERS, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Get users failed: ${response.status}`);
  }
  return response.json();
}

/**
 * 사용자 생성 (테스트)
 */
export async function testCreateUser(userData: {
  email: string;
  nickname: string;
  password?: string;
}): Promise<TestApiResponse> {
  const response = await fetch(API_ENDPOINTS.TEST.USERS, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify(userData),
  });
  if (!response.ok) {
    throw new Error(`Create user failed: ${response.status}`);
  }
  return response.json();
}

/**
 * 사용자 조회 (테스트)
 */
export async function testGetUser(userId: number): Promise<TestApiResponse> {
  const response = await fetch(`${API_ENDPOINTS.TEST.USERS}/${userId}`, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Get user failed: ${response.status}`);
  }
  return response.json();
}

/**
 * Zone 목록 조회 (테스트)
 */
export async function testGetZones(): Promise<TestApiResponse> {
  const response = await fetch(API_ENDPOINTS.TEST.ZONES, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Get zones failed: ${response.status}`);
  }
  return response.json();
}

/**
 * Zone 생성 (테스트)
 */
export async function testCreateZone(formData: FormData): Promise<TestApiResponse> {
  const response = await fetch(API_ENDPOINTS.TEST.ZONES, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  });
  if (!response.ok) {
    throw new Error(`Create zone failed: ${response.status}`);
  }
  return response.json();
}

/**
 * Zone 조회 (테스트)
 */
export async function testGetZone(zoneId: number): Promise<TestApiResponse> {
  const response = await fetch(`${API_ENDPOINTS.TEST.ZONES}/${zoneId}`, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Get zone failed: ${response.status}`);
  }
  return response.json();
}

/**
 * Zone 검색 (테스트)
 */
export async function testSearchZones(keyword: string): Promise<TestApiResponse> {
  const response = await fetch(`${API_ENDPOINTS.TEST.ZONES}/search?keyword=${encodeURIComponent(keyword)}`, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Search zones failed: ${response.status}`);
  }
  return response.json();
}

/**
 * 반경 검색 (테스트)
 */
export async function testSearchNearbyZones(
  lat: number,
  lon: number,
  radius: number = 1000
): Promise<TestApiResponse> {
  const response = await fetch(
    `${API_ENDPOINTS.TEST.ZONES}/nearby?lat=${lat}&lon=${lon}&radius=${radius}`,
    {
      credentials: 'include',
    }
  );
  if (!response.ok) {
    throw new Error(`Search nearby zones failed: ${response.status}`);
  }
  return response.json();
}

/**
 * 이미지 업로드 (테스트)
 */
export async function testUploadImage(image: File, type: string): Promise<TestApiResponse> {
  const formData = new FormData();
  formData.append('image', image);
  formData.append('type', type);

  const response = await fetch(`${API_ENDPOINTS.TEST.BASE}/images/upload`, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  });
  if (!response.ok) {
    throw new Error(`Upload image failed: ${response.status}`);
  }
  return response.json();
}

/**
 * Storage 테스트
 */
export async function testStorage(file: File): Promise<TestApiResponse> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`${API_ENDPOINTS.TEST.BASE}/storage/test`, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  });
  if (!response.ok) {
    throw new Error(`Storage test failed: ${response.status}`);
  }
  return response.json();
}

/**
 * OAuth2 환경 변수 확인 (테스트)
 */
export async function testCheckOAuth2Env(): Promise<TestApiResponse> {
  const response = await fetch(API_ENDPOINTS.TEST.OAUTH2.ENV_CHECK, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`OAuth2 env check failed: ${response.status}`);
  }
  return response.json();
}

/**
 * OAuth2 로그인 URL 조회 (테스트)
 */
export async function testGetOAuth2LoginUrls(): Promise<TestApiResponse> {
  const response = await fetch(API_ENDPOINTS.TEST.OAUTH2.LOGIN_URLS, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Get OAuth2 login URLs failed: ${response.status}`);
  }
  return response.json();
}

/**
 * 토큰 검증 (테스트)
 */
export async function testValidateToken(token: string): Promise<TestApiResponse> {
  const response = await fetch(API_ENDPOINTS.AUTH.VALIDATE, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify({ token }),
  });
  if (!response.ok) {
    throw new Error(`Validate token failed: ${response.status}`);
  }
  return response.json();
}

/**
 * 토큰 재발급 (테스트)
 */
export async function testRefreshToken(refreshToken: string): Promise<TestApiResponse> {
  const response = await fetch(API_ENDPOINTS.AUTH.REFRESH, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify({ refreshToken }),
  });
  if (!response.ok) {
    throw new Error(`Refresh token failed: ${response.status}`);
  }
  return response.json();
}

/**
 * 현재 사용자 정보 조회 (테스트)
 */
export async function testGetCurrentUser(): Promise<TestApiResponse> {
  const response = await fetch(API_ENDPOINTS.AUTH.ME, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Get current user failed: ${response.status}`);
  }
  return response.json();
}

/**
 * 쿠키 정보 조회 (테스트)
 */
export async function testGetCookies(): Promise<TestApiResponse> {
  const response = await fetch(`${API_ENDPOINTS.TEST.BASE}/auth/cookies`, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`Get cookies failed: ${response.status}`);
  }
  return response.json();
}
