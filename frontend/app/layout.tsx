import type React from "react"
import type { Metadata } from "next"
import "./globals.css"

export const metadata: Metadata = {
  title: "너굴맵 - Nugul Map",
  description: "흡연구역 위치 서비스",
  generator: "v0.app",
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="ko">
      <head>
        <style>{`
html {
  font-family: 'Kakao', ui-sans-serif, system-ui, sans-serif;
}
        `}</style>
      </head>
      <body>{children}</body>
    </html>
  )
}
