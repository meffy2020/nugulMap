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
          className="w-full h-12 pl-4 pr-12 bg-gray-200/95 backdrop-blur-sm border border-gray-300/60 rounded-full text-gray-800 placeholder-gray-600 focus:outline-none focus:ring-2 focus:ring-orange-400/50 focus:border-orange-400/50 transition-all duration-200 shadow-sm hover:shadow-md"
        />
        <button
          type="submit"
          className="absolute right-3 p-2 text-gray-600 hover:text-orange-500 transition-colors duration-200"
        >
          <Search className="w-5 h-5" />
        </button>
      </div>
    </form>
  )
}
