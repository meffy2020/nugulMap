"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ArrowLeft, MapPin, Calendar, Loader2 } from "lucide-react"
import Link from "next/link"
import Image from "next/image"
import { useAuth } from "@/hooks/use-auth"
import { fetchUserZones, getImageUrl, type SmokingZone, deleteZone } from "@/lib/api"
import { useToast } from "@/hooks/use-toast"

export default function ProfilePage() {
  const { user, isLoading: isAuthLoading } = useAuth()
  const [userZones, setUserZones] = useState<SmokingZone[]>([])
  const [isLoadingZones, setIsLoadingZones] = useState(false)
  const { toast } = useToast()

  const loadZones = async () => {
    if (user) {
      setIsLoadingZones(true)
      try {
        const zones = await fetchUserZones()
        setUserZones(zones)
      } catch (err) {
        console.error("Failed to load user zones:", err)
      } finally {
        setIsLoadingZones(false)
      }
    }
  }

  useEffect(() => {
    loadZones()
  }, [user])

  const handleDeleteZone = async (id: number) => {
    if (!confirm("정말 이 장소를 삭제하시겠습니까?")) return

    try {
      await deleteZone(id)
      toast({
        title: "삭제 완료",
        description: "제보하신 장소가 삭제되었습니다.",
      })
      loadZones() // 목록 새로고침
    } catch (err) {
      console.error("Failed to delete zone:", err)
      toast({
        title: "삭제 실패",
        description: "장소 삭제 중 문제가 발생했습니다.",
        variant: "destructive",
      })
    }
  }

  if (isAuthLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!user) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center p-4">
        <h1 className="text-xl font-bold mb-4">로그인이 필요합니다.</h1>
        <Link href="/login">
          <Button>로그인하러 가기</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <div className="sticky top-0 z-40 bg-background/80 backdrop-blur-md border-b border-border/50 shadow-sm">
        <div className="container mx-auto px-4 py-4">
          <div className="flex items-center gap-4">
            <Link href="/">
              <Button variant="ghost" size="sm" className="hover:bg-accent/50 rounded-xl">
                <ArrowLeft className="h-4 w-4 mr-2" />
                지도로 돌아가기
              </Button>
            </Link>
            <h1 className="text-xl font-black tracking-tight">마이 프로필</h1>
          </div>
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <Tabs defaultValue="profile" className="space-y-6">
          <TabsList className="grid w-full grid-cols-2 p-1 bg-muted/50 rounded-2xl h-12">
            <TabsTrigger value="profile" className="rounded-xl font-bold">내 정보</TabsTrigger>
            <TabsTrigger value="zones" className="rounded-xl font-bold">내가 등록한 장소</TabsTrigger>
          </TabsList>

          <TabsContent value="profile" className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
            <Card className="border-none shadow-xl rounded-[2rem] overflow-hidden">
              <div className="h-24 bg-primary/5" />
              <CardContent className="p-8 -mt-12">
                <div className="flex flex-col md:flex-row items-center md:items-end gap-6 mb-8">
                  <Avatar className="h-32 w-32 ring-4 ring-background shadow-2xl">
                    <AvatarImage src={getImageUrl(user.profileImage) || "/neutral-user-avatar.png"} alt={user.nickname} />
                    <AvatarFallback className="text-4xl font-bold">{user.nickname[0]}</AvatarFallback>
                  </Avatar>
                  <div className="flex-1 text-center md:text-left space-y-1">
                    <h2 className="text-3xl font-black tracking-tight text-foreground">{user.nickname}</h2>
                    <p className="text-muted-foreground font-medium">{user.email}</p>
                    <div className="flex items-center justify-center md:justify-start gap-4 text-xs font-bold text-muted-foreground pt-2">
                      <div className="flex items-center gap-1 bg-muted px-3 py-1.5 rounded-full">
                        <Calendar className="h-3 w-3" />
                        {new Date(user.createdAt).toLocaleDateString()} 가입
                      </div>
                      <div className="flex items-center gap-1 bg-muted px-3 py-1.5 rounded-full">
                        <MapPin className="h-3 w-3" />
                        {userZones.length}개 장소 제보
                      </div>
                    </div>
                  </div>
                </div>

                <div className="grid gap-6">
                  <div className="space-y-2">
                    <Label className="text-xs font-black uppercase tracking-widest text-muted-foreground ml-1">이메일 계정</Label>
                    <Input value={user.email} disabled className="bg-muted/30 border-border/50 h-12 rounded-xl" />
                  </div>
                  <div className="space-y-2">
                    <Label className="text-xs font-black uppercase tracking-widest text-muted-foreground ml-1">닉네임</Label>
                    <Input value={user.nickname} disabled className="bg-muted/30 border-border/50 h-12 rounded-xl" />
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card className="border-none shadow-lg rounded-[2rem] bg-primary text-primary-foreground">
              <CardContent className="p-8">
                <h3 className="text-lg font-bold mb-4 opacity-90">활동 요약</h3>
                <div className="grid grid-cols-3 gap-4 text-center">
                  <div className="space-y-1">
                    <div className="text-3xl font-black">{userZones.length}</div>
                    <div className="text-[10px] font-bold uppercase tracking-wider opacity-70">등록한 장소</div>
                  </div>
                  <div className="space-y-1">
                    <div className="text-3xl font-black">0</div>
                    <div className="text-[10px] font-bold uppercase tracking-wider opacity-70">도움됨</div>
                  </div>
                  <div className="space-y-1">
                    <div className="text-3xl font-black">
                      {Math.floor((new Date().getTime() - new Date(user.createdAt).getTime()) / (1000 * 60 * 60 * 24))}
                    </div>
                    <div className="text-[10px] font-bold uppercase tracking-wider opacity-70">활동 일수</div>
                  </div>
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="zones" className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
            <Card className="border-none shadow-xl rounded-[2rem]">
              <CardHeader className="p-8 pb-0">
                <CardTitle className="text-xl font-black">내가 제보한 장소들</CardTitle>
                <CardDescription className="font-medium">지금까지 총 {userZones.length}개의 흡연구역을 공유해주셨습니다.</CardDescription>
              </CardHeader>
              <CardContent className="p-8">
                {isLoadingZones ? (
                  <div className="flex justify-center py-12">
                    <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
                  </div>
                ) : userZones.length === 0 ? (
                  <div className="text-center py-12 space-y-4 bg-muted/20 rounded-[1.5rem] border-2 border-dashed border-border/50">
                    <MapPin className="w-12 h-12 mx-auto text-muted-foreground/30" />
                    <p className="text-muted-foreground font-bold">아직 제보한 장소가 없습니다.</p>
                    <Link href="/">
                      <Button variant="outline" className="rounded-xl border-2 font-bold">지도로 가서 제보하기</Button>
                    </Link>
                  </div>
                ) : (
                  <div className="grid gap-4">
                    {userZones.map((zone) => (
                      <Card key={zone.id} className="border-border/50 shadow-sm rounded-[1.5rem] hover:shadow-md transition-all group overflow-hidden">
                        <CardContent className="p-0">
                          <div className="flex flex-col sm:flex-row">
                            {zone.image ? (
                              <div className="relative w-full sm:w-48 h-32">
                                <Image
                                  src={getImageUrl(zone.image) || "/placeholder.svg"}
                                  alt={zone.address}
                                  fill
                                  className="object-cover"
                                />
                              </div>
                            ) : (
                               <div className="w-full sm:w-48 h-32 bg-muted flex items-center justify-center">
                                 <MapPin className="w-8 h-8 text-muted-foreground/20" />
                               </div>
                            )}
                            <div className="flex-1 p-5 space-y-2">
                              <div className="flex items-start justify-between">
                                <h3 className="font-black text-lg tracking-tight">{zone.address}</h3>
                                <div className="flex items-center gap-2">
                                  <Button 
                                    variant="ghost" 
                                    size="sm" 
                                    className="h-8 w-8 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
                                    onClick={() => handleDeleteZone(zone.id)}
                                  >
                                    <X className="h-4 w-4" />
                                  </Button>
                                  <Badge variant="secondary" className="rounded-lg font-black text-[10px] px-2 py-0.5">{zone.type}</Badge>
                                </div>
                              </div>
                              <p className="text-sm text-muted-foreground font-medium line-clamp-1">{zone.description || "상세 설명이 없습니다."}</p>
                              <div className="flex items-center gap-4 text-[10px] font-bold text-muted-foreground/60 uppercase tracking-widest pt-1">
                                <span>크기: {zone.size}</span>
                                <span>구역: {zone.subtype || "일반"}</span>
                              </div>
                            </div>
                          </div>
                        </CardContent>
                      </Card>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  )
}
