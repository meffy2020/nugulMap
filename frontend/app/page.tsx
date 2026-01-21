"use client"

import { useState, useRef, useEffect } from "react"
import { MapContainer, type MapContainerRef } from "@/components/map-container"
import { FloatingActionButton } from "@/components/floating-action-button"
import { AddLocationModal } from "@/components/add-location-modal"
import { FloatingUserProfile } from "@/components/floating-user-profile"
// import { CurrentLocationButton } from "@/components/current-location-button"
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

  // 앱 실행 시 자동으로 위치 권한 요청 및 이동
  useEffect(() => {
    if (navigator.geolocation) {
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
          // 권한 거부 시 서울 시청 기본값 유지 (MapContainer 내부 기본값)
        }
      )
    }
  }, [])

  const handleAddClick = () => {
    if (!user) {
      router.push("/login")
      return
    }
    setIsModalOpen(true)
  }
...
        {/* <div className="absolute bottom-24 left-6 pointer-events-auto z-[9999]">
          <CurrentLocationButton onLocationFound={handleLocationFound} />
        </div> */}

        <div className="absolute bottom-8 right-6 pointer-events-auto z-40">
          <FloatingActionButton onClick={handleAddClick} />
        </div>

      </div>

      <AddLocationModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} onZoneCreated={handleZoneCreated} />
    </div>
  )
}
