import type { NextConfig } from "next";

const backendUrl = process.env.BACKEND_URL || "http://127.0.0.1:8080";
const cetBackendUrl = process.env.CET_BACKEND_URL || "http://127.0.0.1:8082";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      { source: "/api/auth/:path*", destination: `${backendUrl}/api/auth/:path*` },
      { source: "/api/chat/:path*", destination: `${backendUrl}/api/chat/:path*` },
      { source: "/api/learners", destination: `${backendUrl}/api/learners` },
      { source: "/api/learners/:path*", destination: `${backendUrl}/api/learners/:path*` },
      { source: "/api/cet/:path*", destination: `${cetBackendUrl}/api/cet/:path*` },
    ];
  },
};

export default nextConfig;
