# CET 儿童气泡：ASR 文案 + 本地录音回放

- **日期：** 2026-09-10
- **仓库：** `kidora-ai`（`cet-tutor-core` / `cet-tutor-server` / `kidora-web`）
- **状态：** Accepted
- **关联：** [docs/ui-design.md](../../ui-design.md) §5–§6；现有 SSE：`message.delta` / `audio.tts` / `pronunciation` / `plan.updated`

## 1. 背景与目标

CET 陪练页（`/cet/session/[id]`）语音发送后，儿童气泡写死为「（语音）」。ASR 仅在服务端 `resolveChildText` 入模，不回传前端；本地 WAV `base64` 发完即丢，无法回放。

**目标：**

1. 儿童语音气泡展示 **ASR 识别文案**。
2. 同一气泡可 **回放本次本地录音**（与外教「播放」对称）。
3. 不落库、不上传云存储儿童音频；合规与实现都走轻量路径。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 产品形态 | A：ASR 文字 + 本地录音回放 |
| 实现路径 | 方案 1：前端保留录音 + SSE `asr.transcript` |
| ASR 失败 | 保留「（语音）」+ 仍可播放；沿用现有错误提示 |
| Soft redirect / Safety | 若已识别出文本，仍发 `asr.transcript`，再走现有安全文案/错误流 |
| 打字轮 | 行为不变：仅文字，无播放按钮 |

### 1.2 非目标

- 儿童录音落库 / 云端 URL / 会话重进后回放历史录音
- 儿童主界面展示发音四维分（`pronunciation` 仍仅家长/调试）
- 改 MCP ASR 工具协议
- 实时流式 ASR（仍是「录完 → 整段识别」）

## 2. SSE 协议

### 2.1 新增事件

| event | data | 时机 |
|---|---|---|
| `asr.transcript` | JSON，见下 | 语音轮、ASR 成功后、Tutor `message.delta` **之前** |

```json
{
  "text": "I have a cat",
  "locale": "en-US",
  "provider": "tencent"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `text` | 是 | ASR 原文；前端直接展示 |
| `locale` | 否 | 请求 locale，缺省可省略 |
| `provider` | 否 | 厂商 id，便于调试；儿童 UI 不展示 |

### 2.2 发送规则

- **仅**请求体含 `audioBase64` 且 ASR 成功时发送。
- 纯文本轮不发。
- ASR 失败：抛现有 `BAD_REQUEST`（「ASR 未能识别语音」）等，**不**发空 `asr.transcript`。
- Soft input redirect：已有 `childText` 时**必须**先发 `asr.transcript`，再发安全侧文案（`message.delta` 或现有 soft 流）。
- Hard safety block：ASR 已成功则**必须**先发 `asr.transcript`，再发 `error` / `safety.block`（保证孩子仍能看到自己说了什么）。

### 2.3 事件顺序（语音轮典型）

```
asr.transcript → message.delta* → audio.tts? → pronunciation? → plan.updated? → done
```

## 3. 后端改动

| 位置 | 改动 |
|---|---|
| `CetStreamEvent.Type` | 新增 `ASR` |
| `CetStreamEvent.asr(String json)` | 工厂方法 |
| `CetLessonService.streamTurn` | `resolveChildText` 成功且来自音频时，`Flux` 前缀 `asr.transcript` |
| `CetSessionController.toSse` | `ASR` → `asr.transcript` |
| 单测 | 映射名；语音轮有事件；文本轮无事件；payload 含 `text` |

`resolveChildText` 可返回 `(text, AsrMeta?)` 或在语音路径旁路记下 `AsrResult`，避免二次 ASR。**禁止**为展示再调一次 MCP ASR。

## 4. 前端改动（`kidora-web`）

### 4.1 气泡模型

```ts
type Bubble = {
  id: string;
  role: "child" | "tutor" | "system";
  text: string;
  audioBase64?: string;  // 儿童：本地录音；外教：TTS
  mimeType?: string;
  needsTap?: boolean;    // 仅外教自动播放被拦
};
```

### 4.2 行为

1. `toggleRecord` 停止后：`streamTurn` 时儿童气泡初始 `text: "（语音）"`，并挂上 `audioBase64` / `mimeType: "audio/wav"`。
2. `postSse` 增加 `onAsr`；收到后 `setBubbles` 更新对应儿童气泡 `text`。
3. 儿童气泡有 `audioBase64` 时渲染「播放」；点击走与外教相同的 `HTMLAudioElement` 路径，并调用现有 `stopTutorVoice()`（或统一 `stopSharedAudio()`）避免抢播。
4. ASR 未到或失败：保持「（语音）」；有本地音频则仍可播放。

### 4.3 UI 约束

- 儿童模式不展示 `provider` / 原始 JSON。
- 气泡布局与现有外教「播放」对齐（小号按钮，不新增卡片堆）。
- 不在首屏塞波形图或长进度条。

## 5. 文档同步（实现同一交付）

| 文档 | 更新点 |
|---|---|
| `docs/ui-design.md` §5 / §6 / §10 | 儿童气泡：ASR 文案 + 本地回放；SSE 消费 `asr.transcript`；去掉「仅显示（语音）」 |
| `docs/cet-tutor-design.md` 或 `docs/cet-lesson-flow.md` | 仅当文中已有 SSE 事件枚举时增补一行 `asr.transcript`；无枚举表则不动 |

## 6. 验收标准

1. 录音发送后气泡立即有「（语音）」+「播放」，点播放能听到自己刚说的话。
2. ASR 成功后同一气泡文案变为识别英文（或厂商返回文本），播放仍可用。
3. ASR 失败：气泡仍可播放；页面有错误提示；无空 transcript 事件。
4. 打字发送：无「播放」、无 `asr.transcript`。
5. 外教 TTS 与儿童回放互斥：后点停止先播。
6. 单测覆盖 SSE 映射与「仅语音轮发 ASR」。

## 7. 实现范围清单

| # | 模块 | 工作 |
|---|---|---|
| T1 | `cet-tutor-core` | `CetStreamEvent.ASR` + `streamTurn` 前缀事件 + 单测 |
| T2 | `cet-tutor-server` | `toSse` 映射 + Controller 单测 |
| T3 | `kidora-web` | `api.ts` 消费事件；session 页气泡展示/回放 |
| T4 | docs | `ui-design.md` + 必要 SSE 文档 |

预估：单人小改，约 0.5–1 日。
