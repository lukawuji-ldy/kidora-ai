# Kidora Web

用户前台（Next.js）：家长登录、儿童英语 CET 陪练、最小通用 Chat。

## 主要功能

- 登录 / 注册、个人中心与儿童档案
- CET 文本陪练（开课 → 多轮对话 → 结课报告）；语音依赖后端 MCP
- 通用 Chat（代理至 `kidora-agent-server`）
- 教具 / 舞台等与 CET SSE 事件联动的展示（以后端权威为准）

## 架构位置

```
浏览器 ──► kidora-web (:3000)
              ├── /api/auth|chat|learners ──► kidora-agent-server (:8080)
              └── /api/cet/*              ──► cet-tutor-server (:8082)
```

JWT 存 `localStorage`（`kidora_token`），请求头 `Authorization: Bearer`。

## 与 AI Agent 的关系（用户侧）

前端不实现规划器。用户感知到的课时流程对应后端 **Plan-and-Execute**：

1. 开课 → 展示计划摘要（Plan）
2. 对话区多轮陪练（Practice / Tutor 小循环）
3. 结课 → 报告页（Evaluate；再规划在服务端阶段边界完成）

## 技术栈

Next.js 15 · React · TypeScript

## 最小本地启动

需先启动 `:8080`；CET 需 `:8082`；录音 / TTS 需 `:8081` MCP。

```bash
cp .env.local.example .env.local
npm install
npm run dev
# http://localhost:3000
```

演示账号：`parent1` / `parent123`。

## 代理

| 前缀 | 目标 |
|---|---|
| `/api/auth/*`、`/api/chat/*`、`/api/learners` | `BACKEND_URL`（默认 `http://127.0.0.1:8080`） |
| `/api/cet/*` | `CET_BACKEND_URL`（默认 `http://127.0.0.1:8082`） |

CET 语音大 body 走 Route Handler 转发（避免代理层体积限制）。

## 相关模块

- 仓库总览：[../README.md](../README.md)
- [`kidora-agent-server`](../kidora-agent-server/README.md) · [`cet-tutor-server`](../cet-tutor-server/README.md)
