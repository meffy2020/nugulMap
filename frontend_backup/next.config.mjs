/** @type {import('next').NextConfig} */
const nextConfig = {
  eslint: {
    ignoreDuringBuilds: true,
  },
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
  // rewrites 제거: 모든 API 요청은 Nginx를 통해 전달됨
  // 프론트엔드에서 /api/* 요청 시 브라우저가 현재 호스트로 요청하고,
  // Nginx가 /api 요청을 백엔드로 프록시함
}

export default nextConfig
