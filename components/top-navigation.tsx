"use client"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { User, LogOut } from "lucide-react"

export function TopNavigation() {
  return (
    <nav className="fixed top-0 left-0 right-0 z-50 h-16 bg-background/95 backdrop-blur-sm border-b border-border px-4 flex items-center justify-between transition-all duration-300">
      <div className="flex items-center">
        <h1 className="text-xl font-bold text-foreground transition-all duration-300 hover:text-primary cursor-default">
          너굴맵
        </h1>
      </div>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            className="relative h-10 w-10 rounded-full transition-all duration-300 hover:scale-110 hover:bg-secondary/50"
          >
            <Avatar className="h-10 w-10 transition-all duration-300 hover:ring-2 hover:ring-primary/30">
              <AvatarImage src="/diverse-user-avatars.png" alt="User Avatar" />
              <AvatarFallback className="bg-secondary text-secondary-foreground">
                <User className="h-5 w-5" />
              </AvatarFallback>
            </Avatar>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          className="w-56 animate-in slide-in-from-top-2 fade-in duration-300"
          align="end"
          forceMount
        >
          <DropdownMenuItem className="cursor-pointer transition-all duration-200 hover:bg-secondary/80 hover:scale-[1.02]">
            <User className="mr-2 h-4 w-4 transition-colors duration-200" />
            <span>내 정보</span>
          </DropdownMenuItem>
          <DropdownMenuItem className="cursor-pointer transition-all duration-200 hover:bg-secondary/80 hover:scale-[1.02]">
            <LogOut className="mr-2 h-4 w-4 transition-colors duration-200" />
            <span>로그아웃</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </nav>
  )
}
