"use client"

import type React from "react"

import { useState } from "react"
import { Search, X } from "lucide-react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"

interface SearchBarProps {
  className?: string
  placeholder?: string
  onSearch?: (query: string) => void
}

export function SearchBar({ className, placeholder = "장소, 주소 검색...", onSearch }: SearchBarProps) {
  const [query, setQuery] = useState("")

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (onSearch && query.trim()) {
      onSearch(query.trim())
    }
  }

  const handleClear = () => {
    setQuery("")
  }

  return (
    <form 
      onSubmit={handleSubmit} 
      className={cn(
        "relative flex items-center w-full h-14 bg-background/95 backdrop-blur-md border border-border shadow-lg rounded-2xl px-4 transition-all duration-300 focus-within:ring-2 focus-within:ring-primary/20 focus-within:border-primary/50 group",
        className
      )}
    >
      <Search className="w-5 h-5 text-muted-foreground group-focus-within:text-primary transition-colors" />
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={placeholder}
        className="flex-1 h-full bg-transparent border-none outline-none px-3 text-base text-foreground placeholder:text-muted-foreground/70"
      />
      {query && (
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-8 w-8 rounded-full text-muted-foreground hover:text-foreground"
          onClick={handleClear}
        >
          <X className="w-4 h-4" />
        </Button>
      )}
      <div className="w-[1px] h-6 bg-border mx-2" />
      <Button 
        type="submit" 
        variant="ghost" 
        size="sm" 
        className="text-primary font-bold hover:bg-primary/5"
      >
        검색
      </Button>
    </form>
  )
}
