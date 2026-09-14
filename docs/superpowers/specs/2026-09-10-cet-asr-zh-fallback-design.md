# CET ASR 中文回退（en→zh）设计

- **日期：** 2026-09-10
- **仓库：** `kidora-ai`
- **状态：** Implemented（2026-09-10）
- **关联：** `TencentSpeechProvider` / `IFlytekSpeechProvider`；既有中文脚手架 `2026-09-10-cet-tutor-zh-scaffold-tts-design.md`

## 1. 背景与目标

实测：孩子清晰说中文「我的狗是黑色的，用英文怎么说」，气泡却显示 `The.`，Tutor 按垃圾转写夸赞并追问颜色。

根因：

1. Web 语音轮固定 `locale=en-US`。
2. 腾讯极速 ASR 写死 `engine_type=16k_en`；讯飞 IAT 写死 `language=en_us`；`locale` 只回写响应元数据，不进厂商请求。
3. Tutor Prompt 已支持「中文求助 → 中文脚手架 + 英文示范」，但从未收到正确中文转写。

**目标：** 在默认英文练习路径下，静默识别中文求助/中文脚手架触发句，使既有 Tutor 规则生效。

## 2. 决策

| 项 | 选择 |
|---|---|
| 方案 | A：主引擎按 locale；弱转写则静默二次另一语种；取更合理结果 |
| UX | **静默**（不弹「听成中文了」） |
| 主引擎 | `en*` / 缺省 → 英文；`zh*` → 中文 |
| 弱转写启发式 | 去标点后极短，或单功能词（the/a/an/um…） |
| 选型偏好 | 含汉字且另一侧弱/明显更短 → 取中文 |
| 发音评测 SOE | **不改**（仍英文评测引擎） |
| CET / Web | **不改** locale 默认（仍 en-US）；修复下沉到 MCP 厂商适配 |

### 非目标

- 不引入实时流式 ASR
- 不改 SSE `asr.transcript` 协议字段
- 不改 Tutor Prompt（脚手架已够）
- 不做置信度真实透传（厂商仍可硬编码 confidence）

## 3. 行为

```
audio + locale
  → primaryEngine(locale)  # 默认 16k_en / en_us
  → text1
  → if !weak(text1): return text1
  → secondaryEngine        # 另一语种
  → text2
  → return pickBetter(text1, text2)
```

弱转写示例：`The.` / `A` / `Um` / 空 / 纯标点。  
应回退成功示例：中文「我的狗是黑色的，用英文怎么说」。

## 4. 改动面

| 模块 | 改动 |
|---|---|
| `kidora-mcp-server` | 共享 `AsrTranscriptQuality`；腾讯 `buildFlashQuery` 接受 `engine_type`；讯飞 IAT `language` 按 locale；`transcribe` 弱结果二次调用 |
| 单测 | engine 映射、weak/pickBetter、query 含 `16k_zh` |
| `docs/mcp-design.md` | 记录 locale→引擎 + 弱转写回退 |

## 5. 验收

1. 清晰中文「……用英文怎么说」→ ASR 文本含汉字求助句，非 `The.`。
2. 清晰英文练习句（如 `black` / `My dog is black`）→ 仍走英文引擎，不因回退改坏。
3. 弱英文且中文也空/失败 → 保持原弱结果或现有错误语义（不额外抛新错）。
4. SOE 发音评测行为不变。
