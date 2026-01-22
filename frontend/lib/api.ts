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

// 환경 변수가 있으면 최우선 사용, 없으면 브라우저에서는 운영 API 주소, 서버에서는 도커 내부 주소 사용
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL
  ? process.env.NEXT_PUBLIC_API_BASE_URL
  : typeof window !== "undefined"
    ? "https://api.nugulmap.com"
    : "http://nginx"

const MOCK_ZONES: SmokingZone[] = [
  {
    id: 1,
    region: "서울특별시",
    type: "실외",
    subtype: "공원",
    description: "시청 앞 광장 흡연구역",
    latitude: 37.5665,
    longitude: 126.978,
    address: "서울특별시 중구 태평로1가 31",
    user: "관리자",
    image: null,
  },
  {
    id: 2,
    region: "서울특별시",
    type: "실외",
    subtype: "거리",
    description: "광화문 광장 흡연구역",
    latitude: 37.572,
    longitude: 126.9769,
    address: "서울특별시 종로구 세종로",
    user: "관리자",
    image: null,
  },
]

/**
 * 지도의 특정 영역(Bounding Box) 내의 흡연구역을 가져옵니다.
 */
export async function fetchZones(minLat: number, maxLat: number, minLng: number, maxLng: number): Promise<SmokingZone[]> {
  try {
    const response = await fetch(
      `${API_BASE_URL}/api/zones/bounds?minLat=${minLat}&maxLat=${maxLat}&minLng=${minLng}&maxLng=${maxLng}`,
    )
    if (!response.ok) {
      throw new Error("Network response was not ok")
    }
    return await response.json()
  } catch (error) {
    console.error("Error fetching zones:", error)
    // 에러 시 빈 배열 반환하여 지도 작동 유지
    return []
  }
}

/**
 * 새로운 흡연구역을 서버에 생성합니다.
 * @param zoneData - 생성할 흡연구역의 데이터
 * @param imageFile - 업로드할 이미지 파일 (선택 사항)
 * @returns 생성된 SmokingZone 객체
 */
export async function createZone(zoneData: CreateZonePayload, imageFile?: File): Promise<SmokingZone> {
  try {
    const formData = new FormData()

    formData.append("data", new Blob([JSON.stringify(zoneData)], { type: "application/json" }))

    if (imageFile) {
      formData.append("image", imageFile)
    }

    const response = await fetch(`${API_BASE_URL}/api/zones`, {
      method: "POST",
      body: formData,
      credentials: "include",
    })

    if (!response.ok) {
      throw new Error(`API call failed: ${response.status}`)
    }

    return response.json()
  } catch (err) {
    console.warn("[v0] 백엔드 API 연결 실패. 임시 객체를 반환합니다:", err)
    // 임시 ID로 새 객체 생성
    return {
      id: Date.now(),
      ...zoneData,
      image: null,
    }
  }
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
  const response = await fetch(`${API_BASE_URL}/api/users/${userId}`, {
    credentials: "include",
  })

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
    credentials: "include",
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`API call failed: ${response.status} ${errorText}`)
  }

  return response.json()
}

/**
 * 회원가입 완료 처리 (닉네임 및 프로필 이미지 등록)
 */
export async function completeSignup(nickname: string, profileImage?: File): Promise<any> {
  const formData = new FormData()
  formData.append("nickname", nickname)
  if (profileImage) {
    formData.append("profileImage", profileImage)
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/signup`, {
    method: "POST",
    body: formData,
    credentials: "include",
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`API call failed: ${response.status} ${errorText}`)
  }

  return response.json()
}

/**
 * 현재 로그인된 사용자 정보 가져오기
 */
export async function getCurrentUser(): Promise<UserProfile | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
      credentials: "include",
    })

    if (!response.ok) {
      return null
    }

    const result = await response.json()
    return result.data.user
  } catch (err) {
    console.error("Failed to get current user:", err)
    return null
  }
}

/**
 * 이미지 URL 생성 헬퍼
 */
export function getImageUrl(imagePath: string | null | undefined): string | null {
  if (!imagePath) return null
  if (imagePath.startsWith("http")) return imagePath
  return `${API_BASE_URL}/api/images/${imagePath}`
}

/**
 * 사용자 등록 장소 가져오기
 */
export async function fetchUserZones(): Promise<SmokingZone[]> {
  const response = await fetch(`${API_BASE_URL}/api/zones/my`, {
    credentials: "include",
  })

  if (!response.ok) {
    throw new Error(`API call failed: ${response.status}`)
  }

  const result = await response.json()
  return result.data.zones
}

/**
 * 장소 검색 (키워드)
 */
export async function searchZones(keyword: string): Promise<SmokingZone[]> {
  const response = await fetch(`${API_BASE_URL}/api/zones/search?keyword=${encodeURIComponent(keyword)}`, {
    credentials: "include",
  })

  if (!response.ok) {
    throw new Error(`API call failed: ${response.status}`)
  }

  const result = await response.json()
  return result.data.zones
}

/**
 * 흡연구역 삭제
 */
export async function deleteZone(id: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/zones/${id}`, {
    method: "DELETE",
    credentials: "include",
  })

  if (!response.ok) {
    throw new Error(`API call failed: ${response.status}`)
  }
}

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
