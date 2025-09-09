"use client"

import { Button } from "@/components/ui/button"
import { Plus } from "lucide-react"

interface FloatingActionButtonProps {
  onClick: () => void
}

export function FloatingActionButton({ onClick }: FloatingActionButtonProps) {
  return (
    <Button
      onClick={onClick}
      className="fixed bottom-6 right-6 h-14 w-14 rounded-full bg-primary hover:bg-primary/90 text-primary-foreground shadow-lg hover:shadow-xl transition-all duration-300 z-40 hover:scale-110 active:scale-95 animate-in slide-in-from-bottom-4 fade-in"
      size="icon"
    >
      <Plus className="h-6 w-6 transition-transform duration-300 hover:rotate-90" />
    </Button>
  )
}
