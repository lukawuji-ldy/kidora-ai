# CET 人设 ↔ 腾讯 TTS 音色映射设计

- **日期：** 2026-09-10
- **仓库：** `kidora-ai`（schema / 运行时权威）；`kidora-ai-manage`（Admin API + UI，路径 `E:\java-workspace\ai_workspace_1\kidora-ai-manage`）
- **状态：** Draft（待用户审阅规格后 Accepted）
- **音色官方对照：** [腾讯云语音合成音色列表](https://cloud.tencent.com/document/product/1073/92668)

## 1. 背景与目标

CET 陪练人设（`emma` / `mike` / `lily` / `tom` / `coco` / `alex`）目前只进入 Planner/Tutor Prompt 与 `cet_lesson_session.persona_id`，**不驱动 TTS**。腾讯侧 `TencentSpeechProvider` 在 `voice` 为空时固定 `VoiceType=101001`；CET `buildSpeechExtras` 调用 `port.tts(text, null, locale)`，导致所有人设同一音色。

**目标：**

1. 按人设选用腾讯 TTS `VoiceType`（及名称、特点描述）。
2. 映射入库，管理台可切换；行内维护官方音色列表 URL，方便查改。
3. 运行时经现有 MCP `tts_synthesize.voice` 传参，**cet-\* 不直连云厂商**。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 范围 | 本仓表+seed+运行时 + manage CRUD/UI（方案 A） |
| 存储 | 独立表 `cet_persona_voice`（非塞进 `extra_json`） |
| Seed | 下表 6 人设 × `tencent` |
| 多厂商 | 表预留 `vendor_code`；本期只 seed 腾讯 |
| 热更新 | 改库后重启 `cet-tutor-server`（与现有 speech 约定一致） |

### 1.2 非目标

- 本期不做讯飞/Azure 人设音色 seed（结构可扩展）
- 不做儿童端「试听换音色」产品页
- 不改 MCP 工具协议（已支持可选 `voice`）
- 不自动 failover；仍读 `speech_route.primary`

## 2. 数据模型

### 2.1 表 `cet_persona_voice`

权威 DDL：`schema/23_cet_persona_voice.sql`  
Flyway：`kidora-agent-server/.../V11__cet_persona_voice.sql`（含 COMMENT + seed）  
manage **Flyway 关闭**，消费同一库。

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 雪花/固定 seed id |
| `persona_id` | VARCHAR(64) NOT NULL | `emma`…`alex` |
| `vendor_code` | VARCHAR(32) NOT NULL | 本期 `tencent` |
| `voice_id` | VARCHAR(64) NOT NULL | 腾讯 VoiceType，如 `501009` |
| `voice_name` | VARCHAR(128) NOT NULL | 展示名，如 WeWinny |
| `voice_traits` | TEXT NOT NULL | 中文特点（运营可读） |
| `catalog_url` | VARCHAR(512) NOT NULL | 官方对照表 URL |
| `locale` | VARCHAR(16) NOT NULL DEFAULT `en-US` | 建议朗读 locale |
| `status` | VARCHAR(20) NOT NULL DEFAULT `ACTIVE` | `ACTIVE` \| `DISABLED` |
| `create_time` / `update_time` | TIMESTAMPTZ | |

**约束：** `UNIQUE (persona_id, vendor_code)`  
**索引：** `(vendor_code, status)`（列表/解析）

全部字段/表须 `COMMENT ON`（中文）。

### 2.2 Seed（腾讯）

`catalog_url` 统一：`https://cloud.tencent.com/document/product/1073/92668`

| persona_id | voice_id | voice_name | voice_traits |
|---|---|---|---|
| emma | 501009 | WeWinny | 外语女声；英文清晰温和，贴合温柔鼓励型外教 |
| mike | 501008 | WeJames | 外语男声；英文沉稳有力，贴合探险任务型 |
| lily | 603004 | 温柔小柠 | 中英聊天女声；柔和、偏叙事，贴合故事型 |
| tom | 101050 | WeJack | 精品英文男声；干脆有力，贴合挑战型 |
| coco | 502007 | 智小虎 | 中英聊天童声；活泼亲切，贴合萌宠/低龄 |
| alex | 603000 | 懂事少年 | 中英特色男声；少年感，贴合游戏关卡型 |

来源：[腾讯音色列表](https://cloud.tencent.com/document/product/1073/92668)。运营可在管理台改 `voice_id` 等字段。

## 3. 运行时（kidora-ai）

### 3.1 解析

```
session.persona_id + speech_route.primary.vendor_code
  → CetPersonaVoiceRepository.findActive(personaId, vendorCode)
  → Optional<voice_id>（无 ACTIVE 行 → empty）
  → SpeechToolPort.tts(speakText, voiceOrNull, locale)
  → McpSpeechToolAdapter → tts_synthesize.voice
  → TencentSpeechProvider VoiceType
```

- Repository 放在 **cet-tutor-core** 的 `repo` 包（与现有 CET JDBC repo 一致）；`cet-tutor-server` 仅装配。
- `speech_route.primary`：CET 用只读 JDBC 查 `speech_route` 单行取 `primary_vendor`（或 server 启动时 Bean 注入当前 primary）；解析失败时视为无映射。**禁止** mcp-server 依赖 CET 人设表。
- primary 下无 ACTIVE 映射行：`voice=null`，行为与今日一致（腾讯默认 `101001`、讯飞 `x4_xiaoyan` 等）。
- 继续使用既有 `speakableForTts`（括号中文不进 TTS）。

### 3.2 调用点

- `CetLessonService` 开场 / 轮次：`buildSpeechExtras(..., personaId)` 内解析 voice 后 `port.tts(speakText, voice, locale)`。
- 单测：映射命中传正确 voice；无映射传 null；`speakableForTts` 回归保留。

### 3.3 mcp-server

无协议变更。可选：将腾讯缺省 `101001` 保留为最后兜底（当 voice 仍空时）。

## 4. 管理台（kidora-ai-manage）

对齐现有 `admin/speech` 风格（`AdminSpeechVendor*` + `SpeechVendorsView.vue`）。

### 4.1 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/cet/persona-voices` | 列表（可 `?vendorCode=tencent`） |
| PUT | `/api/admin/cet/persona-voices/{id}` | 更新 `voice_id` / `voice_name` / `voice_traits` / `catalog_url` / `locale` / `status` |

- Admin JWT；写操作记 `admin_audit_log`。
- 不提供随意插入新 persona（本期固定 6 人设；后续若加人设再开 POST）。
- 校验：`voice_id` 非空；`status` ∈ ACTIVE/DISABLED；`vendor_code` 不可改（或忽略请求体中的变更）。

### 4.2 UI

- 路由建议：`/cet/persona-voices`；侧栏「人设音色」（紧挨「语音供应商」）。
- 页头外链：官方音色列表（用任意行 `catalog_url` 或常量同 URL）。
- 表格列：人设、厂商、VoiceType、名称、特点（截断）、locale、状态、操作。
- 编辑弹窗：改 VoiceType / 名称 / 特点 / catalog_url / locale / 状态；保存调 PUT。
- 文案提示：修改后需重启 `cet-tutor-server` 方可对陪练生效（与供应商页语义一致）。

### 4.3 包建议

```
com.wuji.kidora.ai.manage.admin.cet.persona
  AdminCetPersonaVoiceController
  AdminCetPersonaVoiceService
  AdminCetPersonaVoiceRepository
  AdminCetPersonaVoiceView / UpdateRequest
```

前端：`api/cetPersonaVoices.ts` + `views/CetPersonaVoicesView.vue`。

## 5. 文档同步

| 文档 | 更新点 |
|---|---|
| `kidora-ai/docs/cet-persona-design.md` | 人设↔音色映射表 + 官方 URL |
| `kidora-ai/docs/database-design.md` | 新表索引 |
| `kidora-ai/docs/mcp-design.md` | TTS voice 来自人设映射 |
| `kidora-ai/docs/admin-design.md` | 互链 manage 新人设音色能力 |
| `kidora-ai-manage/docs/admin-design.md` | `/api/admin/cet/persona-voices` |

## 6. 验收

1. Flyway/手工执行 V11 后，库中 6 行 tencent seed，`catalog_url` 正确。
2. `speech_route.primary=tencent` 时，emma 会话 TTS MCP 参数含 `voice=501009`。
3. 管理台可改 mike 的 `voice_id`；重启 cet-tutor-server 后新会话用新 ID。
4. 无映射 / primary=iflytek：不传 voice 或走厂商默认，陪练不崩。
5. 单测覆盖 resolve + buildSpeechExtras voice 传参。

## 7. 实现分期（建议）

| 切片 | 仓 | 内容 |
|---|---|---|
| T1 | kidora-ai | DDL + Flyway seed + Repository + CetLessonService 传 voice + 单测 + docs |
| T2 | kidora-ai-manage | Admin API + 页面 + 侧栏 + admin-design |

可同一次交付说明两仓互链。
