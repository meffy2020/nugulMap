"use client"

import type React from "react"

import { useState } from "react"
import { Search } from "lucide-react"
import { cn } from "@/lib/utils"

interface SearchBarProps {
  className?: string
  placeholder?: string
  onSearch?: (query: string) => void
}

export function SearchBar({ className, placeholder = "흡연구역 검색...", onSearch }: SearchBarProps) {
  const [query, setQuery] = useState("")

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (onSearch && query.trim()) {
      onSearch(query.trim())
    }
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setQuery(e.target.value)
  }

  return (
    <form onSubmit={handleSubmit} className={cn("relative", className)}>
      <div className="relative flex items-center">
        <input
          type="text"
          value={query}
          onChange={handleInputChange}
          placeholder={placeholder}
          className="w-full h-12 pl-4 pr-12 bg-card/95 backdrop-blur-sm border border-border rounded-full text-card-foreground placeholder-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary/50 transition-all duration-200 shadow-sm hover:shadow-md"
        />
        <button
          type="submit"
          className="absolute right-3 p-2 text-muted-foreground hover:text-primary transition-colors duration-200"
        >
          <Search className="w-5 h-5" />
        </button>
      </div>
    </form>
  )
}
