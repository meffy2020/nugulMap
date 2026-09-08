"use client"

import { FloatingActionButton } from "@/components/floating-action-button"
import { MapContainer, type MapContainerRef } from "@/components/map-container"
import { Suspense, useEffect, useRef } from "react"
import { CurrentLocationButton } from "@/components/current-location-button"
import { FloatingUserProfile } from "@/components/floating-user-profile"
import { SearchBar } from "@/components/search-bar"
import { useAuth } from "@/hooks/use-auth"
import { useToast } from "@/hooks/use-toast"
import { searchZones } from "@/lib/api"
import { Loader2 } from "lucide-react"
import { useRouter, useSearchParams } from "next/navigation"

function HomePageContent() {
  const mapRef = useRef<MapContainerRef>(null)
  const searchRequestRef = useRef(0)
  const { user } = useAuth()
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()

  useEffect(() => () => { searchRequestRef.current += 1 }, [])

  // URL 파라미터로 위치 이동 (등록 후 복귀 시)
  useEffect(() => {
    const lat = searchParams.get("lat")
    const lng = searchParams.get("lng")

    if (lat && lng) {
      mapRef.current?.centerOnLocation(Number(lat), Number(lng))
    }
  }, [searchParams])

  // 앱 실행 시 자동으로 위치 권한 요청 및 이동 (URL 파라미터가 없을 때만)
  useEffect(() => {
    let active = true
    const initialRequest = searchRequestRef.current
    if (!searchParams.get("lat") && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          if (!active || initialRequest !== searchRequestRef.current) return
          const { latitude, longitude } = position.coords
          if (mapRef.current?.centerOnLocation) {
            mapRef.current.centerOnLocation(latitude, longitude)
          }
        },
        () => {},
        { timeout: 10_000, maximumAge: 60_000 },
      )
    }
    return () => { active = false }
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
    searchRequestRef.current += 1
    if (mapRef.current?.centerOnLocation) {
      mapRef.current.centerOnLocation(lat, lng)
    }
  }

  const handleSearch = async (query: string) => {
    const requestId = ++searchRequestRef.current
    if (!query.trim()) return

    try {
      const kakaoPlace = await findKakaoPlace(query)
      if (requestId !== searchRequestRef.current) return
      if (kakaoPlace) {
        mapRef.current?.centerOnLocation(kakaoPlace.latitude, kakaoPlace.longitude)
        return
      }

      const center = mapRef.current?.getCenter()
      const zones = await searchZones(query, center?.lat, center?.lng)
      if (requestId !== searchRequestRef.current) return
      if (zones.length) {
        mapRef.current?.centerOnLocation(zones[0].latitude, zones[0].longitude)
      } else {
        showSearchEmptyToast(toast)
      }
    } catch (err) {
      if (requestId !== searchRequestRef.current) return
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
    description: "해당 위치나 흡연구역을 찾을 수 없습니다.",
  })
}

export default function HomePage() {
  return (
    <Suspense fallback={<div className="h-screen w-full flex items-center justify-center bg-background"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>}>
      <HomePageContent />
    </Suspense>
  )
}
