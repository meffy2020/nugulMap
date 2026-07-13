"use client"

import { useState, useRef, useEffect, Suspense } from "react"
import { MapContainer, type MapContainerRef } from "@/components/map-container"
import { FloatingActionButton } from "@/components/floating-action-button"
// import { AddLocationModal } from "@/components/add-location-modal"
import { FloatingUserProfile } from "@/components/floating-user-profile"
import { CurrentLocationButton } from "@/components/current-location-button"
import { SearchBar } from "@/components/search-bar"
import { useAuth } from "@/hooks/use-auth"
import { useRouter, useSearchParams } from "next/navigation"
import { fetchMapInsights, searchZones, type Hotplace, type TrendEvent } from "@/lib/api"
import { useToast } from "@/hooks/use-toast"
import { CalendarDays, Flame, Loader2 } from "lucide-react"

const INSIGHT_SHORTCUTS = [
  { label: "롯데월드 혼잡도", query: "롯데월드", kind: "hotplace", showAll: false },
  { label: "지금 핫한 곳", query: "hot-now", kind: "hotplace", showAll: true },
  { label: "성수 팝업", query: "성수", kind: "event", showAll: false },
] as const

type InsightIntent = "hotplace" | "event"

function HomePageContent() {
  const mapRef = useRef<MapContainerRef>(null)
  const { user } = useAuth()
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()
  const [shortcutLoading, setShortcutLoading] = useState<string | null>(null)

  // URL 파라미터로 위치 이동 (등록 후 복귀 시)
  useEffect(() => {
    const lat = searchParams.get("lat")
    const lng = searchParams.get("lng")

    if (lat && lng && mapRef.current?.centerOnLocation) {
      // 약간의 지연 후 이동 (지도가 로드될 시간 확보)
      setTimeout(() => {
        mapRef.current?.centerOnLocation(parseFloat(lat), parseFloat(lng))
      }, 500)
    }
  }, [searchParams])

  // 앱 실행 시 자동으로 위치 권한 요청 및 이동 (URL 파라미터가 없을 때만)
  useEffect(() => {
    if (!searchParams.get("lat") && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const { latitude, longitude } = position.coords
          if (mapRef.current?.centerOnLocation) {
            mapRef.current.centerOnLocation(latitude, longitude)
          }
        }
      )
    }
  }, [searchParams])

  const handleAddClick = () => {
    if (!user) {
      router.push("/login")
      return
    }
    
    // 현재 지도 중심 좌표 가져오기
    let url = "/add"
    if (mapRef.current?.getCenter) {
      const center = mapRef.current.getCenter()
      url += `?lat=${center.lat}&lng=${center.lng}`
    }
    
    router.push(url)
  }

  const handleLocationFound = (lat: number, lng: number) => {
    if (mapRef.current?.centerOnLocation) {
      mapRef.current.centerOnLocation(lat, lng)
    }
  }

  const searchNugulData = async (query: string) => {
    const center = mapRef.current?.getCenter ? mapRef.current.getCenter() : null
    try {
      const zones = await searchZones(query, center?.lat, center?.lng)
      if (zones.length > 0) {
        const first = zones[0]
        mapRef.current?.centerOnLocation(first.latitude, first.longitude)
        return true
      }
    } catch (error) {
      console.warn("Zone search failed; trying Season 2 insights:", error)
    }

    return focusFirstInsight(query)
  }

  const focusHotplace = (place: Hotplace) => {
    mapRef.current?.focusHotplace(place)
  }

  const focusEvent = (event: TrendEvent) => {
    mapRef.current?.focusEvent(event)
  }

  const focusFirstInsight = async (query: string, prefer?: InsightIntent, showAll = false) => {
    const insight = await fetchMapInsights(5, 5, undefined, query)
    const hotplace = insight.hotplaces.places[0]
    const event = insight.events.events[0]

    if (prefer === "hotplace" && hotplace) {
      if (showAll) {
        mapRef.current?.showHotplaceResults(insight.hotplaces)
      } else {
        focusHotplace(hotplace)
      }
      return true
    }
    if (prefer === "event" && event) {
      if (showAll) {
        mapRef.current?.showEventResults(insight.events)
      } else {
        focusEvent(event)
      }
      return true
    }
    if (event) {
      focusEvent(event)
      return true
    }
    if (hotplace) {
      focusHotplace(hotplace)
      return true
    }
    return false
  }

  const handleInsightShortcut = async (query: string, kind: InsightIntent, showAll: boolean) => {
    setShortcutLoading(query)
    try {
      const found = await focusFirstInsight(query, kind, showAll)
      if (!found) {
        toast({
          title: "검색 결과 없음",
          description: "해당 핫플, 행사, 팝업 정보를 찾을 수 없습니다.",
        })
      }
    } catch (error) {
      console.error("Insight shortcut failed:", error)
      toast({
        title: "정보 갱신 실패",
        description: "기존 지도 정보는 유지됩니다. 잠시 후 다시 시도해 주세요.",
      })
    } finally {
      setShortcutLoading(null)
    }
  }

  const handleSearch = async (query: string) => {
    if (!query.trim()) return

    try {
      const insightIntent = detectInsightIntent(query)
      if (insightIntent) {
        const insightQuery = normalizeInsightQuery(query, insightIntent)
        const found = await focusFirstInsight(insightQuery, insightIntent, true)
        if (!found) showSearchEmptyToast(toast)
        return
      }

      const kakaoPlace = await findKakaoPlace(query)
      if (kakaoPlace) {
        mapRef.current?.centerOnLocation(kakaoPlace.latitude, kakaoPlace.longitude)
        return
      }

      const found = await searchNugulData(query)
      if (!found) showSearchEmptyToast(toast)
    } catch (err) {
      console.error("Search failed:", err)
      toast({
        title: "검색 실패",
        description: "기존 지도 정보는 유지됩니다. 잠시 후 다시 시도해 주세요.",
      })
    }
  }

  return (
    <div className="relative h-screen-dvh w-full bg-background overflow-hidden">
      {/* 1. Floating Top Header Bar */}
      <header className="absolute top-0 left-0 right-0 z-50">
        {/* Notch Area protection with blur background */}
        <div className="bg-white/80 backdrop-blur-md border-b">
          <div style={{ height: 'env(safe-area-inset-top, 0px)' }} />
          <div className="px-4 h-16 flex items-center gap-3 max-w-5xl mx-auto w-full">
            <div className="min-w-0 flex-1">
              <SearchBar onSearch={handleSearch} />
            </div>
            <div className="shrink-0">
              <FloatingUserProfile />
            </div>
          </div>
          <div className="mx-auto flex h-11 w-full max-w-5xl items-center gap-2 overflow-x-auto px-4 pb-3">
            {INSIGHT_SHORTCUTS.map((shortcut) => (
              <button
                key={shortcut.label}
                type="button"
                className="flex h-8 shrink-0 items-center gap-1.5 rounded-full border border-white/80 bg-white px-3 text-xs font-black text-zinc-700 shadow-sm transition hover:text-zinc-950 disabled:opacity-60"
                disabled={shortcutLoading !== null}
                onClick={() => handleInsightShortcut(shortcut.query, shortcut.kind, shortcut.showAll)}
                aria-busy={shortcutLoading === shortcut.query}
              >
                {shortcut.kind === "hotplace" ? <Flame className="h-3.5 w-3.5 text-orange-600" /> : <CalendarDays className="h-3.5 w-3.5 text-sky-700" />}
                <span>{shortcutLoading === shortcut.query ? "확인 중" : shortcut.label}</span>
              </button>
            ))}
          </div>
        </div>
      </header>

      {/* 2. Full Screen Map Layer */}
      <div className="absolute inset-0 z-0">
        <MapContainer ref={mapRef} />
      </div>

      {/* Floating Controls Overlay */}
      <div className="absolute bottom-0 left-0 right-0 pointer-events-none z-40">
        <div className="relative w-full h-[150px]">
          {/* Bottom Left: Current Location Button */}
          <div className="absolute bottom-10 left-6 pointer-events-auto">
            <CurrentLocationButton onLocationFound={handleLocationFound} />
          </div>

          {/* Bottom Right: Add Button */}
          <div className="absolute bottom-10 right-6 pointer-events-auto">
            <FloatingActionButton onClick={handleAddClick} />
          </div>
        </div>
        {/* Bottom Safe Area padding */}
        <div style={{ height: 'env(safe-area-inset-bottom, 0px)' }} className="bg-transparent" />
      </div>
    </div>
  )
}

function detectInsightIntent(query: string): InsightIntent | null {
  const normalized = query.toLowerCase()
  if (/팝업|행사|축제|페스티벌|언제\s*부터|언제\s*까지/.test(normalized)) {
    return "event"
  }
  if (/혼잡|붐비|붐벼|사람\s*많|인파|hot[-_\s]?now|지금\s*핫/.test(normalized)) {
    return "hotplace"
  }
  return null
}

function normalizeInsightQuery(query: string, intent: InsightIntent): string {
  if (intent === "hotplace" && /어디/.test(query)) {
    return "hot-now"
  }
  if (intent === "event") {
    const normalized = query
      .replace(/언제\s*부터|언제\s*까지/g, " ")
      .replace(/[?？!！]+/g, " ")
      .replace(/\s+/g, " ")
      .trim()
    return normalized || "팝업 행사"
  }
  return query
}

function findKakaoPlace(query: string): Promise<{ latitude: number; longitude: number } | null> {
  if (!window.kakao?.maps?.services) {
    return Promise.resolve(null)
  }

  return new Promise((resolve) => {
    const places = new window.kakao.maps.services.Places()
    places.keywordSearch(query, (data: any[], status: string) => {
      if (status !== window.kakao.maps.services.Status.OK || !data?.[0]) {
        resolve(null)
        return
      }
      const latitude = Number.parseFloat(data[0].y)
      const longitude = Number.parseFloat(data[0].x)
      resolve(Number.isFinite(latitude) && Number.isFinite(longitude) ? { latitude, longitude } : null)
    })
  })
}

function showSearchEmptyToast(showToast: (message: { title: string; description: string }) => unknown) {
  showToast({
    title: "검색 결과 없음",
    description: "해당 위치, 흡연구역, 혼잡도, 행사를 찾을 수 없습니다.",
  })
}

export default function HomePage() {
  return (
    <Suspense fallback={<div className="h-screen w-full flex items-center justify-center bg-background"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>}>
      <HomePageContent />
    </Suspense>
  )
}
