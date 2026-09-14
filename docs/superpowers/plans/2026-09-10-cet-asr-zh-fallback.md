# CET ASR 中文回退 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 弱英文 ASR 结果静默回退中文引擎，使中文求助句能进入既有 Tutor 中文脚手架。

**Architecture:** 在 `kidora-mcp-server` 增加共享转写质量启发式；腾讯/讯飞 `transcribe` 按 locale 选主引擎，弱结果再打另一语种并 `pickBetter`。CET/Web 不改。

**Tech Stack:** Java 17、JUnit 5、腾讯 Flash ASR、讯飞 IAT WebSocket

## Global Constraints

- groupId `com.wuji.kidora.ai`；Javadoc `@author liudy`
- 不改 SOE 引擎；不改 SSE 协议；静默回退
- 版本对齐 AGENTS.md 锁定表

---

## File map

| File | Responsibility |
|---|---|
| `.../speech/AsrTranscriptQuality.java` | locale→引擎、weak、pickBetter |
| `.../speech/TencentSpeechProvider.java` | `engine_type` 入 query + 回退 |
| `.../speech/IFlytekSpeechProvider.java` | IAT `language` + 回退 |
| `.../speech/AsrTranscriptQualityTest.java` | 启发式单测 |
| `.../speech/TencentSpeechProviderTest.java` | query 引擎参数 |
| `docs/mcp-design.md` | 文档同步 |

---

### Task 1: `AsrTranscriptQuality` + 单测

**Files:**
- Create: `kidora-mcp-server/src/main/java/com/wuji/kidora/ai/mcp/speech/AsrTranscriptQuality.java`
- Create: `kidora-mcp-server/src/test/java/com/wuji/kidora/ai/mcp/speech/AsrTranscriptQualityTest.java`

- [ ] **Step 1: 写失败单测**

覆盖：`The.` / `a` 为 weak；`My dog is black` 非 weak；含汉字非 weak；`pickBetter("The.","我的狗是黑色的")` 取中文；`tencentEngineType("en-US")`→`16k_en`；`zh-CN`→`16k_zh`；`iFlytekLanguage` 同理。

- [ ] **Step 2: 实现 `AsrTranscriptQuality`**
- [ ] **Step 3: 跑测通过** `mvn -pl kidora-mcp-server -Dtest=AsrTranscriptQualityTest test`（该工程独立 pom，在模块目录执行）

---

### Task 2: 腾讯 Flash ASR locale + 回退

**Files:**
- Modify: `TencentSpeechProvider.java`
- Modify: `TencentSpeechProviderTest.java`

- [ ] **Step 1: 单测** `buildFlashQuery(..., "16k_zh")` 含 `engine_type=16k_zh`；默认/en 仍 `16k_en`
- [ ] **Step 2: `buildFlashQuery` 增加 `engineType` 参数**
- [ ] **Step 3: `transcribe` 主引擎 + 弱则二次 + pickBetter；`mapAsr` locale 用实际采用侧**
- [ ] **Step 4: 跑 `TencentSpeechProviderTest`**

---

### Task 3: 讯飞 IAT locale + 回退

**Files:**
- Modify: `IFlytekSpeechProvider.java`
- Create/Modify: `IFlytekSpeechProviderTest.java`（若无可只测 package-visible 映射经 `AsrTranscriptQuality`）

- [ ] **Step 1: `iatRecognize` 增加 `language` 参数**
- [ ] **Step 2: `transcribe` 同腾讯回退策略**
- [ ] **Step 3: 编译/相关单测**

---

### Task 4: 文档

- [ ] 更新 `docs/mcp-design.md`：locale→引擎、弱转写静默二次识别
- [ ] 验收清单写入 progress 可选；本计划以 mcp-design 为准
