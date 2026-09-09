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
- `cet-tutor-server` `:8082`（CET）

演示账号：`parent1` / `parent123`。

## 代理

| 前缀 | 目标 |
|---|---|
| `/api/auth/*` `/api/chat/*` `/api/learners` | `BACKEND_URL`（8080） |
| `/api/cet/*` | `CET_BACKEND_URL`（8082） |

JWT 存在 `localStorage`（`kidora_token`），请求头 `Authorization: Bearer`。
