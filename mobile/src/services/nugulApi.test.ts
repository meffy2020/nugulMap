import {
  createZone,
  deleteZone,
  fetchMyZones,
  fetchZoneById,
  fetchZonesByBounds,
  getCurrentUser,
  searchZones,
  validateToken,
} from "./nugulApi"

const mockFetch = jest.fn()

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

    expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining("&lat=37.5&lng=126.9"))
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

  it("getCurrentUser returns null without token", async () => {
    const user = await getCurrentUser()
    expect(user).toBeNull()
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it("fetchMyZones returns [] when unauthorized", async () => {
    mockFetch.mockResolvedValue(mockJsonResponse({}, false))

    const zones = await fetchMyZones("token-123")

    expect(zones).toEqual([])
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

  it("deleteZone throws without token", async () => {
    await expect(deleteZone(10)).rejects.toThrow("auth token required")
  })
})
