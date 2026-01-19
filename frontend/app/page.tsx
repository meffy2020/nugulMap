"use client"

import { useState, useRef } from "react"
import { useRouter } from "next/navigation"
import { MapContainer, type MapContainerRef } from "@/components/map-container"
import { FloatingActionButton } from "@/components/floating-action-button"
import { AddLocationModal } from "@/components/add-location-modal"
import { FloatingUserProfile } from "@/components/floating-user-profile"
import { CurrentLocationButton } from "@/components/current-location-button"
import { SearchBar } from "@/components/search-bar"
import { getCurrentUser, searchZones } from "@/lib/api"

export default function HomePage() {
  const [isModalOpen, setIsModalOpen] = useState(false)
  const mapRef = useRef<MapContainerRef>(null)
  const router = useRouter()

  const handleZoneCreateClick = async () => {
    const currentUser = await getCurrentUser()
    if (!currentUser) {
      router.push("/login")
      return
    }
    setIsModalOpen(true)
  }

  const handleZoneCreated = (newZone: any) => {
    console.log("[v0] New zone created, updating map:", newZone)
    if (mapRef.current?.handleZoneCreated) {
      mapRef.current.handleZoneCreated(newZone)
    }
  }

  const handleLocationFound = (location: { latitude: number; longitude: number; address: string }) => {
    console.log("[v0] Current location found:", location)
    if (mapRef.current?.centerOnLocation) {
      mapRef.current.centerOnLocation(location.latitude, location.longitude)
    }
  }

  const handleSearch = async (query: string) => {
    console.log("[v0] Search query:", query)
    try {
      const results = await searchZones(query)
      console.log("[v0] Search results:", results)
      
      // 검색 결과가 있으면 첫 번째 결과로 지도 이동
      if (results.length > 0 && mapRef.current?.centerOnLocation) {
        const firstResult = results[0]
        mapRef.current.centerOnLocation(firstResult.latitude, firstResult.longitude)
      } else {
        // 검색 결과가 없으면 사용자에게 알림
        alert("검색 결과가 없습니다.")
      }
    } catch (err) {
      console.error("Search failed:", err)
      alert("검색 중 오류가 발생했습니다.")
    }
  }

  return (
    <div className="h-screen w-screen bg-background text-foreground overflow-hidden relative">
      <MapContainer ref={mapRef} />

      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-6 left-1/2 transform -translate-x-1/2 w-full max-w-md px-6 pointer-events-auto z-10">
          <SearchBar onSearch={handleSearch} />
        </div>

        <div className="absolute top-6 right-6 pointer-events-auto">
          <FloatingUserProfile />
        </div>

        <div className="absolute bottom-24 left-6 pointer-events-auto z-[9999]">
          <CurrentLocationButton onLocationFound={handleLocationFound} />
        </div>

        <div className="absolute bottom-8 right-6 pointer-events-auto">
          <FloatingActionButton onClick={handleZoneCreateClick} />
        </div>
      </div>

      <AddLocationModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} onZoneCreated={handleZoneCreated} />
    </div>
  )
}
