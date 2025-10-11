// 백엔드의 ZoneResponse DTO와 일치하는 타입 정의
export interface SmokingZone {
  id: number
  region: string
  type: string
  subtype: string
  description: string
  latitude: number
  longitude: number
  address: string
  user: string
  image: string | null
}

// Zone 생성 시 요청 DTO에 맞는 타입 정의
export type CreateZonePayload = Omit<SmokingZone, "id" | "image">

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"

/**
 * 특정 위치 주변의 흡연구역 목록을 서버에서 가져옵니다.
 * @param lat - 검색 중심의 위도
 * @param lon - 검색 중심의 경도
 * @param radius - 검색 반경 (km)
 * @returns SmokingZone 객체의 배열
 */
export async function fetchZones(lat: number, lon: number, radius = 1.0): Promise<SmokingZone[]> {
  const response = await fetch(`${API_BASE_URL}/api/zones?latitude=${lat}&longitude=${lon}&radius=${radius}`)

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`API call failed: ${response.status} ${errorText}`)
  }

  return response.json()
}

/**
 * 새로운 흡연구역을 서버에 생성합니다.
 * @param zoneData - 생성할 흡연구역의 데이터
 * @param imageFile - 업로드할 이미지 파일 (선택 사항)
 * @returns 생성된 SmokingZone 객체
 */
export async function createZone(zoneData: CreateZonePayload, imageFile?: File): Promise<SmokingZone> {
  const formData = new FormData()

  formData.append("data", new Blob([JSON.stringify(zoneData)], { type: "application/json" }))

  if (imageFile) {
    formData.append("image", imageFile)
  }

  const response = await fetch(`${API_BASE_URL}/api/zones`, {
    method: "POST",
    body: formData,
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`API call failed: ${response.status} ${errorText}`)
  }

  return response.json()
}

// ----------------------------------------------------------------------------
// 사용자 관련 API 함수
// ----------------------------------------------------------------------------

// 백엔드의 UserResponse DTO와 일치하는 타입 정의
export interface UserProfile {
  id: number
  email: string
  nickname: string
  profileImage: string | null
  createdAt: string
}

/**
 * 사용자 프로필 정보를 가져옵니다.
 * @param userId - 조회할 사용자의 ID
 * @returns UserProfile 객체
 */
export async function fetchUserProfile(userId: number): Promise<UserProfile> {
  const response = await fetch(`${API_BASE_URL}/api/users/${userId}`)

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`API call failed: ${response.status} ${errorText}`)
  }

  return response.json()
}

/**
 * 사용자 닉네임을 업데이트합니다.
 * @param userId - 업데이트할 사용자의 ID
 * @param nickname - 새로운 닉네임
 * @returns 업데이트된 UserProfile 객체
 */
export async function updateUserNickname(userId: number, nickname: string): Promise<UserProfile> {
  const response = await fetch(`${API_BASE_URL}/api/users/${userId}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ nickname }),
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`API call failed: ${response.status} ${errorText}`)
  }

  return response.json()
}

/**
 * 사용자 프로필 이미지를 업데이트합니다.
 * @param userId - 업데이트할 사용자의 ID
 * @param imageFile - 새로운 프로필 이미지 파일
 */
export async function updateUserProfileImage(userId: number, imageFile: File): Promise<void> {
  const formData = new FormData()
  formData.append("profileImage", imageFile)

  const response = await fetch(`${API_BASE_URL}/api/users/${userId}/profile-image`, {
    method: "PUT",
    body: formData,
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`API call failed: ${response.status} ${errorText}`)
  }
}

// ----------------------------------------------------------------------------
// 하위 호환성을 위한 ApiService 클래스 (기존 코드와의 호환성 유지)
// ----------------------------------------------------------------------------

export interface ZoneRequest {
  region: string
  type: string
  subtype: string
  description: string
  latitude: number
  longitude: number
  size: string
  address: string
  user: string
  image?: string
}

class ApiService {
  async createZone(zoneData: ZoneRequest, imageFile?: File): Promise<SmokingZone> {
    const payload: CreateZonePayload = {
      region: zoneData.region,
      type: zoneData.type,
      subtype: zoneData.subtype,
      description: zoneData.description,
      latitude: zoneData.latitude,
      longitude: zoneData.longitude,
      address: zoneData.address,
      user: zoneData.user,
    }
    return createZone(payload, imageFile)
  }

  async getAllZones(latitude?: number, longitude?: number, radius?: number): Promise<SmokingZone[]> {
    if (latitude !== undefined && longitude !== undefined) {
      return fetchZones(latitude, longitude, radius)
    }
    // 기본 위치로 서울 시청 사용
    return fetchZones(37.5665, 126.978, radius)
  }
}

export const apiService = new ApiService()
