"use client"

import { Button } from "@/components/ui/button"
import { Plus } from "lucide-react"
import { cn } from "@/lib/utils"

interface FloatingActionButtonProps {
  onClick: () => void
  className?: string
}

export function FloatingActionButton({ onClick, className }: FloatingActionButtonProps) {
  return (
    <Button
      onClick={onClick}
      className={cn(
        "group relative h-14 w-auto rounded-2xl bg-primary hover:bg-primary/90 text-primary-foreground shadow-2xl transition-all duration-300 pl-4 pr-5 gap-2 hover:scale-105 active:scale-95 animate-in slide-in-from-bottom-4 fade-in",
        className
      )}
    >
      <Plus className="h-6 w-6 group-hover:rotate-90 transition-transform duration-300" />
      <span className="font-bold tracking-tight">제보하기</span>
    </Button>
  )
}
