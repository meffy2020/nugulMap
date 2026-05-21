import type { MapBounds, SmokingZone, UserProfile, ZoneReview } from "../types"

const API_BASE_URL = (process.env.EXPO_PUBLIC_API_BASE_URL || "https://api.nugulmap.com").replace(/\/+$/, "")
const REQUEST_TIMEOUT_MS = 10_000

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

export type UpdateZonePayload = CreateZonePayload

export interface UploadImageAsset {
  uri: string
  name?: string | null
  type?: string | null
}

export interface CreateZoneReviewPayload {
  content: string
}

export interface ProfilePayload {
  nickname: string
}

type UnknownRecord = Record<string, unknown>

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
    image: null,
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
    image: null,
  },
]

function asRecord(value: unknown): UnknownRecord | null {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as UnknownRecord
  }
  return null
}

function readString(value: unknown, fallback = ""): string {
  if (typeof value === "string") return value
  if (value == null) return fallback
  return String(value)
}

function readNumber(value: unknown, fallback = 0): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function extractPayloadData(payload: unknown): unknown {
  const record = asRecord(payload)
  if (!record) return payload

  if ("data" in record) {
    return record.data
  }

  return payload
}

function pickZones(payload: unknown): SmokingZone[] {
  if (Array.isArray(payload)) {
    return payload.map((zone) => toZone(zone)).filter((zone) => zone.id > 0)
  }

  const record = asRecord(payload)
  if (!record) return []

  if (Array.isArray(record.zones)) {
    return record.zones.map((zone) => toZone(zone)).filter((zone) => zone.id > 0)
  }

  return []
}

function pickReviews(payload: unknown): ZoneReview[] {
  if (Array.isArray(payload)) {
    return payload.map((review) => toZoneReview(review)).filter((review) => review.id > 0)
  }

  const record = asRecord(payload)
  if (!record) return []

  if (Array.isArray(record.reviews)) {
    return record.reviews.map((review) => toZoneReview(review)).filter((review) => review.id > 0)
  }

  return []
}

function toZone(item: unknown): SmokingZone {
  const record = asRecord(item) || {}

  return {
    id: readNumber(record.id),
    region: readString(record.region),
    type: readString(record.type),
    subtype: readString(record.subtype),
    description: readString(record.description),
    size: readString(record.size),
    date: record.date ? readString(record.date) : undefined,
    latitude: readNumber(record.latitude),
    longitude: readNumber(record.longitude),
    address: readString(record.address),
    user: readString(record.user),
    image: typeof record.image === "string" ? record.image : null,
  }
}

function toZoneReview(item: unknown): ZoneReview {
  const record = asRecord(item) || {}
  const createdAt = readString(record.createdAt, new Date().toISOString())

  return {
    id: readNumber(record.id),
    zoneId: readNumber(record.zoneId),
    authorId: record.authorId == null ? null : readNumber(record.authorId),
    authorNickname: readString(record.authorNickname, readString(record.authorEmail, "익명")),
    authorEmail: readString(record.authorEmail),
    authorProfileImage: typeof record.authorProfileImage === "string" ? record.authorProfileImage : null,
    content: readString(record.content),
    createdAt,
    updatedAt: readString(record.updatedAt, createdAt),
  }
}

function authHeaders(token?: string): HeadersInit {
  if (!token) return {}
  return { Authorization: `Bearer ${token}` }
}

function appendFileToFormData(
  formData: FormData,
  fieldName: string,
  imageAsset?: UploadImageAsset | null,
  fallbackPrefix = "file",
) {
  if (!imageAsset?.uri) return

  const normalizedName = imageAsset.name?.trim() || `${fallbackPrefix}-${Date.now()}.jpg`
  const normalizedType = imageAsset.type?.trim() || "image/jpeg"
  formData.append(
    fieldName,
    {
      uri: imageAsset.uri,
      name: normalizedName,
      type: normalizedType,
    } as unknown as Blob,
  )
}

function appendImageToFormData(formData: FormData, imageAsset?: UploadImageAsset | null) {
  appendFileToFormData(formData, "image", imageAsset, "zone")
}

function appendProfileImageToFormData(formData: FormData, imageAsset?: UploadImageAsset | null) {
  appendFileToFormData(formData, "profileImage", imageAsset, "profile")
}

async function safeJson(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

async function fetchWithTimeout(url: string, init?: RequestInit): Promise<Response> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    return await fetch(url, {
      ...init,
      signal: controller.signal,
    })
  } finally {
    clearTimeout(timer)
  }
}

function getResponseStatus(response: Response): number {
  return typeof response.status === "number" && Number.isFinite(response.status) ? response.status : 0
}

function pickMessage(payload: unknown): string | null {
  const record = asRecord(payload)
  if (!record) return null
  if (typeof record.message === "string" && record.message.trim()) {
    return record.message
  }

  const data = asRecord(record.data)
  if (data && typeof data.message === "string" && data.message.trim()) {
    return data.message
  }

  return null
}

function toErrorMessage(prefix: string, response: Response, payload: unknown): string {
  const message = pickMessage(payload)
  const status = getResponseStatus(response)

  if (message) {
    return `${prefix}: ${message} (${status})`
  }
  return `${prefix} (${status})`
}

async function readZonesOrEmpty(response: Response): Promise<SmokingZone[]> {
  const result = await safeJson(response)
  return pickZones(extractPayloadData(result))
}

function toUserProfile(payload: unknown): UserProfile | null {
  const record = asRecord(payload)
  if (!record) return null

  const id = readNumber(record.id)
  const email = readString(record.email)
  const nickname = typeof record.nickname === "string" ? record.nickname : null
  const createdAt = readString(record.createdAt)

  if (!id || !email) {
    return null
  }

  return {
    id,
    email,
    nickname,
    profileImage: typeof record.profileImage === "string" ? record.profileImage : null,
    createdAt: createdAt || new Date().toISOString(),
  }
}

function pickUserProfile(payload: unknown): UserProfile | null {
  const record = asRecord(payload)
  if (!record) return null

  const data = asRecord(record.data)
  return toUserProfile(data?.user || record.user || data)
}

export async function fetchZonesByBounds(bounds: MapBounds): Promise<SmokingZone[]> {
  const url = `${API_BASE_URL}/api/zones/bounds?minLat=${bounds.minLat}&maxLat=${bounds.maxLat}&minLng=${bounds.minLng}&maxLng=${bounds.maxLng}`

  try {
    const response = await fetchWithTimeout(url)
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
    const response = await fetchWithTimeout(url)
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
    const response = await fetchWithTimeout(url)
    if (!response.ok) {
      return null
    }

    const result = await safeJson(response)
    const payload = asRecord(result)
    const data = asRecord(payload?.data)
    const raw = data?.zone || payload?.zone
    return raw ? toZone(raw) : null
  } catch {
    return null
  }
}

export async function createZone(
  payload: CreateZonePayload,
  token?: string,
  imageAsset?: UploadImageAsset | null,
): Promise<SmokingZone> {
  const formData = new FormData()
  formData.append("data", JSON.stringify(payload))
  appendImageToFormData(formData, imageAsset)

  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}/api/zones`, {
      method: "POST",
      headers: authHeaders(token),
      body: formData,
    })

    const result = await safeJson(response)
    if (!response.ok) {
      throw new Error(toErrorMessage("zone create failed", response, result))
    }

    const payload = asRecord(result)
    const data = asRecord(payload?.data)
    const raw = data?.zone || payload?.zone
    if (!raw) {
      throw new Error("invalid zone create response")
    }
    return toZone(raw)
  } catch (error) {
    if (error instanceof Error) {
      throw error
    }
    throw new Error("zone create failed: network error")
  }
}

export async function updateZone(
  id: number,
  payload: UpdateZonePayload,
  token?: string,
  imageAsset?: UploadImageAsset | null,
): Promise<SmokingZone> {
  const formData = new FormData()
  formData.append("data", JSON.stringify(payload))
  appendImageToFormData(formData, imageAsset)

  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}/api/zones/${id}`, {
      method: "PUT",
      headers: authHeaders(token),
      body: formData,
    })

    const result = await safeJson(response)
    if (!response.ok) {
      throw new Error(toErrorMessage("zone update failed", response, result))
    }

    const parsed = asRecord(result)
    const data = asRecord(parsed?.data)
    const raw = data?.zone || parsed?.zone
    if (!raw) {
      throw new Error("invalid zone update response")
    }

    return toZone(raw)
  } catch (error) {
    if (error instanceof Error) {
      throw error
    }
    throw new Error("zone update failed: network error")
  }
}

export async function getCurrentUser(token?: string): Promise<UserProfile | null> {
  if (!token) return null

  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}/api/auth/me`, {
      headers: authHeaders(token),
    })

    if (!response.ok) return null
    const result = await safeJson(response)
    const payload = asRecord(result)
    const data = asRecord(payload?.data)
    return toUserProfile(data?.user)
  } catch {
    return null
  }
}

export async function completeProfileSetup(
  payload: ProfilePayload,
  token?: string,
  imageAsset?: UploadImageAsset | null,
): Promise<UserProfile> {
  if (!token) {
    throw new Error("auth token required")
  }

  const nickname = payload.nickname.trim()
  if (!nickname) {
    throw new Error("nickname required")
  }

  const formData = new FormData()
  formData.append("nickname", nickname)
  appendProfileImageToFormData(formData, imageAsset)

  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}/api/users/profile-setup`, {
      method: "POST",
      headers: authHeaders(token),
      body: formData,
    })

    const result = await safeJson(response)
    if (!response.ok) {
      throw new Error(toErrorMessage("profile setup failed", response, result))
    }

    const user = pickUserProfile(result)
    if (!user) {
      throw new Error("invalid profile setup response")
    }

    return user
  } catch (error) {
    if (error instanceof Error) {
      throw error
    }
    throw new Error("profile setup failed: network error")
  }
}

export async function updateUserProfile(
  id: number,
  payload: ProfilePayload,
  token?: string,
  imageAsset?: UploadImageAsset | null,
): Promise<UserProfile> {
  if (!token) {
    throw new Error("auth token required")
  }

  const nickname = payload.nickname.trim()
  if (!nickname) {
    throw new Error("nickname required")
  }

  const formData = new FormData()
  formData.append("userData", JSON.stringify({ nickname }))
  appendProfileImageToFormData(formData, imageAsset)

  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}/api/users/${id}`, {
      method: "PUT",
      headers: authHeaders(token),
      body: formData,
    })

    const result = await safeJson(response)
    if (!response.ok) {
      throw new Error(toErrorMessage("profile update failed", response, result))
    }

    const user = pickUserProfile(result)
    if (!user) {
      throw new Error("invalid profile update response")
    }

    return user
  } catch (error) {
    if (error instanceof Error) {
      throw error
    }
    throw new Error("profile update failed: network error")
  }
}

export async function validateToken(token: string): Promise<boolean> {
  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}/api/auth/validate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ token }),
    })

    if (!response.ok) return false
    const result = await safeJson(response)
    const payload = asRecord(result)
    return Boolean(payload?.valid)
  } catch {
    return false
  }
}

export async function fetchMyZones(token?: string): Promise<SmokingZone[]> {
  if (!token) return []

  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}/api/zones/my`, {
      headers: authHeaders(token),
    })

    if (!response.ok) {
      return []
    }

    return readZonesOrEmpty(response)
  } catch {
    return []
  }
}

export async function deleteZone(id: number, token?: string): Promise<void> {
  if (!token) {
    throw new Error("auth token required")
  }

  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}/api/zones/${id}`, {
      method: "DELETE",
      headers: authHeaders(token),
    })

    if (!response.ok) {
      const payload = await safeJson(response)
      throw new Error(toErrorMessage("delete zone failed", response, payload))
    }
  } catch (error) {
    if (error instanceof Error) {
      throw error
    }
    throw new Error("delete zone failed: network error")
  }
}

export async function fetchZoneReviews(zoneId: number): Promise<ZoneReview[]> {
  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}/api/zones/${zoneId}/reviews`)
    if (!response.ok) {
      return []
    }

    const result = await safeJson(response)
    return pickReviews(extractPayloadData(result))
  } catch {
    return []
  }
}

export async function createZoneReview(
  zoneId: number,
  payload: CreateZoneReviewPayload | string,
  token?: string,
): Promise<ZoneReview> {
  if (!token) {
    throw new Error("auth token required")
  }

  const normalizedPayload =
    typeof payload === "string"
      ? { content: payload.trim() }
      : {
          content: String(payload?.content || "").trim(),
        }

  if (!normalizedPayload.content) {
    throw new Error("review content required")
  }

  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}/api/zones/${zoneId}/reviews`, {
      method: "POST",
      headers: {
        ...authHeaders(token),
        "Content-Type": "application/json",
      },
      body: JSON.stringify(normalizedPayload),
    })

    const result = await safeJson(response)
    if (!response.ok) {
      throw new Error(toErrorMessage("zone review create failed", response, result))
    }

    const parsed = asRecord(result)
    const data = asRecord(parsed?.data)
    const raw = data?.review || parsed?.review
    if (!raw) {
      throw new Error("invalid zone review create response")
    }

    return toZoneReview(raw)
  } catch (error) {
    if (error instanceof Error) {
      throw error
    }
    throw new Error("zone review create failed: network error")
  }
}

export function getImageUrl(imagePath: string | null | undefined): string | null {
  if (!imagePath) return null
  if (imagePath.startsWith("http")) return imagePath
  if (imagePath.startsWith("/")) return `${API_BASE_URL}${imagePath}`
  return `${API_BASE_URL}/api/images/${imagePath}`
}
