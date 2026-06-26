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
  { label: "롯데월드 혼잡도", query: "롯데월드", kind: "hotplace" },
  { label: "지금 핫한 곳", query: "hot-now", kind: "hotplace" },
  { label: "성수 팝업", query: "성수", kind: "event" },
] as const

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
          console.log("[v0] Auto-location found:", latitude, longitude)
          if (mapRef.current?.centerOnLocation) {
            mapRef.current.centerOnLocation(latitude, longitude)
          }
        },
        (error) => {
          console.error("[v0] Auto-location error:", error)
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

  const focusFirstInsight = async (query: string, prefer?: "hotplace" | "event") => {
    const insight = await fetchMapInsights(5, 5, undefined, query)
    const hotplace = insight.hotplaces.places[0]
    const event = insight.events.events[0]

    if (prefer === "hotplace" && hotplace) {
      focusHotplace(hotplace)
      return true
    }
    if (prefer === "event" && event) {
      focusEvent(event)
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

  const handleInsightShortcut = async (query: string, kind: "hotplace" | "event") => {
    setShortcutLoading(query)
    try {
      const found = await focusFirstInsight(query, kind)
      if (!found) {
        toast({
          title: "검색 결과 없음",
          description: "해당 핫플, 행사, 팝업 정보를 찾을 수 없습니다.",
        })
      }
    } finally {
      setShortcutLoading(null)
    }
  }

  const handleSearch = async (query: string) => {
    if (!query.trim()) return

    try {
      // 1. 카카오 장소 검색 시도 (일반 목적지 찾기용)
      if (window.kakao && window.kakao.maps.services) {
        const ps = new window.kakao.maps.services.Places()
        ps.keywordSearch(query, async (data: any, status: any) => {
          if (status === window.kakao.maps.services.Status.OK) {
            // 가장 연관성 높은 장소로 이동
            const first = data[0]
            if (mapRef.current?.centerOnLocation) {
              mapRef.current.centerOnLocation(parseFloat(first.y), parseFloat(first.x))
              return // 장소를 찾았으면 여기서 종료 (이지역 흡연구역은 MapContainer가 자동으로 로드함)
            }
          }

          // 2. 카카오 결과가 없거나 실패 시 너굴맵 데이터 검색
          const found = await searchNugulData(query)
          if (!found) {
            toast({
              title: "검색 결과 없음",
              description: "해당 위치, 흡연구역, 핫플, 행사를 찾을 수 없습니다.",
            })
          }
        })
      } else {
        const found = await searchNugulData(query)
        if (!found) {
          toast({
            title: "검색 결과 없음",
            description: "해당 위치, 흡연구역, 핫플, 행사를 찾을 수 없습니다.",
          })
        }
      }
    } catch (err) {
      console.error("Search failed:", err)
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
            <div className="flex-1 min-w-0">
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
                onClick={() => handleInsightShortcut(shortcut.query, shortcut.kind)}
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

export default function HomePage() {
  return (
    <Suspense fallback={<div className="h-screen w-full flex items-center justify-center bg-background"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>}>
      <HomePageContent />
    </Suspense>
  )
}
