"use client"

import type React from "react"
import { useState, useEffect, useRef } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import * as z from "zod"
import Script from "next/script"

import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"

import { Loader2, Check, Camera, X, ImageIcon } from "lucide-react"
import { createZone, getCurrentUser, type SmokingZone, type CreateZonePayload } from "@/lib/api"

// Zod를 사용한 폼 유효성 검사 스키마 정의
const formSchema = z.object({
  address: z.string().min(5, { message: "주소는 5자 이상이어야 합니다." }),
  description: z.string().optional(),
  // region: z.string().min(2, { message: "지역을 입력해주세요." }), // 다운로드 버전에 UI가 없으므로 제거 (백엔드 API에는 필요하므로 내부적으로는 사용)
  region: z.string().optional(), // 다운로드 버전에 UI가 없으므로 optional로 변경 (카카오맵 Geocoder에서 자동 설정)
  type: z.string().min(1, { message: "유형을 선택해주세요." }),
  subtype: z.string().optional(),
  latitude: z.number(),
  longitude: z.number(),
  size: z.string().min(1, { message: "크기를 선택해주세요." }),
});

// 흡연구역 유형 상수 (다운로드 버전: 4개 옵션)
const ZONE_TYPES = [
  { value: "지정구역", label: "지정구역" },
  { value: "일반구역", label: "일반구역" },
  { value: "24시간", label: "24시간" },
  { value: "실외구역", label: "실외구역" },
]

interface AddLocationModalProps {
  isOpen: boolean
  onClose: () => void
  onZoneCreated?: (zone: SmokingZone) => void
}

export function AddLocationModal({ isOpen, onClose, onZoneCreated }: AddLocationModalProps) {
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showCamera, setShowCamera] = useState(false);

  const mapContainerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<any>(null)
  const markerRef = useRef<any>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      address: "",
      description: "",
      region: "", // 다운로드 버전에 UI가 없으므로 기본값만 유지 (카카오맵 Geocoder에서 자동 설정)
      type: "",
      subtype: "",
      latitude: 37.5665,
      longitude: 126.9780,
      size: "중형",
    },
  });

  // 모달이 열릴 때 현재 위치를 가져와 폼에 설정
  useEffect(() => {
    if (isOpen) {
      form.reset(); // 폼 초기화
      setError(null);
      setImageFile(null);
      setImagePreview(null);
      getCurrentLocationForForm();
    }
  }, [isOpen, form]);

  // 카카오맵 초기화
  useEffect(() => {
    if (isOpen && window.kakao?.maps && mapContainerRef.current && !mapRef.current) {
      initializeMap();
    }
  }, [isOpen]);

  // 위도/경도 변경 시 지도 마커 업데이트
  useEffect(() => {
    if (mapRef.current && markerRef.current) {
      const latitude = form.watch("latitude");
      const longitude = form.watch("longitude");
      const position = new window.kakao.maps.LatLng(latitude, longitude);
      mapRef.current.setCenter(position);
      markerRef.current.setPosition(position);
    }
  }, [form.watch("latitude"), form.watch("longitude")]);

  const initializeMap = () => {
    if (!window.kakao?.maps || !mapContainerRef.current) return;

    const container = mapContainerRef.current;
    const latitude = form.getValues("latitude");
    const longitude = form.getValues("longitude");
    const options = {
      center: new window.kakao.maps.LatLng(latitude, longitude),
      level: 3,
    };

    const map = new window.kakao.maps.Map(container, options);
    mapRef.current = map;

    // 마커 생성
    const markerPosition = new window.kakao.maps.LatLng(latitude, longitude);
    const marker = new window.kakao.maps.Marker({
      position: markerPosition,
    });
    marker.setMap(map);
    markerRef.current = marker;

    // 지도 클릭 시 마커 이동
    window.kakao.maps.event.addListener(map, 'click', (mouseEvent: any) => {
      const latlng = mouseEvent.latLng;
      marker.setPosition(latlng);
      form.setValue("latitude", latlng.getLat());
      form.setValue("longitude", latlng.getLng());
      
      // 카카오맵 Geocoder로 주소 변환
      if (window.kakao?.maps?.services) {
        const geocoder = new window.kakao.maps.services.Geocoder();
        geocoder.coord2Address(latlng.getLng(), latlng.getLat(), (result: any, status: any) => {
          if (status === window.kakao.maps.services.Status.OK && result[0]) {
            const addr = result[0].address;
            form.setValue("address", addr.address_name || form.getValues("address"));
            form.setValue("region", addr.region_1depth_name || form.getValues("region"));
          }
        });
      }
    });
  };

  const getCurrentLocationForForm = async () => {
    if (!navigator.geolocation) return;

    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const { latitude, longitude } = position.coords;
        
        form.setValue("latitude", latitude);
        form.setValue("longitude", longitude);
        form.setValue("address", `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`);

        // 카카오맵 Geocoder로 주소 변환
        if (window.kakao?.maps?.services) {
          const geocoder = new window.kakao.maps.services.Geocoder();
          geocoder.coord2Address(longitude, latitude, (result: any, status: any) => {
            if (status === window.kakao.maps.services.Status.OK && result[0]) {
              const addr = result[0].address;
              form.setValue("address", addr.address_name || form.getValues("address"));
              form.setValue("region", addr.region_1depth_name || form.getValues("region"));
            }
          });
        }
      },
      (error) => console.error("Location error:", error)
    );
  };

  // 카메라 시작
  const startCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "environment" },
      });
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        streamRef.current = stream;
      }
      setShowCamera(true);
    } catch (err) {
      console.error("Camera access denied:", err);
      alert("카메라 접근 권한이 필요합니다.");
    }
  };

  // 사진 촬영
  const capturePhoto = () => {
    if (!videoRef.current) return;

    const canvas = document.createElement("canvas");
    canvas.width = videoRef.current.videoWidth;
    canvas.height = videoRef.current.videoHeight;
    const ctx = canvas.getContext("2d");
    if (ctx) {
      ctx.drawImage(videoRef.current, 0, 0);
      canvas.toBlob((blob) => {
        if (blob) {
          const file = new File([blob], "photo.jpg", { type: "image/jpeg" });
          setImageFile(file);
          setImagePreview(URL.createObjectURL(blob));
          stopCamera();
        }
      }, "image/jpeg");
    }
  };

  // 카메라 중지
  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    setShowCamera(false);
  };

  // 파일 선택 핸들러
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setImageFile(file);
      setImagePreview(URL.createObjectURL(file));
    }
  };

  // 이미지 제거
  const removeImage = () => {
    setImageFile(null);
    setImagePreview(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  // 폼 제출 핸들러
  const onSubmit = async (values: z.infer<typeof formSchema>) => {
    setIsLoading(true);
    setError(null);

    try {
      // 현재 사용자 정보 가져오기
      const currentUser = await getCurrentUser();
      if (!currentUser) {
        setError("로그인이 필요합니다. 로그인 후 다시 시도해주세요.");
        setIsLoading(false);
        return;
      }

      const payload: CreateZonePayload = {
        ...values,
        subtype: values.subtype ?? "",
        description: values.description ?? "",
        user: currentUser.email, // 현재 사용자 이메일 사용
      };

      const newZone = await createZone(payload, imageFile || undefined);
      console.log("Zone created successfully:", newZone);

      setShowSuccess(true);
      onZoneCreated?.(newZone);

      setTimeout(() => {
        setShowSuccess(false);
        handleCancel();
      }, 1500);

    } catch (err) {
      console.error("Error creating zone:", err);
      setError(err instanceof Error ? err.message : "흡연구역 생성에 실패했습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  const handleCancel = () => {
    form.reset();
    removeImage();
    stopCamera();
    setError(null);
    if (mapRef.current) {
      mapRef.current = null;
    }
    if (markerRef.current) {
      markerRef.current = null;
    }
    onClose();
  };

  return (
    <>
      <Script
        src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${process.env.NEXT_PUBLIC_KAKAOMAP_APIKEY}&libraries=services&autoload=false`}
        strategy="beforeInteractive"
        onLoad={() => {
          if (window.kakao?.maps) {
            window.kakao.maps.load(() => {
              console.log("[v0] Kakao Maps loaded");
            });
          }
        }}
      />

      <Dialog open={isOpen} onOpenChange={handleCancel}>
        <DialogContent className="w-full max-w-md max-h-[95vh] overflow-y-auto bg-card border-border p-4">
          <DialogHeader>
            <DialogTitle className="text-lg font-semibold text-card-foreground">신규 흡연구역 등록</DialogTitle>
          </DialogHeader>

          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5">
            <div className="space-y-2">
              <Label htmlFor="address" className="text-sm font-medium text-card-foreground">
                주소
              </Label>
              <Input
                id="address"
                {...form.register("address")}
                placeholder="주소를 입력하세요"
                className="bg-input border-border text-foreground h-11"
              />
              {form.formState.errors.address && <p className="text-red-500 text-xs mt-1">{form.formState.errors.address.message}</p>}
            </div>

            <div className="space-y-2">
              <Label className="text-sm font-medium text-card-foreground">위치</Label>
              <div ref={mapContainerRef} className="w-full h-40 bg-muted rounded-lg border border-border" />
            </div>

            {/* 지역 필드 제거 - 다운로드 버전에 UI가 없으므로 제거 (카카오맵 Geocoder에서 자동으로 region 설정) */}
            {/* <div className="space-y-2">
              <Label htmlFor="region" className="text-sm font-medium text-card-foreground">
                지역
              </Label>
              <Input
                id="region"
                {...form.register("region")}
                placeholder="지역을 입력하세요 (예: 서울시 강남구)"
                className="bg-input border-border text-foreground h-11"
              />
              {form.formState.errors.region && <p className="text-red-500 text-xs mt-1">{form.formState.errors.region.message}</p>}
            </div> */}

            <div className="space-y-3">
              <Label className="text-sm font-medium text-card-foreground">유형 선택</Label>
              <div className="grid grid-cols-2 gap-2">
                {ZONE_TYPES.map((type) => (
                  <Button
                    key={type.value}
                    type="button"
                    variant={form.watch("type") === type.value ? "default" : "outline"}
                    className={`h-12 text-sm font-medium ${
                      form.watch("type") === type.value
                        ? "bg-primary text-primary-foreground"
                        : "bg-background text-foreground border-border hover:bg-accent"
                    } transition-all`}
                    onClick={() => form.setValue("type", type.value)}
                  >
                    {type.label}
                  </Button>
                ))}
              </div>
              {form.formState.errors.type && <p className="text-red-500 text-xs mt-1">{form.formState.errors.type.message}</p>}
            </div>

            <div className="space-y-2">
              <Label htmlFor="size" className="text-sm font-medium text-card-foreground">크기</Label>
              <Select onValueChange={(value) => form.setValue("size", value)} defaultValue={form.getValues("size")}>
                <SelectTrigger><SelectValue placeholder="크기" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="소형">소형</SelectItem>
                  <SelectItem value="중형">중형</SelectItem>
                  <SelectItem value="대형">대형</SelectItem>
                </SelectContent>
              </Select>
              {form.formState.errors.size && <p className="text-red-500 text-xs mt-1">{form.formState.errors.size.message}</p>}
            </div>

            <div className="space-y-2">
              <Label htmlFor="description" className="text-sm font-medium text-card-foreground">상세 설명</Label>
              <Textarea
                id="description"
                {...form.register("description")}
                placeholder="상세 설명을 입력하세요"
                className="bg-input border-border text-foreground"
              />
            </div>

            <div className="space-y-3">
              <Label className="text-sm font-medium text-card-foreground">사진 추가</Label>
              {!imagePreview ? (
                <div className="space-y-2">
                  <Button
                    type="button"
                    variant="outline"
                    className="w-full h-24 border-2 border-dashed border-border hover:border-primary transition-all bg-transparent"
                    onClick={startCamera}
                  >
                    <div className="flex flex-col items-center gap-2">
                      <Camera className="h-7 w-7" />
                      <span className="text-sm font-medium">사진 촬영</span>
                    </div>
                  </Button>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={handleFileSelect}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    className="w-full h-12 bg-transparent border-border hover:bg-accent font-medium"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <ImageIcon className="h-5 w-5 mr-2" />
                    갤러리에서 선택
                  </Button>
                </div>
              ) : (
                <div className="relative">
                  <img
                    src={imagePreview || "/placeholder.svg"}
                    alt="Preview"
                    className="w-full h-48 object-cover rounded-lg border border-border"
                  />
                  <Button
                    type="button"
                    variant="destructive"
                    size="icon"
                    className="absolute top-2 right-2 h-8 w-8"
                    onClick={removeImage}
                  >
                    <X className="h-5 w-5" />
                  </Button>
                </div>
              )}
            </div>

            {error && (
              <div className="text-red-500 text-sm bg-red-50 p-3 rounded-md border border-red-200">{error}</div>
            )}

            <div className="flex flex-col gap-2 pt-2">
              <Button
                type="submit"
                disabled={isLoading}
                className="w-full h-12 bg-primary hover:bg-primary/90 text-primary-foreground font-medium"
              >
                {isLoading ? (
                  <div className="flex items-center gap-2">
                    <Loader2 className="h-5 w-5 animate-spin" />
                    저장 중...
                  </div>
                ) : showSuccess ? (
                  <div className="flex items-center gap-2">
                    <Check className="h-5 w-5" />
                    완료!
                  </div>
                ) : (
                  "저장하기"
                )}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={handleCancel}
                disabled={isLoading}
                className="w-full h-12 border-border text-foreground bg-transparent hover:bg-accent font-medium"
              >
                취소
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {showCamera && (
        <Dialog open={showCamera} onOpenChange={stopCamera}>
          <DialogContent className="w-full max-w-md">
            <DialogHeader>
              <DialogTitle>사진 촬영</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <video ref={videoRef} autoPlay playsInline className="w-full rounded-lg border border-border" />
              <div className="flex flex-col gap-2">
                <Button type="button" onClick={capturePhoto} className="w-full h-12">
                  사진 촬영
                </Button>
                <Button type="button" variant="outline" onClick={stopCamera} className="w-full h-12 bg-transparent">
                  취소
                </Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      )}
    </>
  )
}
