import * as nugulApi from "./nugulApi"

const {
  completeProfileSetup,
  createZoneReview,
  createZone,
  deleteZone,
  fetchMyZones,
  fetchZoneReviews,
  fetchZoneById,
  fetchZonesByBounds,
  getCurrentUser,
  searchZones,
  updateZone,
  updateUserProfile,
  validateToken,
} = nugulApi

type ZonePayload = Parameters<typeof createZone>[0]
type UploadAsset = {
  uri: string
  name: string
  type: string
}

const mockFetch = jest.fn()
const supportsOptionalImageUpload = createZone.length > 2 || updateZone.length > 3

const rawZone = {
  id: "10",
  region: "서울특별시",
  type: "실외",
  subtype: "테스트 부스",
  description: "테스트 설명",
  latitude: "37.5",
  longitude: "126.9",
  address: "서울 중구 테스트로 1",
  user: "tester@nugulmap.com",
  image: null,
}

beforeAll(() => {
  global.fetch = mockFetch as unknown as typeof fetch
})

beforeEach(() => {
  mockFetch.mockReset()
})

function mockJsonResponse(body: unknown, ok = true): Response {
  return {
    ok,
    text: async () => JSON.stringify(body),
  } as Response
}

describe("nugulApi", () => {
  it("fetchZonesByBounds parses zones from API response", async () => {
    mockFetch.mockResolvedValue(
      mockJsonResponse({
        success: true,
        data: {
          zones: [rawZone],
        },
      }),
    )

    const zones = await fetchZonesByBounds({
      minLat: 37.4,
      maxLat: 37.6,
      minLng: 126.8,
      maxLng: 127.0,
    })

    expect(mockFetch).toHaveBeenCalledTimes(1)
    expect(zones).toHaveLength(1)
    expect(zones[0]).toMatchObject({
      id: 10,
      subtype: "테스트 부스",
      latitude: 37.5,
      longitude: 126.9,
    })
  })

  it("fetchZonesByBounds returns fallback zones on non-ok response", async () => {
    mockFetch.mockResolvedValue(
      mockJsonResponse(
        {
          success: false,
        },
        false,
      ),
    )

    const zones = await fetchZonesByBounds({
      minLat: 0,
      maxLat: 1,
      minLng: 0,
      maxLng: 1,
    })

    expect(zones.length).toBeGreaterThan(0)
    expect(zones[0].id).toBe(1)
  })

  it("searchZones returns empty list on fetch error", async () => {
    mockFetch.mockRejectedValue(new Error("network down"))

    const zones = await searchZones("광화문")

    expect(zones).toEqual([])
  })

  it("searchZones includes lat/lng when provided", async () => {
    mockFetch.mockResolvedValue(
      mockJsonResponse({
        success: true,
        data: { zones: [rawZone] },
      }),
    )

    await searchZones("시청", 37.5, 126.9)

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("&lat=37.5&lng=126.9"),
      expect.any(Object),
    )
  })

  it("fetchZoneById returns one zone", async () => {
    mockFetch.mockResolvedValue(
      mockJsonResponse({
        success: true,
        data: {
          zone: rawZone,
        },
      }),
    )

    const zone = await fetchZoneById(10)

    expect(zone).not.toBeNull()
    expect(zone?.id).toBe(10)
    expect(zone?.subtype).toBe("테스트 부스")
  })

  it("fetchZonesByBounds falls back when JSON parse fails", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      text: async () => "not-json",
    } as Response)

    const zones = await fetchZonesByBounds({
      minLat: 37.4,
      maxLat: 37.6,
      minLng: 126.8,
      maxLng: 127.0,
    })

    expect(zones).toEqual([])
  })

  it("validateToken returns true for valid token response", async () => {
    mockFetch.mockResolvedValue(mockJsonResponse({ success: true, valid: true }))

    const valid = await validateToken("token-123")

    expect(valid).toBe(true)
  })

  it("validateToken returns false when request fails", async () => {
    mockFetch.mockRejectedValue(new Error("timeout"))

    const valid = await validateToken("token-123")

    expect(valid).toBe(false)
  })

  it("getCurrentUser returns null without token", async () => {
    const user = await getCurrentUser()
    expect(user).toBeNull()
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it("getCurrentUser returns null on network failure", async () => {
    mockFetch.mockRejectedValue(new Error("offline"))

    const user = await getCurrentUser("token-123")

    expect(user).toBeNull()
  })

  it("completeProfileSetup sends multipart nickname and optional image", async () => {
    mockFetch.mockResolvedValue(
      mockJsonResponse({
        success: true,
        data: {
          user: {
            id: 12,
            email: "tester@nugulmap.com",
            nickname: "tester",
            profileImage: "/images/profiles/tester.jpg",
            createdAt: "2026-04-06T12:00:00",
          },
        },
      }),
    )

    const user = await completeProfileSetup(
      { nickname: "tester" },
      "token-abc",
      {
        uri: "file:///tmp/profile.jpg",
        name: "profile.jpg",
        type: "image/jpeg",
      },
    )

    expect(user.nickname).toBe("tester")
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/users/profile-setup"),
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer token-abc" }),
      }),
    )

    const requestInit = mockFetch.mock.calls[0]?.[1] as RequestInit & { body?: FormData | null }
    expect(requestInit?.body).toBeInstanceOf(FormData)
    expect((requestInit?.body as FormData).get("nickname")).toBe("tester")
    expect((requestInit?.body as FormData).has("profileImage")).toBe(true)
  })

  it("updateUserProfile sends userData multipart payload", async () => {
    mockFetch.mockResolvedValue(
      mockJsonResponse({
        success: true,
        data: {
          user: {
            id: 12,
            email: "tester@nugulmap.com",
            nickname: "tester-updated",
            profileImage: null,
            createdAt: "2026-04-06T12:00:00",
          },
        },
      }),
    )

    const user = await updateUserProfile(12, { nickname: "tester-updated" }, "token-abc")

    expect(user.nickname).toBe("tester-updated")
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/users/12"),
      expect.objectContaining({
        method: "PUT",
        headers: expect.objectContaining({ Authorization: "Bearer token-abc" }),
      }),
    )

    const requestInit = mockFetch.mock.calls[0]?.[1] as RequestInit & { body?: FormData | null }
    expect(requestInit?.body).toBeInstanceOf(FormData)
    expect((requestInit?.body as FormData).get("userData")).toBe(JSON.stringify({ nickname: "tester-updated" }))
  })

  it("fetchMyZones returns [] when unauthorized", async () => {
    mockFetch.mockResolvedValue(mockJsonResponse({}, false))

    const zones = await fetchMyZones("token-123")

    expect(zones).toEqual([])
  })

  it("fetchZoneReviews parses review payloads", async () => {
    mockFetch.mockResolvedValue(
      mockJsonResponse({
        success: true,
        data: {
          reviews: [
            {
              id: 3,
              zoneId: 10,
              authorId: 5,
              authorNickname: "테스터",
              authorProfileImage: null,
              content: "깔끔하고 찾기 쉬워요.",
              createdAt: "2026-04-06T12:00:00",
              updatedAt: "2026-04-06T12:00:00",
            },
          ],
        },
      }),
    )

    const reviews = await fetchZoneReviews(10)

    expect(reviews).toHaveLength(1)
    expect(reviews[0]).toMatchObject({
      id: 3,
      zoneId: 10,
      authorNickname: "테스터",
      content: "깔끔하고 찾기 쉬워요.",
    })
  })

  it("createZoneReview sends json body with auth header", async () => {
    mockFetch.mockResolvedValue(
      mockJsonResponse({
        success: true,
        data: {
          review: {
            id: 4,
            zoneId: 10,
            authorId: 5,
            authorNickname: "모바일유저",
            authorProfileImage: null,
            content: "야간에도 찾기 쉬웠습니다.",
            createdAt: "2026-04-06T12:00:00",
            updatedAt: "2026-04-06T12:00:00",
          },
        },
      }),
    )

    const review = await createZoneReview(10, { content: "야간에도 찾기 쉬웠습니다." }, "token-abc")

    expect(review.id).toBe(4)
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/zones/10/reviews"),
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Authorization: "Bearer token-abc",
          "Content-Type": "application/json",
        }),
        body: JSON.stringify({ content: "야간에도 찾기 쉬웠습니다." }),
      }),
    )
  })

  it("createZoneReview throws without token", async () => {
    await expect(createZoneReview(10, { content: "로그인 없는 요청" })).rejects.toThrow("auth token required")
  })

  it("createZone sends multipart and auth header", async () => {
    mockFetch.mockResolvedValue(
      mockJsonResponse({
        success: true,
        data: { zone: rawZone },
      }),
    )

    const zone = await createZone(
      {
        region: "서울특별시",
        type: "BOOTH",
        subtype: "부스",
        description: "테스트",
        latitude: 37.5,
        longitude: 126.9,
        address: "서울 중구",
        user: "mobile-user",
      },
      "token-abc",
    )

    expect(zone.id).toBe(10)
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/zones"),
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer token-abc" }),
      }),
    )
  })

  const imageUploadSuite = supportsOptionalImageUpload ? describe : describe.skip

  imageUploadSuite("optional image upload", () => {
    const zoneImage: UploadAsset = {
      uri: "file:///tmp/zone.jpg",
      name: "zone.jpg",
      type: "image/jpeg",
    }
    const createZoneWithImage = createZone as unknown as (
      payload: ZonePayload,
      token: string,
      image: UploadAsset,
    ) => Promise<unknown>
    const updateZoneWithImage = updateZone as unknown as (
      id: number,
      payload: ZonePayload,
      token: string,
      image: UploadAsset,
    ) => Promise<unknown>

    it("creates a zone with an image multipart part when provided", async () => {
      mockFetch.mockResolvedValue(
        mockJsonResponse({
          success: true,
          data: { zone: rawZone },
        }),
      )

      await createZoneWithImage(
        {
          region: "서울특별시",
          type: "BOOTH",
          subtype: "부스",
          description: "이미지 포함 등록",
          latitude: 37.5,
          longitude: 126.9,
          address: "서울 중구",
          user: "mobile-user",
        },
        "token-abc",
        zoneImage,
      )

      const requestInit = mockFetch.mock.calls[0]?.[1] as RequestInit & {
        body?: FormData | null
      }
      expect(requestInit?.body).toBeInstanceOf(FormData)
      expect((requestInit?.body as FormData).has("image")).toBe(true)
    })

    it("updates a zone with an image multipart part when provided", async () => {
      mockFetch.mockResolvedValue(
        mockJsonResponse({
          success: true,
          data: { zone: rawZone },
        }),
      )

      await updateZoneWithImage(
        10,
        {
          region: "서울특별시",
          type: "BOOTH",
          subtype: "수정된 부스",
          description: "이미지 포함 수정",
          latitude: 37.501,
          longitude: 126.901,
          size: "M",
          address: "서울 중구 수정로 10",
          user: "mobile-user",
        },
        "token-abc",
        zoneImage,
      )

      const requestInit = mockFetch.mock.calls[0]?.[1] as RequestInit & {
        body?: FormData | null
      }
      expect(requestInit?.body).toBeInstanceOf(FormData)
      expect((requestInit?.body as FormData).has("image")).toBe(true)
    })
  })

  it("deleteZone throws without token", async () => {
    await expect(deleteZone(10)).rejects.toThrow("auth token required")
  })

  describe("updateZone", () => {
    const updatePayload: ZonePayload = {
      region: "서울특별시",
      type: "BOOTH",
      subtype: "수정된 부스",
      description: "수정된 설명",
      latitude: 37.501,
      longitude: 126.901,
      size: "M",
      address: "서울 중구 수정로 10",
      user: "mobile-user",
    }

    it("updates a zone with multipart payload and auth header", async () => {
      mockFetch.mockResolvedValue(
        mockJsonResponse({
          success: true,
          data: { zone: rawZone },
        }),
      )

      const zone = await updateZone(10, updatePayload, "token-abc")

      expect(zone).toBeTruthy()
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/zones/10"),
        expect.objectContaining({
          method: "PUT",
          headers: expect.objectContaining({ Authorization: "Bearer token-abc" }),
        }),
      )

      const requestInit = mockFetch.mock.calls[0]?.[1] as RequestInit & {
        body?: FormData | null
      }
      expect(requestInit?.body).toBeInstanceOf(FormData)
      expect((requestInit?.body as FormData).get("data")).toBe(JSON.stringify(updatePayload))
    })

    it("throws a descriptive error when the update request fails", async () => {
      mockFetch.mockResolvedValue(
        mockJsonResponse(
          {
            success: false,
            message: "update rejected",
          },
          false,
        ),
      )

      await expect(updateZone(10, updatePayload, "token-abc")).rejects.toThrow("zone update failed")
    })
  })
})
