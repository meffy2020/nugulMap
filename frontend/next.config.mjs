/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [
      {
        // 이 설정은 /api/ 로 시작하는 모든 요청을 잡아냅니다.
        source: '/api/:path*',
        // 그리고 그 요청을 백엔드 서버의 해당 경로로 그대로 전달합니다.
        // 예: /api/zones -> http://localhost:8080/zones
        destination: 'http://localhost:8080/:path*',
      },
    ];
  },
};

export default nextConfig;