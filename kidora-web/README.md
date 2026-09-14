# Kidora Web

Next.js 用户前台（登录、CET 文本陪练、通用 Chat）。

## 开发

```bash
cp .env.local.example .env.local
npm install
npm run dev
```

默认 `http://localhost:3000`。需先启动：

- `kidora-agent-server` `:8080`（Auth / Chat / learners）
- `cet-tutor-server` `:8082`（CET；语音需 `kidora.mcp.enabled=true`，默认已开）
- `kidora-mcp-server` `:8081`（录音 / TTS / 发音；本地可用 `kidora.speech.mode=stub`）

仅文本陪练可不启 MCP；点「录音」会报「语音输入需要启用 MCP 语音能力」或 ASR 失败。

演示账号：`parent1` / `parent123`。

## 代理

| 前缀 | 目标 |
|---|---|
| `/api/auth/*` `/api/chat/*` `/api/learners` | `BACKEND_URL`（8080） |
| `/api/cet/*` | `CET_BACKEND_URL`（8082） |

JWT 存在 `localStorage`（`kidora_token`），请求头 `Authorization: Bearer`。
