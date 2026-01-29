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

          // 2. 카카오 결과가 없거나 실패 시 우리 DB 검색 (흡연구역 이름으로 찾기)
          const center = mapRef.current?.getCenter ? mapRef.current.getCenter() : null
          const results = await searchZones(query, center?.lat, center?.lng)
          
          if (results.length > 0) {
            const first = results[0]
            if (mapRef.current?.centerOnLocation) {
              mapRef.current.centerOnLocation(first.latitude, first.longitude)
            }
          } else {
            toast({
              title: "검색 결과 없음",
              description: "해당 위치나 흡연구역을 찾을 수 없습니다.",
            })
          }
        })
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