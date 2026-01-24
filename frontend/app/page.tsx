"use client"

import { useState, useRef, useEffect, Suspense } from "react"
import { MapContainer, type MapContainerRef } from "@/components/map-container"
import { FloatingActionButton } from "@/components/floating-action-button"
// import { AddLocationModal } from "@/components/add-location-modal"
import { FloatingUserProfile } from "@/components/floating-user-profile"
// import { CurrentLocationButton } from "@/components/current-location-button"
import { SearchBar } from "@/components/search-bar"
import { useAuth } from "@/hooks/use-auth"
import { useRouter, useSearchParams } from "next/navigation"
import { searchZones } from "@/lib/api"
import { useToast } from "@/hooks/use-toast"
import { Loader2 } from "lucide-react"

function HomePageContent() {
  const mapRef = useRef<MapContainerRef>(null)
  const { user } = useAuth()
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()

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

  const handleSearch = async (query: string) => {
    if (!query.trim()) return

    try {
      const results = await searchZones(query)
      if (results.length > 0) {
        const first = results[0]
        if (mapRef.current?.centerOnLocation) {
          mapRef.current.centerOnLocation(first.latitude, first.longitude)
        }
      } else {
        toast({
          title: "검색 결과 없음",
          description: "해당 키워드로 등록된 장소가 없습니다.",
        })
      }
    } catch (err) {
      console.error("Search failed:", err)
    }
  }

  return (
    <div className="relative h-screen w-full flex flex-col bg-background overflow-hidden">
      <div className="flex-1 relative">
        <MapContainer ref={mapRef} />

        {/* Top UI: Search Bar & Profile (Safe Area & Margin Adjusted) */}
        <div className="absolute top-0 left-0 right-0 z-40 pointer-events-none px-4 flex flex-col gap-4"
             style={{ paddingTop: 'calc(env(safe-area-inset-top, 0px) + 12px)' }}>
          <div className="flex items-center gap-3 pointer-events-auto w-full max-w-5xl mx-auto">
            <div className="flex-1">
              <SearchBar onSearch={handleSearch} />
            </div>
            <div className="shrink-0">
              <FloatingUserProfile />
            </div>
          </div>
        </div>

        <div className="absolute bottom-8 right-6 pointer-events-auto z-40">
          <FloatingActionButton onClick={handleAddClick} />
        </div>
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