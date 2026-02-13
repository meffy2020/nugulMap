import type { MapBounds, SmokingZone } from "../types"

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL || "https://api.nugulmap.com"

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
    latitude: Number(item.latitude),
    longitude: Number(item.longitude),
    address: item.address || "",
    user: item.user || "",
    image: item.image || null,
  }
}

async function safeJson(response: Response): Promise<any> {
  const text = await response.text()
  if (!text) return null
  return JSON.parse(text)
}

export async function fetchZonesByBounds(bounds: MapBounds): Promise<SmokingZone[]> {
  const url = `${API_BASE_URL}/api/zones/bounds?minLat=${bounds.minLat}&maxLat=${bounds.maxLat}&minLng=${bounds.minLng}&maxLng=${bounds.maxLng}`

  try {
    const response = await fetch(url)
    if (!response.ok) {
      return fallbackZones
    }

    const result = await safeJson(response)
    return pickZones(result?.data || result)
  } catch (error) {
    console.warn("zones fetch failed", error)
    return fallbackZones
  }
}

export async function searchZones(keyword: string): Promise<SmokingZone[]> {
  const url = `${API_BASE_URL}/api/zones/search?keyword=${encodeURIComponent(keyword)}`

  try {
    const response = await fetch(url)
    if (!response.ok) {
      return []
    }

    const result = await safeJson(response)
    return pickZones(result?.data || result)
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
