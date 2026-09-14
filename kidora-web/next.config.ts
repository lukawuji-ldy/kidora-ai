import type { NextConfig } from "next";

const backendUrl = process.env.BACKEND_URL || "http://127.0.0.1:8080";
// CET 走 Route Handler：`src/app/api/cet/[...path]/route.ts`（CET_BACKEND_URL），不用 rewrite。
// 原因：Next rewrite/proxy 约 1MB body 上限，语音 stream 的 audioBase64 易 SSE HTTP 500。

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      { source: "/api/auth/:path*", destination: `${backendUrl}/api/auth/:path*` },
      { source: "/api/chat/:path*", destination: `${backendUrl}/api/chat/:path*` },
      { source: "/api/learners", destination: `${backendUrl}/api/learners` },
      { source: "/api/learners/:path*", destination: `${backendUrl}/api/learners/:path*` },
    ];
  },
};

export default nextConfig;
