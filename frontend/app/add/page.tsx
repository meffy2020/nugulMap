"use client"

import { useState, useCallback, useRef, useEffect, Suspense } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { FixedPinMap } from "@/components/add-zone/fixed-pin-map"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ArrowLeft, Search, Navigation, Camera, X, Loader2, MapPin, Building2, Trees, Warehouse } from "lucide-react"
import { cn } from "@/lib/utils"
import { createZone, type CreateZonePayload } from "@/lib/api"
import { useToast } from "@/hooks/use-toast"

function AddZoneContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()
  
  // URL 파라미터에서 초기 위치 가져오기
  const initialLat = parseFloat(searchParams.get("lat") || "37.5665")
  const initialLng = parseFloat(searchParams.get("lng") || "126.978")

  // State
  const [address, setAddress] = useState("위치 확인 중...")
  const [region, setRegion] = useState("서울특별시")
  const [coords, setCoords] = useState({ lat: initialLat, lng: initialLng })
  const [isAddressLoading, setIsAddressLoading] = useState(false)
  
  const [type, setType] = useState("부스") // 부스, 개방, 실내
  const [description, setDescription] = useState("")
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const fileInputRef = useRef<HTMLInputElement>(null)

  // 🌍 주소 변환 로직 (Geocoding)
  const updateAddress = useCallback((lat: number, lng: number, retryCount = 0) => {
    if (!window.kakao?.maps?.services) {
      if (retryCount < 5) {
        setTimeout(() => updateAddress(lat, lng, retryCount + 1), 500)
      } else {
        setAddress("주소 변환 실패 (API 로드 오류)")
        setIsAddressLoading(false)
      }
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
      } else {
        setAddress("주소를 찾을 수 없습니다.")
      }
    })
  }, [])

  const handleLocationChange = useCallback((lat: number, lng: number) => {
    setCoords({ lat, lng })
    updateAddress(lat, lng)
  }, [updateAddress])

  // 🖼️ 이미지 리사이징 함수 (실무용 최적화)
  const resizeImage = (file: File): Promise<File> => {
    return new Promise((resolve) => {
      const reader = new FileReader()
      reader.readAsDataURL(file)
      reader.onload = (event) => {
        const img = new Image()
        img.src = event.target?.result as string
        img.onload = () => {
          const canvas = document.createElement("canvas")
          let width = img.width
          let height = img.height
          const MAX_SIZE = 1280 // 최대 가로/세로 1280px로 제한

          if (width > height) {
            if (width > MAX_SIZE) {
              height *= MAX_SIZE / width
              width = MAX_SIZE
            }
          } else {
            if (height > MAX_SIZE) {
              width *= MAX_SIZE / height
              height = MAX_SIZE
            }
          }

          canvas.width = width
          canvas.height = height
          const ctx = canvas.getContext("2d")
          ctx?.drawImage(img, 0, 0, width, height)

          canvas.toBlob(
            (blob) => {
              if (blob) {
                const resizedFile = new File([blob], file.name, {
                  type: "image/jpeg",
                  lastModified: Date.now(),
                })
                resolve(resizedFile)
              } else {
                resolve(file)
              }
            },
            "image/jpeg",
            0.8 // 품질 80% (용량 대폭 절감)
          )
        }
      }
    })
  }

  const handleImageChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      // 리사이징 진행
      const optimizedFile = await resizeImage(file)
      setImageFile(optimizedFile)
      setImagePreview(URL.createObjectURL(optimizedFile))
      console.log(`[v0] Image optimized: ${(file.size / 1024).toFixed(1)}KB -> ${(optimizedFile.size / 1024).toFixed(1)}KB`)
    }
  }

  const handleSubmit = async () => {
    if (isSubmitting) return

    // 🛡️ 도배 방지 (Rate Limit - 30초)
    const LAST_SUBMIT_KEY = "nugul_last_submit"
    const lastSubmit = localStorage.getItem(LAST_SUBMIT_KEY)
    const now = Date.now()

    if (lastSubmit && now - parseInt(lastSubmit) < 30000) {
      const remaining = Math.ceil((30000 - (now - parseInt(lastSubmit))) / 1000)
      toast({
        title: "천천히 해주세요! ✋",
        description: `${remaining}초 후에 다시 등록할 수 있습니다.`,
        variant: "destructive",
      })
      return
    }

    setIsSubmitting(true)
    try {
      const payload: CreateZonePayload = {
        region,
        type: "일반구역",
        subtype: type,
        description: description || `${address}에 위치한 ${type}형 흡연구역`,
        latitude: coords.lat,
        longitude: coords.lng,
        address,
        user: "익명사용자",
      }

      await createZone(payload, imageFile || undefined)
      
      // 마지막 제출 시간 기록
      localStorage.setItem(LAST_SUBMIT_KEY, Date.now().toString())
      
      toast({
        title: "등록 완료! 👏",
        description: "너구리들을 위한 소중한 정보 감사합니다.",
      })
      // 메인으로 이동하면서 해당 위치로 줌인하기 위해 쿼리 파라미터 전달
      router.push(`/?lat=${coords.lat}&lng=${coords.lng}&zoom=true`)
    } catch (err) {
      console.error("Failed to create zone:", err)
      toast({
        title: "등록 실패",
        description: "잠시 후 다시 시도해주세요.",
        variant: "destructive",
      })
    } finally {
      setIsSubmitting(false)
    }
  }

  const ZONE_TYPES = [
    { id: "부스", label: "흡연부스", icon: Building2 },
    { id: "개방", label: "개방구역", icon: Trees },
    { id: "실내", label: "실내흡연", icon: Warehouse },
  ]

  return (
    <div className="relative h-screen w-full flex flex-col bg-background overflow-hidden">
      {/* 1. Header (Adjusted for Safe Area and visibility) */}
      <div className="absolute top-0 left-0 right-0 z-50 bg-gradient-to-b from-black/70 via-black/40 to-transparent pb-20 px-4 pointer-events-none transition-all" 
           style={{ paddingTop: 'env(safe-area-inset-top, 1rem)' }}>
        
        {/* Top Row: Back Button & Title (Moved up) */}
        <div className="flex items-center gap-3 pt-2 pointer-events-auto">
          <Button 
            variant="ghost" 
            size="icon" 
            className="text-white hover:bg-white/20 rounded-full h-10 w-10"
            onClick={() => router.back()}
          >
            <ArrowLeft className="w-6 h-6 shadow-sm" />
          </Button>
          <div className="flex-1">
             <h1 className="text-white font-bold text-base leading-tight drop-shadow-md">흡연구역 등록</h1>
             <p className="text-white/80 text-[10px] drop-shadow-sm font-medium">지도를 움직여 핀을 위치시켜주세요.</p>
          </div>
        </div>

        {/* Search Bar (Moved up to top-[105px]) */}
        <div className="absolute top-[105px] left-0 right-0 pointer-events-auto px-5">
          <div className="relative group">
            <div className="absolute inset-y-0 left-3.5 flex items-center pointer-events-none">
              <Search className="h-4 w-4 text-white/60 group-focus-within:text-white transition-colors" />
            </div>
            <Input 
              placeholder="건물명, 도로명 주소 검색" 
              className="pl-11 h-11 bg-white/10 backdrop-blur-md border border-white/10 text-white placeholder:text-white/40 rounded-xl shadow-sm focus-visible:ring-white/20 focus-visible:bg-white/20 transition-all border-none shadow-none text-sm"
            />
          </div>
        </div>
      </div>

      {/* 2. Map Layer */}
      <div className="absolute inset-0 w-full h-full z-0 bg-muted" style={{ height: '100vh', width: '100vw' }}>
        <FixedPinMap onLocationChange={handleLocationChange} bottomOffset={300} initialLat={initialLat} initialLng={initialLng} />
      </div>

      {/* 3. Bottom Sheet */}
      <div className="absolute bottom-0 left-0 right-0 bg-background rounded-t-[2rem] shadow-[0_-10px_40px_rgba(0,0,0,0.2)] z-50 flex flex-col transition-transform duration-300">
        <div className="w-full flex justify-center pt-3 pb-1 cursor-grab active:cursor-grabbing">
           <div className="w-12 h-1.5 bg-muted-foreground/20 rounded-full" />
        </div>

        <div className="p-5 pt-0 pb-8 space-y-4">
          
          {/* Address (Simplified) */}
          <div className="flex items-start gap-2 pt-2">
             <MapPin className="w-5 h-5 text-primary mt-0.5 shrink-0" />
             <h2 className="text-lg font-black text-foreground leading-tight line-clamp-2">
               {isAddressLoading ? (
                 <span className="animate-pulse text-muted-foreground">위치 확인 중...</span>
               ) : (
                 address
               )}
             </h2>
          </div>

          <div className="h-px bg-border/50" />

          {/* Type & Photo Row */}
          <div className="grid grid-cols-[1fr_auto] gap-4">
            <div className="space-y-2">
              <label className="text-xs font-bold text-muted-foreground">유형 선택</label>
              <div className="grid grid-cols-3 gap-2 h-20">
                {ZONE_TYPES.map((t) => (
                  <button
                    key={t.id}
                    onClick={() => setType(t.id)}
                    className={cn(
                      "flex flex-col items-center justify-center rounded-xl border transition-all duration-200 p-1",
                      type === t.id 
                        ? "border-primary bg-primary/5 text-primary shadow-sm" 
                        : "border-border/40 bg-background text-muted-foreground hover:bg-muted/50"
                    )}
                  >
                    <t.icon className={cn("w-5 h-5 mb-1", type === t.id ? "fill-current" : "")} />
                    <span className="text-[10px] font-bold">{t.label}</span>
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-2 w-20">
               <label className="text-xs font-bold text-muted-foreground text-center block">사진</label>
               <div 
                 onClick={() => fileInputRef.current?.click()}
                 className={cn(
                   "w-full h-20 rounded-xl border-2 border-dashed flex flex-col items-center justify-center cursor-pointer overflow-hidden transition-all relative bg-muted/30 hover:bg-muted/50",
                   imagePreview ? "border-primary border-solid p-0" : "border-muted-foreground/30"
                 )}
               >
                 {imagePreview ? (
                   <img src={imagePreview} alt="Preview" className="w-full h-full object-cover" />
                 ) : (
                   <Camera className="w-6 h-6 text-muted-foreground/50" />
                 )}
               </div>
               <input type="file" ref={fileInputRef} onChange={handleImageChange} accept="image/*" className="hidden" />
            </div>
          </div>

          <Input 
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="상세 설명 (선택 사항)"
            className="h-10 rounded-xl bg-muted/30 border-border/50 text-sm"
          />

          <Button 
            className="w-full h-12 text-base font-black rounded-xl shadow-lg active:scale-[0.98] transition-all bg-primary text-primary-foreground hover:bg-primary/90"
            size="lg"
            disabled={isSubmitting || isAddressLoading}
            onClick={handleSubmit}
          >
            {isSubmitting ? (
              <Loader2 className="w-4 h-4 animate-spin mr-2" />
            ) : (
              "이 위치로 등록하기"
            )}
          </Button>
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
