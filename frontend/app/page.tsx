// frontend/app/page.tsx

"use client"

import { useState, useRef } from "react"
import { MapContainer, type MapContainerRef } from "@/components/map-container"
import { FloatingActionButton } from "@/components/floating-action-button"
import { AddLocationModal } from "@/components/add-location-modal"
import { FloatingUserProfile } from "@/components/floating-user-profile"
import { CurrentLocationButton } from "@/components/current-location-button"
import type { SmokingZone } from "@/lib/api" // SmokingZone 타입만 import

export default function HomePage() {
  const [isModalOpen, setIsModalOpen] = useState(false)
  const mapRef = useRef<MapContainerRef>(null)

  const handleZoneCreated = (newZone: SmokingZone) => {
    console.log("New zone created, updating map:", newZone)
    if (mapRef.current?.handleZoneCreated) {
      mapRef.current.handleZoneCreated(newZone)
    }
  }

  const handleLocationFound = (location: { latitude: number; longitude: number; address: string }) => {
    console.log("Current location found:", location)
    if (mapRef.current?.centerOnLocation) {
      mapRef.current.centerOnLocation(location.latitude, location.longitude)
    }
    // TODO: 현재 위치 기준으로 데이터를 다시 불러오는 로직 추가
  }

  return (
    <div className="h-screen w-screen bg-background text-foreground overflow-hidden relative">
      {/* MapContainer는 이제 스스로 데이터를 불러오므로 prop을 전달할 필요가 없습니다. */}
      <MapContainer ref={mapRef} />

      <div className="absolute inset-0 pointer-events-none">
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