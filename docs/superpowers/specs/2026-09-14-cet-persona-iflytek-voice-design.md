# CET 人设 ↔ 讯飞 TTS 音色映射设计

- **日期：** 2026-09-14
- **仓库：** `kidora-ai`（Flyway seed + 文档权威）；`kidora-ai-manage`（人设音色页双厂商 UX）
- **状态：** Accepted
- **音色对照：** [讯飞开放平台 TTS 控制台](https://console.xfyun.cn/services/tts)
- **前置：** [2026-09-10-cet-persona-tencent-voice-design.md](2026-09-10-cet-persona-tencent-voice-design.md)（表结构与运行时解析已落地）

## 1. 背景与目标

`cet_persona_voice` 已支持 `vendor_code`，但 seed 仅腾讯。`speech_route.primary` 默认为 `iflytek` 时，无 ACTIVE 映射 → `voice=null` → MCP 回落 `x4_xiaoyan`，六人设同音色。

**目标：**

1. 为 6 人设 seed `vendor_code=iflytek` 的 ACTIVE 映射（`voice_id` = 讯飞 `vcn`）。
2. 管理台人设音色页文案/筛选/对照链接/编辑占位支持腾讯 + 讯飞；讯飞编辑提供常用 `vcn` 快捷选项。
3. **不改** MCP 协议、表结构、运行时解析逻辑（已按 primary vendor 查表）。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 范围 | 本仓 Flyway seed + 文档 + manage UI 双厂商友好（方案 C） |
| Seed 映射 | emma→`x4_xiaoyan`；mike→`aisjiuxu`；lily→`x4_yezi`；tom→`aisjiuxu`；coco→`aisbabyxu`；alex→`aisjinger` |
| locale | 与腾讯一致：`en-US` |
| catalog_url | `https://console.xfyun.cn/services/tts` |
| Admin TTS 试听 | **不做**新 API；真机试听走陪练或语音供应商 probe |

### 1.2 非目标

- 改表结构 / UNIQUE 约束
- 改 MCP `tts_synthesize` 协议
- Azure 人设 seed
- 儿童端换音色 / 自动 failover
- 特色页 `business.total` / `vcn.x4_xiaoyan` 等权益包名不当 `voice_id`

## 2. 数据（kidora-ai）

### 2.1 Flyway

`kidora-agent-server/.../V25__cet_persona_voice_iflytek.sql`：

- `INSERT` 6 行 `vendor_code='iflytek'`，固定 id `40011`–`40016`
- `ON CONFLICT (persona_id, vendor_code) DO NOTHING`
- 可选：`COMMENT ON COLUMN cet_persona_voice.voice_id` 注明「腾讯 VoiceType / 讯飞 vcn」

权威 DDL 仍为 `schema/23_cet_persona_voice.sql`（无结构变更）；注释可同步。

### 2.2 Seed 表

| id | persona_id | voice_id | voice_name | voice_traits |
|---|---|---|---|---|
| 40011 | emma | x4_xiaoyan | 讯飞小燕 | 普通话女声；清晰温和，贴合温柔鼓励型外教 |
| 40012 | mike | aisjiuxu | 讯飞许久 | 普通话男声；沉稳，贴合探险任务型（基础发音人唯一男声） |
| 40013 | lily | x4_yezi | 讯飞小露 | 普通话女声；柔和，贴合故事型 |
| 40014 | tom | aisjiuxu | 讯飞许久 | 普通话男声；与 mike 暂共用 vcn（开通更多男声后可管理台改） |
| 40015 | coco | aisbabyxu | 讯飞许小宝 | 普通话童声；活泼亲切，贴合萌宠/低龄 |
| 40016 | alex | aisjinger | 讯飞小婧 | 普通话女声；年轻感；无少年男声时作游戏型替代 |

## 3. 运行时

无代码变更。既有链路：

```
session.persona_id + speech_route.primary_vendor
  → CetPersonaVoiceRepository.findActiveVoiceId
  → MCP tts_synthesize.voice → IFlytekSpeechProvider vcn
```

primary=`iflytek` 且 seed 生效后，各人设传上表 `voice_id`；无行仍回落 `x4_xiaoyan`。

## 4. 管理台（kidora-ai-manage）

既有 `GET/PUT /api/admin/cet/persona-voices` **不变**（seed 后列表自然出现 iflytek 行）。

UI（`CetPersonaVoicesView.vue` + Dashboard 文案）：

1. 默认筛选改为「全部」（空），便于一次看到双厂商。
2. 页头对照链接按当前筛选/首行厂商切换：腾讯文档 ↔ 讯飞控制台。
3. 列名 `VoiceType` → `voice_id`；编辑标签/占位按 `vendorCode` 区分（腾讯 VoiceType / 讯飞 vcn）。
4. 编辑讯飞行时提供常用 vcn 快捷下拉（写入 `voiceId`，仍可手改）。
5. Dashboard「人设音色」描述改为「CET 人设 ↔ 腾讯/讯飞 TTS」。
6. 同步 manage `docs/admin-design.md` / `docs/ui-design.md`。

## 5. 文档（kidora-ai）

- `docs/cet-persona-design.md`：新增 §5.2 讯飞 seed 表
- `docs/database-design.md`：seed 说明含 iflytek × 6；Flyway 列 `V23`
- `docs/admin-design.md`：提及 V23 / 双厂商
- `docs/mcp-design.md`：人设音色句补充讯飞 seed（若有「仅腾讯」措辞）

## 6. 验收

1. Flyway 应用后：`SELECT count(*) FROM cet_persona_voice WHERE vendor_code='iflytek' AND status='ACTIVE'` = 6。
2. primary=iflytek 开课时，TTS 请求 `voice`/`vcn` 为人设对应值（非一律默认）。
3. 管理台筛选 iflytek 可见 6 行；编辑保存后重启 `cet-tutor-server` 生效。
4. mike/tom 均为 `aisjiuxu`（已知限制，可管理台改）。
