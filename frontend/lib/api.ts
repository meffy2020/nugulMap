const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:3000"

export interface ZoneRequest {
  region: string
  type: string
  subtype: string
  description: string
  latitude: number
  longitude: number
  address: string
  user: string
}

export interface ZoneResponse {
  id: number
  region: string
  type: string
  subtype: string
  description: string
  latitude: number
  longitude: number
  address: string
  user: string
  image?: string
}

export interface UserResponse {
  id: number
  nickname: string
  email: string
  profileImageUrl?: string
}

export interface NicknameUpdateRequest {
  nickname: string
}

class ApiService {
  private getAuthToken(): string | null {
    // This should be implemented based on your auth system
    return localStorage.getItem("access_token")
  }

  private async request<T>(endpoint: string, options?: RequestInit): Promise<T> {
    const url = `${API_BASE_URL}/api${endpoint}`
    const authToken = this.getAuthToken()

    const response = await fetch(url, {
      headers: {
        ...(options?.headers?.["Content-Type"] ? {} : { "Content-Type": "application/json" }),
        ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
        ...options?.headers,
      },
      ...options,
    })

    if (!response.ok) {
      throw new Error(`API Error: ${response.status} ${response.statusText}`)
    }

    const contentType = response.headers.get("content-type")
    if (contentType && contentType.includes("application/json")) {
      return response.json()
    }
    return {} as T
  }

  async createZone(zoneData: ZoneRequest, imageFile?: File): Promise<ZoneResponse> {
    const formData = new FormData()
    formData.append("request", JSON.stringify(zoneData))
    if (imageFile) {
      formData.append("image", imageFile)
    }

    return this.request<ZoneResponse>("/zones", {
      method: "POST",
      headers: {}, // Let browser set Content-Type for FormData
      body: formData,
    })
  }

  async getAllZones(latitude?: number, longitude?: number, radius?: number): Promise<ZoneResponse[]> {
    let endpoint = "/zones"
    const params = new URLSearchParams()

    if (latitude !== undefined) params.append("latitude", latitude.toString())
    if (longitude !== undefined) params.append("longitude", longitude.toString())
    if (radius !== undefined) params.append("radius", radius.toString())

    if (params.toString()) {
      endpoint += `?${params.toString()}`
    }

    return this.request<ZoneResponse[]>(endpoint)
  }

  async getZone(id: number): Promise<ZoneResponse> {
    return this.request<ZoneResponse>(`/zones/${id}`)
  }

  async updateZone(id: number, zoneData: ZoneRequest, imageFile?: File): Promise<ZoneResponse> {
    const formData = new FormData()
    formData.append("request", JSON.stringify(zoneData))
    if (imageFile) {
      formData.append("image", imageFile)
    }

    return this.request<ZoneResponse>(`/zones/${id}`, {
      method: "PUT",
      headers: {}, // Let browser set Content-Type for FormData
      body: formData,
    })
  }

  async deleteZone(id: number): Promise<void> {
    return this.request<void>(`/zones/${id}`, {
      method: "DELETE",
    })
  }

  async getCurrentUser(): Promise<UserResponse> {
    return this.request<UserResponse>("/users/me")
  }

  async updateUserNickname(nickname: string): Promise<UserResponse> {
    return this.request<UserResponse>("/users/me/nickname", {
      method: "PUT",
      body: JSON.stringify({ nickname }),
    })
  }

  async updateUserProfileImage(imageFile: File): Promise<UserResponse> {
    const formData = new FormData()
    formData.append("image", imageFile)

    return this.request<UserResponse>("/users/me/profile-image", {
      method: "PUT",
      headers: {}, // Let browser set Content-Type for FormData
      body: formData,
    })
  }

  async deleteCurrentUser(): Promise<void> {
    return this.request<void>("/users/me", {
      method: "DELETE",
    })
  }
}

export const apiService = new ApiService()
