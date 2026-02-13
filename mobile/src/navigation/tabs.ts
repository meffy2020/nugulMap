export type TabKey = "map" | "list" | "bookmark"

export const TABS: { key: TabKey; label: string }[] = [
  { key: "map", label: "지도" },
  { key: "list", label: "목록" },
  { key: "bookmark", label: "북마크" },
]
