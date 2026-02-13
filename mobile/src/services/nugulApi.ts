import type { MapBounds, SmokingZone, UserProfile } from "../types"

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL || "https://api.nugulmap.com"

export interface CreateZonePayload {
  region: string
  type: string
  subtype: string
  description: string
  latitude: number
  longitude: number
  size?: string
  address: string
  user: string
}

const fallbackZones: SmokingZone[] = [
  {
    id: 1,
    region: "서울특별시",
    type: "실외",
    subtype: "공원",
    description: "시청 근처 흡연구역",
    latitude: 37.5665,
    longitude: 126.978,
    address: "서울특별시 중구 세종대로 1가",
    user: "관리자",
    image: null
  },
  {
    id: 2,
    region: "서울특별시",
    type: "실외",
    subtype: "거리",
    description: "광화문 주변 흡연구역",
    latitude: 37.572,
    longitude: 126.9769,
    address: "서울특별시 종로구 세종로 1",
    user: "관리자",
    image: null
  },
]

function pickZones(payload: any): SmokingZone[] {
  if (Array.isArray(payload)) {
    return payload.map((zone) => toZone(zone))
  }

  if (payload && Array.isArray(payload.zones)) {
    return payload.zones.map((zone: SmokingZone) => toZone(zone))
  }

  if (payload && Array.isArray(payload.data?.zones)) {
    return payload.data.zones.map((zone: SmokingZone) => toZone(zone))
  }

  return []
}

function toZone(item: SmokingZone): SmokingZone {
  return {
    id: Number(item.id),
    region: item.region || "",
    type: item.type || "",
    subtype: item.subtype || "",
    description: item.description || "",
    size: item.size || "",
    date: item.date ? String(item.date) : undefined,
    latitude: Number(item.latitude),
    longitude: Number(item.longitude),
    address: item.address || "",
    user: item.user || "",
    image: item.image || null,
  }
}

function authHeaders(token?: string): HeadersInit {
  if (!token) return {}
  return { Authorization: `Bearer ${token}` }
}

async function safeJson(response: Response): Promise<any> {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

async function readZonesOrEmpty(response: Response): Promise<SmokingZone[]> {
  const result = await safeJson(response)
  return pickZones(result?.data || result)
}

export async function fetchZonesByBounds(bounds: MapBounds): Promise<SmokingZone[]> {
  const url = `${API_BASE_URL}/api/zones/bounds?minLat=${bounds.minLat}&maxLat=${bounds.maxLat}&minLng=${bounds.minLng}&maxLng=${bounds.maxLng}`

  try {
    const response = await fetch(url)
    if (!response.ok) {
      return fallbackZones
    }

    return readZonesOrEmpty(response)
  } catch (error) {
    console.warn("zones fetch failed", error)
    return fallbackZones
  }
}

export async function searchZones(keyword: string, lat?: number, lng?: number): Promise<SmokingZone[]> {
  let url = `${API_BASE_URL}/api/zones/search?keyword=${encodeURIComponent(keyword)}`
  if (lat !== undefined && lng !== undefined) {
    url += `&lat=${lat}&lng=${lng}`
  }

  try {
    const response = await fetch(url)
    if (!response.ok) {
      return []
    }

    return readZonesOrEmpty(response)
  } catch {
    return []
  }
}

export async function fetchZoneById(id: number): Promise<SmokingZone | null> {
  const url = `${API_BASE_URL}/api/zones/${id}`

  try {
    const response = await fetch(url)
    if (!response.ok) {
      return null
    }

    const result = await safeJson(response)
    const raw = result?.data?.zone || result?.zone
    return raw ? toZone(raw) : null
  } catch {
    return null
  }
}

export async function createZone(payload: CreateZonePayload, token?: string): Promise<SmokingZone> {
  const formData = new FormData()
  formData.append("data", JSON.stringify(payload))

  const response = await fetch(`${API_BASE_URL}/api/zones`, {
    method: "POST",
    headers: authHeaders(token),
    body: formData,
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`zone create failed: ${response.status} ${text}`)
  }

  const result = await safeJson(response)
  const raw = result?.data?.zone || result?.zone
  if (!raw) {
    throw new Error("invalid zone create response")
  }
  return toZone(raw)
}

export async function getCurrentUser(token?: string): Promise<UserProfile | null> {
  if (!token) return null

  const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
    headers: authHeaders(token),
  })

  if (!response.ok) return null
  const result = await safeJson(response)
  return result?.data?.user || null
}

export async function validateToken(token: string): Promise<boolean> {
  const response = await fetch(`${API_BASE_URL}/api/auth/validate`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ token }),
  })

  if (!response.ok) return false
  const result = await safeJson(response)
  return Boolean(result?.valid)
}

export async function fetchMyZones(token?: string): Promise<SmokingZone[]> {
  if (!token) return []

  const response = await fetch(`${API_BASE_URL}/api/zones/my`, {
    headers: authHeaders(token),
  })

  if (!response.ok) {
    return []
  }

  return readZonesOrEmpty(response)
}

export async function deleteZone(id: number, token?: string): Promise<void> {
  if (!token) {
    throw new Error("auth token required")
  }

  const response = await fetch(`${API_BASE_URL}/api/zones/${id}`, {
    method: "DELETE",
    headers: authHeaders(token),
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`delete zone failed: ${response.status} ${text}`)
  }
}

export function getImageUrl(imagePath: string | null | undefined): string | null {
  if (!imagePath) return null
  if (imagePath.startsWith("http")) return imagePath
  if (imagePath.startsWith("/")) return `${API_BASE_URL}${imagePath}`
  return `${API_BASE_URL}/api/images/${imagePath}`
}
