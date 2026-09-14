import { NextRequest } from "next/server";

/**
 * CET API 代理：绕过 Next.js `rewrites` 约 1MB 请求体上限。
 * 语音陪练 `audioBase64`（16k WAV）十余秒即可超过该限制，表现为前端 `SSE HTTP 500`。
 *
 * @author liudy
 */
const cetBackend = () => process.env.CET_BACKEND_URL || "http://127.0.0.1:8082";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 120;

type RouteCtx = { params: Promise<{ path: string[] }> };

async function proxy(req: NextRequest, pathSegments: string[]): Promise<Response> {
  const path = pathSegments.filter(Boolean).join("/");
  const incoming = new URL(req.url);
  const target = `${cetBackend()}/api/cet/${path}${incoming.search}`;

  const headers = new Headers();
  const auth = req.headers.get("authorization");
  if (auth) headers.set("authorization", auth);
  const contentType = req.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  const accept = req.headers.get("accept");
  if (accept) headers.set("accept", accept);

  const init: RequestInit = {
    method: req.method,
    headers,
    redirect: "manual",
  };

  if (req.method !== "GET" && req.method !== "HEAD") {
    // 整包缓冲：绕过 rewrite 约 1MB 截断；对齐 cet-tutor-server codec 16MB 量级
    const buf = await req.arrayBuffer();
    if (buf.byteLength > 0) {
      init.body = buf;
    }
  }

  let upstream: Response;
  try {
    upstream = await fetch(target, init);
  } catch (err) {
    const detail = err instanceof Error ? err.message : String(err);
    console.error("[cet-proxy] upstream unreachable", target, detail);
    return Response.json(
      {
        code: "INTERNAL_ERROR",
        message: `无法连接 CET 后端 (${cetBackend()}): ${detail}`,
        data: null,
      },
      { status: 502 },
    );
  }
  const outHeaders = new Headers();
  const passThrough = ["content-type", "cache-control", "x-accel-buffering"];
  for (const name of passThrough) {
    const value = upstream.headers.get(name);
    if (value) outHeaders.set(name, value);
  }
  const ct = upstream.headers.get("content-type") || "";
  if (ct.includes("text/event-stream")) {
    outHeaders.set("cache-control", "no-cache, no-transform");
    outHeaders.set("connection", "keep-alive");
  }

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: outHeaders,
  });
}

export async function GET(req: NextRequest, ctx: RouteCtx) {
  return proxy(req, (await ctx.params).path);
}

export async function POST(req: NextRequest, ctx: RouteCtx) {
  return proxy(req, (await ctx.params).path);
}

export async function PUT(req: NextRequest, ctx: RouteCtx) {
  return proxy(req, (await ctx.params).path);
}

export async function PATCH(req: NextRequest, ctx: RouteCtx) {
  return proxy(req, (await ctx.params).path);
}

export async function DELETE(req: NextRequest, ctx: RouteCtx) {
  return proxy(req, (await ctx.params).path);
}
