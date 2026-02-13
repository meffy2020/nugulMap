export type TabKey = "map" | "list" | "bookmark"

export const TABS: { key: TabKey; label: string; icon: "map-outline" | "format-list-bulleted" | "star-outline" }[] = [
  { key: "map", label: "지도", icon: "map-outline" },
  { key: "list", label: "목록", icon: "format-list-bulleted" },
  { key: "bookmark", label: "북마크", icon: "star-outline" },
]
