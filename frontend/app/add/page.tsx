"use client"

import { useState, useCallback, useRef, useEffect, Suspense } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { FixedPinMap, type FixedPinMapRef } from "@/components/add-zone/fixed-pin-map"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ArrowLeft, Search, Camera, Loader2, MapPin, Building2, Trees, Warehouse } from "lucide-react"
import { cn } from "@/lib/utils"
import { createZone, type CreateZonePayload } from "@/lib/api"
import { useToast } from "@/hooks/use-toast"
import { CurrentLocationButton } from "@/components/current-location-button"

function AddZoneContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()
  const mapRef = useRef<FixedPinMapRef>(null)
  
  const initialLat = parseFloat(searchParams.get("lat") || "37.5665")
  const initialLng = parseFloat(searchParams.get("lng") || "126.978")

  const [address, setAddress] = useState("위치 확인 중...")
  const [region, setRegion] = useState("서울특별시")
  const [coords, setCoords] = useState({ lat: initialLat, lng: initialLng })
  const [isAddressLoading, setIsAddressLoading] = useState(false)
  
  const [type, setType] = useState("부스") 
  const [description, setDescription] = useState("")
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const fileInputRef = useRef<HTMLInputElement>(null)

  const updateAddress = useCallback((lat: number, lng: number, retryCount = 0) => {
    if (!window.kakao?.maps?.services) {
      if (retryCount < 5) { setTimeout(() => updateAddress(lat, lng, retryCount + 1), 500) }
      else { setAddress("주소를 불러오지 못했어요"); setIsAddressLoading(false) }
      return
    }
    setIsAddressLoading(true)
    const geocoder = new window.kakao.maps.services.Geocoder()
    geocoder.coord2Address(lng, lat, (result: any, status: any) => {
      setIsAddressLoading(false)
      if (status === window.kakao.maps.services.Status.OK && result[0]) {
        const addr = result[0].address
        setAddress(addr.address_name)
        setRegion(addr.region_1depth_name || "서울특별시")
      } else { setAddress("주소를 찾을 수 없습니다.") }
    })
  }, [])

  const handleLocationChange = useCallback((lat: number, lng: number) => {
    setCoords({ lat, lng })
    updateAddress(lat, lng)
  }, [updateAddress])

  const resizeImage = (file: File): Promise<File> => {
    return new Promise((resolve) => {
      const reader = new FileReader()
      reader.readAsDataURL(file)
      reader.onload = (event) => {
        const img = new window.Image()
        img.src = event.target?.result as string
        img.onload = () => {
          const canvas = document.createElement("canvas")
          let width = img.width; let height = img.height
          const MAX_SIZE = 1280
          if (width > height) { if (width > MAX_SIZE) { height *= MAX_SIZE / width; width = MAX_SIZE; } }
          else { if (height > MAX_SIZE) { width *= MAX_SIZE / height; height = MAX_SIZE; } }
          canvas.width = width; canvas.height = height
          const ctx = canvas.getContext("2d")
          ctx?.drawImage(img, 0, 0, width, height)
          canvas.toBlob((blob) => {
            if (blob) resolve(new File([blob], file.name, { type: "image/jpeg", lastModified: Date.now() }))
            else resolve(file)
          }, "image/jpeg", 0.8)
        }
      }
    })
  }

  const handleImageChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      const optimizedFile = await resizeImage(file)
      setImageFile(optimizedFile)
      setImagePreview(URL.createObjectURL(optimizedFile))
    }
  }

  const handleSubmit = async () => {
    if (isSubmitting) return
    const lastSubmit = localStorage.getItem("nugul_last_submit")
    const now = Date.now()
    if (lastSubmit && now - parseInt(lastSubmit) < 30000) {
      toast({ title: "천천히 해주세요! ✋", description: "30초 후에 다시 등록할 수 있습니다.", variant: "destructive" })
      return
    }
    setIsSubmitting(true)
    try {
      const payload: CreateZonePayload = {
        region, 
        type: type === "개방" ? "OPEN" : type === "부스" ? "BOOTH" : "INDOOR", 
        subtype: type,
        description: description || `${address}에 위치한 ${type}형 흡연구역`,
        latitude: coords.lat, longitude: coords.lng, address, user: "익명사용자",
      }
      await createZone(payload, imageFile || undefined)
      localStorage.setItem("nugul_last_submit", Date.now().toString())
      toast({ title: "등록 완료! 👏", description: "너구리들을 위한 소중한 정보 감사합니다." })
      router.push(`/?lat=${coords.lat}&lng=${coords.lng}&zoom=true`)
    } catch (err) {
      toast({ title: "등록 실패", description: "잠시 후 다시 시도해주세요.", variant: "destructive" })
    } finally { setIsSubmitting(false) }
  }

  const ZONE_TYPES = [
    { id: "부스", label: "부스", icon: Building2 },
    { id: "개방", label: "개방", icon: Trees },
    { id: "실내", label: "실내", icon: Warehouse },
  ]

  return (
    <div className="relative h-screen w-full flex flex-col bg-background overflow-hidden">
      {/* 1. Header (Fixed) */}
      <header className="z-50 bg-background border-b shadow-sm shrink-0">
        <div style={{ height: 'env(safe-area-inset-top, 0px)' }} />
        <div className="px-4 py-3 flex flex-col gap-2">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" className="rounded-full h-10 w-10 shrink-0" onClick={() => router.back()}>
              <ArrowLeft className="w-6 h-6 text-foreground" />
            </Button>
            <div className="flex-1 min-w-0">
               <h1 className="font-black text-base text-foreground leading-tight truncate">흡연구역 등록</h1>
               <p className="text-muted-foreground text-[10px] font-medium">핀을 정확한 위치에 맞춰주세요.</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <div className="relative flex-1 group">
              <div className="absolute inset-y-0 left-3 flex items-center pointer-events-none">
                <Search className="h-3.5 w-3.5 text-muted-foreground" />
              </div>
              <Input placeholder="장소 검색" className="pl-9 h-9 bg-muted/50 border-none rounded-lg text-xs" />
            </div>
            <CurrentLocationButton className="h-9 w-9 border-muted bg-muted/30" onLocationFound={(lat, lng) => mapRef.current?.centerOnLocation(lat, lng)} />
          </div>
        </div>
      </header>

      {/* 2. Map Layer */}
      <div className="flex-1 relative overflow-hidden bg-muted">
        <FixedPinMap ref={mapRef} onLocationChange={handleLocationChange} bottomOffset={180} initialLat={initialLat} initialLng={initialLng} />
      </div>

      {/* 3. Slim Bottom Sheet (Fixed) */}
      <div className="z-50 bg-background border-t shadow-[0_-5px_20px_rgba(0,0,0,0.05)] shrink-0">
        <div className="px-5 pt-4 pb-safe-bottom space-y-3">
          {/* Address Row */}
          <div className="flex items-center gap-2">
             <MapPin className="w-4 h-4 text-primary shrink-0" />
             <h2 className="text-sm font-bold text-foreground truncate">
               {isAddressLoading ? <span className="animate-pulse opacity-50">확인 중...</span> : address}
             </h2>
          </div>

          {/* Type & Photo Row (Merged) */}
          <div className="flex items-center gap-3">
            {/* Types (No Labels) */}
            <div className="flex-1 grid grid-cols-3 gap-2">
              {ZONE_TYPES.map((t) => (
                <button key={t.id} onClick={() => setType(t.id)} className={cn("flex flex-col items-center justify-center h-12 rounded-xl border transition-all", type === t.id ? "border-primary bg-primary/5 text-primary" : "border-border/50 bg-background text-muted-foreground")}>
                  <t.icon className={cn("w-5 h-5", type === t.id ? "fill-current" : "")} />
                  <span className="text-[8px] font-black mt-0.5">{t.label}</span>
                </button>
              ))}
            </div>
            
            {/* Photo (Compact) */}
            <div 
              onClick={() => fileInputRef.current?.click()} 
              className={cn("w-14 h-12 rounded-xl border-2 border-dashed flex items-center justify-center cursor-pointer overflow-hidden transition-all shrink-0", imagePreview ? "border-primary border-solid" : "border-muted-foreground/30 bg-muted/30")}>
              {imagePreview ? <img src={imagePreview} alt="P" className="w-full h-full object-cover" /> : <Camera className="w-5 h-5 text-muted-foreground/50" />}
            </div>
            <input type="file" ref={fileInputRef} onChange={handleImageChange} accept="image/*" className="hidden" />
          </div>

          {/* Description & Button */}
          <div className="flex items-center gap-2 pb-2">
            <Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="팁 추가 (선택)" className="flex-1 h-10 rounded-xl bg-muted/30 border-none text-xs" />
            <Button className="h-10 px-6 text-sm font-black rounded-xl bg-primary text-primary-foreground shadow-sm shrink-0" disabled={isSubmitting || isAddressLoading} onClick={handleSubmit}>
              {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : "등록"}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function AddZonePage() {
  return (
    <Suspense fallback={<div className="h-screen w-full flex items-center justify-center bg-background"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>}>
      <AddZoneContent />
    </Suspense>
  )
}
