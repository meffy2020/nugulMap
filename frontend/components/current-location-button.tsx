"use client"

import { useState, useEffect } from "react"
import { Target, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"

interface CurrentLocationButtonProps {
  onLocationFound?: (location: { latitude: number; longitude: number; address: string }) => void
}

export function CurrentLocationButton({ onLocationFound }: CurrentLocationButtonProps) {
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    console.log("[v0] CurrentLocationButton component mounted and rendered")
  }, [])

  const reverseGeocode = async (lat: number, lng: number): Promise<string> => {
    try {
      const response = await fetch(
        `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&accept-language=ko`,
      )
      const data = await response.json()
      return data.display_name || `${lat.toFixed(6)}, ${lng.toFixed(6)}`
    } catch (error) {
      console.log("[v0] Reverse geocoding failed:", error)
      return `${lat.toFixed(6)}, ${lng.toFixed(6)}`
    }
  }

  const handleCurrentLocation = async () => {
    console.log("[v0] Current location button clicked")
    setIsLoading(true)

    try {
      if (!navigator.geolocation) {
        alert("위치 서비스가 지원되지 않습니다.")
        return
      }

      navigator.geolocation.getCurrentPosition(
        async (position) => {
          const { latitude, longitude } = position.coords
          console.log("[v0] Current location:", { latitude, longitude })

          const address = await reverseGeocode(latitude, longitude)
          onLocationFound?.({ latitude, longitude, address })

          setIsLoading(false)
        },
        (error) => {
          console.error("[v0] Location error:", error)
          alert("현재 위치를 가져올 수 없습니다.")
          setIsLoading(false)
        },
        {
          enableHighAccuracy: true,
          timeout: 10000,
          maximumAge: 60000,
        },
      )
    } catch (error) {
      console.error("[v0] Location error:", error)
      setIsLoading(false)
    }
  }

  return (
    <Button
      onClick={handleCurrentLocation}
      disabled={isLoading}
      size="icon"
      className="h-10 w-10 rounded-full shadow-2xl hover:scale-110 transition-all duration-200 z-[9999]"
      style={{
        backgroundColor: "#D97742",
        color: "white",
        position: "relative",
      }}
    >
      {isLoading ? <Loader2 className="h-5 w-5 animate-spin" /> : <Target className="h-5 w-5" />}
    </Button>
  )
}
