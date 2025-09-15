"use client"

import { useState, useRef } from "react"
import { MapContainer, type MapContainerRef } from "@/components/map-container"
import { FloatingActionButton } from "@/components/floating-action-button"
import { AddLocationModal } from "@/components/add-location-modal"
import { FloatingUserProfile } from "@/components/floating-user-profile"
import { CurrentLocationButton } from "@/components/current-location-button"
import { SearchBar } from "@/components/search-bar"

export default function HomePage() {
  const [isModalOpen, setIsModalOpen] = useState(false)
  const mapRef = useRef<MapContainerRef>(null)

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

  const handleSearch = (query: string) => {
    console.log("[v0] Search query:", query)
    // TODO: 실제 검색 로직 구현
  }

  return (
    <div className="h-screen w-screen bg-background text-foreground overflow-hidden relative">
      <MapContainer ref={mapRef} />

      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-6 left-1/2 transform -translate-x-1/2 w-full max-w-md px-6 pointer-events-auto z-[1000]">
          <SearchBar onSearch={handleSearch} />
        </div>

        <div className="absolute top-6 right-6 pointer-events-auto">
          <FloatingUserProfile />
        </div>

        <div className="absolute bottom-24 left-6 pointer-events-auto z-[9999]">
          <CurrentLocationButton onLocationFound={handleLocationFound} />
        </div>

        <div className="absolute bottom-8 right-6 pointer-events-auto">
          <FloatingActionButton onClick={() => setIsModalOpen(true)} />
        </div>
      </div>

      <AddLocationModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} onZoneCreated={handleZoneCreated} />
    </div>
  )
}
