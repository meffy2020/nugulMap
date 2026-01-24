"use client"

import { useState } from "react"
import { Navigation, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useToast } from "@/hooks/use-toast"

interface CurrentLocationButtonProps {
  onLocationFound: (lat: number, lng: number) => void
}

export function CurrentLocationButton({ onLocationFound }: CurrentLocationButtonProps) {
  const [loading, setLoading] = useState(false)
  const { toast } = useToast()

  const handleGetCurrentLocation = () => {
    if (!navigator.geolocation) {
      toast({
        title: "위치 정보 오류",
        description: "브라우저가 위치 정보를 지원하지 않습니다.",
        variant: "destructive",
      })
      return
    }

    setLoading(true)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude } = position.coords
        onLocationFound(latitude, longitude)
        setLoading(false)
      },
      (error) => {
        console.error("Error getting location:", error)
        toast({
          title: "위치 확인 실패",
          description: "위치 권한을 허용했는지 확인해주세요.",
          variant: "destructive",
        })
        setLoading(false)
      },
      { enableHighAccuracy: true },
    )
  }

  return (
    <Button
      variant="outline"
      size="icon"
      className="h-12 w-12 rounded-full bg-background shadow-xl border-none hover:bg-muted active:scale-90 transition-all group"
      onClick={handleGetCurrentLocation}
      disabled={loading}
    >
      {loading ? (
        <Loader2 className="h-5 w-5 animate-spin text-primary" />
      ) : (
        <Navigation className="h-5 w-5 text-primary group-hover:fill-primary transition-all" />
      )}
    </Button>
  )
}
