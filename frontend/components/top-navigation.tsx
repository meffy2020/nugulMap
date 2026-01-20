"use client"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { User, LogOut, LogIn } from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import Image from "next/image"
import { useAuth } from "@/hooks/use-auth"
import { getImageUrl } from "@/lib/api"

import { useAuth } from "@/hooks/use-auth"
import { getImageUrl } from "@/lib/api"

export function TopNavigation() {
  const { user, login, logout, isLoading } = useAuth()
  const router = useRouter()

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 h-16 bg-background/80 backdrop-blur-md border-b border-border/50 px-6 flex items-center justify-between transition-all duration-300 shadow-sm">
      <div className="flex items-center gap-2 group cursor-pointer" onClick={() => router.push("/")}>
        <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center shadow-md group-hover:scale-110 transition-transform">
           <Image src="/images/pin.png" alt="Logo" width={20} height={20} className="invert brightness-0" />
        </div>
        <h1 className="text-xl font-black text-foreground tracking-tighter">
          NugulMap
        </h1>
      </div>

      {isLoading ? (
        <div className="h-10 w-10 rounded-full bg-muted animate-pulse" />
      ) : user ? (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              className="relative h-10 w-10 rounded-full transition-all duration-300 hover:scale-110 hover:bg-secondary/20"
            >
              <Avatar className="h-10 w-10 ring-2 ring-border group-hover:ring-primary/30 transition-all">
                <AvatarImage src={getImageUrl(user.profileImage) || "/neutral-user-avatar.png"} alt={user.nickname} />
                <AvatarFallback className="bg-secondary text-secondary-foreground">
                  <User className="h-5 w-5" />
                </AvatarFallback>
              </Avatar>
            </Button>
          </DropdownMenuTrigger>
...
            <DropdownMenuItem 
              className="cursor-pointer transition-all duration-200 hover:bg-secondary/80 hover:scale-[1.02]"
              onClick={logout}
            >
              <LogOut className="mr-2 h-4 w-4 transition-colors duration-200" />
              <span>로그아웃</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      ) : (
        <Button 
          variant="default" 
          onClick={login}
          className="transition-all duration-300 hover:scale-105 rounded-xl font-bold px-6 shadow-lg active:scale-95"
        >
          <LogIn className="mr-2 h-4 w-4" />
          로그인
        </Button>
      )}
    </nav>
  )
}
