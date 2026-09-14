# 前台个人中心（家长资料 + 儿童档案）

- **日期：** 2026-09-14
- **仓库：** `kidora-ai`
- **状态：** Implemented
- **关联：** [ui-design.md](../../ui-design.md) `/settings`、[2026-09-14-cet-web-register-learner-design.md](./2026-09-14-cet-web-register-learner-design.md)

## 1. 目标

首页可进个人中心，完成：

1. 修改家长展示昵称；修改密码（须验证当前密码）；用户名只读。
2. 儿童档案：列表、添加、编辑昵称/英语水平、软删除（停用）。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 家长资料 | 昵称 + 改密（当前密码校验）；用户名只读 |
| 儿童 | 列表 + 添加 + 编辑昵称/水平 + 软删除 |
| 入口 | `/home` 顶栏「个人中心」→ `/settings`（与 ui-design IA 一致） |
| 水平 UI | 同注册：启蒙/初级/中级/进阶 → CEFR |
| 软删除 | `learner_profile.deleted = TRUE`；列表与开课仅 ACTIVE 且未删 |
| 末孩保护 | 不可软删除名下最后一名 ACTIVE 儿童 |
| JWT | 改昵称成功后重发 JWT，前端 `setToken` |

### 1.2 非目标

- 手机号/邮箱/验证码
- 改用户名、恢复已删儿童 UI、生理年龄/`age_band`
- 儿童独立账号、上传头像

## 2. 方案对比（择优）

| 方案 | 说明 | 取舍 |
|---|---|---|
| **A. 独立 `/settings` 页** | 顶栏入口；分区：家长 / 儿童 | **采用**：与现有 IA 一致，表单空间足够 |
| B. 首页第三卡片 | 与 CET/助手并列 | 挤占产品入口，资料表单不适合作入口卡 |
| C. 顶栏抽屉/弹层 | 无独立路由 | 多孩编辑与改密拥挤，难分享/刷新 |

## 3. API

### 3.1 家长

- `GET /api/auth/me` → `{ userId, username, nickname, role }`
- `PATCH /api/auth/me` body `{ nickname }` → 同 login 形（含新 `token`）
- `POST /api/auth/password` body `{ currentPassword, newPassword }` → `{ ok: true }`

校验：昵称 1–32；新密码 ≥6；当前密码错误 → `UNAUTHORIZED`「当前密码不正确」。

### 3.2 儿童

- `GET /api/learners`：既有；增返回 `englishLevel`（由 CEFR 反查）
- `POST /api/learners` `{ displayName, englishLevel }` → 新建档案（默认人设 `emma`）
- `PATCH /api/learners/{learnerId}` `{ displayName?, englishLevel? }`
- `DELETE /api/learners/{learnerId}`：软删除；最后一名 ACTIVE → `BAD_REQUEST`

归属一律按 JWT `userId`，禁止信任请求体 `userId`。

## 4. 前端

- `/home` 顶栏：`个人中心` | `退出`
- `/settings`：家长资料区 + 儿童列表（行内编辑 / 添加表单 / 删除确认）
- 改昵称后刷新 token；改密成功可提示，不强制重登

## 5. 测试

- `EnglishLevelMapper` 双向映射
- `AuthService`：me / 改昵称 / 改密（含错密）
- `LearnerService`：创建、更新、软删除、末孩保护
- 前端：settings 页可手工验；必要时补 api 类型

## 6. 文档同步

同交付更新 `docs/ui-design.md`、`docs/architecture.md`、`agents.md` / `AGENTS.md` 相关 API 行。
