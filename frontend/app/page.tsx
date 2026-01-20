"use client"

import { useState, useRef } from "react"
import { MapContainer, type MapContainerRef } from "@/components/map-container"
import { FloatingActionButton } from "@/components/floating-action-button"
import { AddLocationModal } from "@/components/add-location-modal"
import { FloatingUserProfile } from "@/components/floating-user-profile"
import { CurrentLocationButton } from "@/components/current-location-button"
import { SearchBar } from "@/components/search-bar"
import { useAuth } from "@/hooks/use-auth"
import { useRouter } from "next/navigation"
import { searchZones } from "@/lib/api"
import { useToast } from "@/hooks/use-toast"

export default function HomePage() {
  const [isModalOpen, setIsModalOpen] = useState(false)
  const mapRef = useRef<MapContainerRef>(null)
  const { user } = useAuth()
  const router = useRouter()
  const { toast } = useToast()

  const handleAddClick = () => {
    if (!user) {
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
      if (results && results.length > 0) {
        if (mapRef.current?.centerOnLocation) {
          mapRef.current.centerOnLocation(results[0].latitude, results[0].longitude)
        }
      } else {
        toast({
          title: "검색 결과 없음",
          description: `'${query}'에 대한 흡연구역을 찾을 수 없습니다.`,
          variant: "destructive",
        })
      }
    } catch (err) {
      console.error("Search failed:", err)
      toast({
        title: "검색 오류",
        description: "검색 중 문제가 발생했습니다.",
        variant: "destructive",
      })
    }
  }

  return (
    <div className="h-screen w-screen bg-background text-foreground overflow-hidden relative">
      <MapContainer ref={mapRef} />

      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-6 left-1/2 transform -translate-x-1/2 w-full max-w-md px-6 pointer-events-auto z-40">
          <SearchBar onSearch={handleSearch} />
        </div>

        <div className="absolute top-6 right-6 pointer-events-auto z-40">
          <FloatingUserProfile />
        </div>

        <div className="absolute bottom-24 left-6 pointer-events-auto z-[9999]">
          <CurrentLocationButton onLocationFound={handleLocationFound} />
        </div>

        <div className="absolute bottom-8 right-6 pointer-events-auto z-40">
          <FloatingActionButton onClick={handleAddClick} />
        </div>
      </div>

      <AddLocationModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} onZoneCreated={handleZoneCreated} />
    </div>
  )
}
