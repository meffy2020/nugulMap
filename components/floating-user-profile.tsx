"use client"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { User, Settings, LogOut, MapPin } from "lucide-react"
import Link from "next/link"

export function FloatingUserProfile() {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className="h-12 w-12 rounded-full p-0 bg-background/80 backdrop-blur-md border border-border/50 hover:bg-background/90 hover:scale-105 transition-all duration-200 shadow-lg"
        >
          <Avatar className="h-10 w-10">
            <AvatarImage src="/neutral-user-avatar.png" alt="사용자" />
            <AvatarFallback className="bg-primary/10 text-primary">
              <User className="h-5 w-5" />
            </AvatarFallback>
          </Avatar>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        className="w-56 bg-background/95 backdrop-blur-md border-border/50 shadow-xl animate-in slide-in-from-top-2 duration-200"
      >
        <div className="px-3 py-2">
          <p className="text-sm font-medium">김철수</p>
          <p className="text-xs text-muted-foreground">kim@example.com</p>
        </div>
        <DropdownMenuSeparator />
        <Link href="/profile">
          <DropdownMenuItem className="hover:bg-accent/50 transition-colors cursor-pointer">
            <User className="mr-2 h-4 w-4" />
            프로필
          </DropdownMenuItem>
        </Link>
        <DropdownMenuItem className="hover:bg-accent/50 transition-colors cursor-pointer">
          <MapPin className="mr-2 h-4 w-4" />내 장소
        </DropdownMenuItem>
        <DropdownMenuItem className="hover:bg-accent/50 transition-colors cursor-pointer">
          <Settings className="mr-2 h-4 w-4" />
          설정
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem className="hover:bg-destructive/10 text-destructive transition-colors cursor-pointer">
          <LogOut className="mr-2 h-4 w-4" />
          로그아웃
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
