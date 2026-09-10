# MCP Server 设计规范

独立 `kidora-mcp-server` 进程、工具规范、与 CET 语音能力分期。  
Agent 侧接入见 [agent-flow.md](agent-flow.md)；运营绑定见旁路管理台。

---

## 1. 目标与边界

- MCP Server **独立部署**，与 `kidora-agent-server` / `cet-tutor-server` 无强耦合。
- 本模块仅依赖 `kidora-common`；禁止依赖 `kidora-agent-core` / `cet-tutor-core`。
- 管理台只做 Server 注册、工具绑定、探活；不运行 MCP Client 业务循环。

---

## 2. 部署拓扑

```
cet-tutor-server / kidora-agent-server
        │  MCP Client (SSE + optional Bearer)
        ▼
kidora-mcp-server (:8081)
        │
        ▼
 外部 ASR / TTS / 发音 / 词典 / 安全服务
```

可多实例；工具名全局唯一，冲突 fail-fast。

---

## 3. Tool 规范

- 名称：`snake_case`，稳定、可版本化。
- 输入输出：JSON Schema；错误返回结构化 code。
- 儿童相关 Tool 须声明是否可能输出音频/外链，供 Safety 策略引用。

### 3.1 CET 相关工具清单（分期）

| Tool | 职责 | MVP |
|---|---|---|
| `echo_ping` | 连通性 | 1（样例） |
| `asr_transcribe` | 语音转文本 | 2 |
| `tts_synthesize` | 文本转语音 | 2 |
| `pronunciation_score` | 发音评测 | 2 |
| `dictionary_lookup` | 词典/例句 | 3（已落地 stub\|http） |
| `child_safety_check` | 补充安全检测（可选与内置 Safety 并用） | 1～2 |
| `image_scene_query` | 场景图（趣味） | 5 |

---

## 4. Agent 侧接入

- Client：`spring-ai-starter-mcp-client-webflux`。
- 连接权威：库表 `mcp_server_ref`（ACTIVE）；yml 仅空库兜底。
- 工具子集：`mcp_tool_binding`（bound/enabled）。
- CET 与通用 Chat 可绑定不同工具子集。

---

## 5. 供应商选型原则（不定死合同）

1. 儿童合规与数据驻留要求可满足。
2. 延迟可支撑口语体验（目标：关键路径尽量 &lt; 1s 级体感，具体基准实现期测）。
3. 可封装为 MCP，便于替换供应商。
4. 优先：可自建开源、或云厂商免费额度；浏览器 API 仅限原型。

### 5.1 MVP-2 供应商路由（可管理台切换）

| 角色 | 供应商 | 说明 |
|---|---|---|
| primary（默认） | 讯飞整栈（听写/合成/ISE） | `speech_route.primary_vendor` |
| backup | 腾讯整栈（ASR/TTS/智聆 SOE） | 仅配置，**不**自动 failover |
| tertiary | Azure Speech | 代码保留，暂不启用 |

本地/CI：`kidora.speech.mode=stub`。生产联调：`mode=db` + 管理台写入凭证密文（`SecretCipher` / `KIDORA_API_KEY_SECRET`）。

腾讯 TTS（`TextToVoice`）使用 API 3.0 **TC3-HMAC-SHA256**。讯飞整栈走控制台**流式 WebSocket**（旧版 HTTP `api.xfyun.cn/v1/service/v1/*` 已弃用）：ASR `wss://iat-api.xfyun.cn/v2/iat`、TTS `wss://tts-api.xfyun.cn/v2/tts`、ISE `wss://ise-api.xfyun.cn/v2/open-ise`；握手鉴权 HMAC-SHA256（`apiKey`+`apiSecret`，与管理台 `IflytekWsAuth` 一致）；凭证 JSON 须含 `appId`+`apiKey`+`apiSecret`；WAV 去 44 字节头后按 pcm raw 分片；**mp3 自动 `encoding/aue=lame`**；**拒收 m4a**；TTS 默认发音人 `x4_xiaoyan`；TTS 每帧 `data.audio` **独立 base64 逐帧 decode 再拼字节**（不可拼接 base64 字符串）；失败透出厂商 `code`/`message`。JDK `HttpClient` WebSocket 文本帧须按 `last` 拼完整消息再 `readTree`（TTS/ISE 大段 base64 常在 ~4KB 处拆帧，否则 `JsonEOFException`）。腾讯 ASR 极速版：query 含 `secretid`/`timestamp`，`voice_format` 为字符串（`wav`/`mp3`/`m4a`/`pcm`），Authorization 为 `POST`+路径+排序 query 的 HMAC-SHA1。腾讯 SOE：`voice_format` 为 0/1/2（pcm/wav/mp3）；签名原文为**未编码**字典序 query，请求 URL 再对 value/`signature` 做百分号编码（空格 `%20`）；WebSocket **须等服务端 code=0 握手包后再发 binary**，音频发完再发文本帧 `{"type":"end"}`，最后等 `final=1`（`onOpen` 立即发音频会被丢弃并触发 4008「超过15秒未发送音频数据」）；并解析 `result` 文本型分数。凭证 JSON：`{"secretId","secretKey","appId"}`，其中 `appId` 须为 [API 密钥管理](https://console.cloud.tencent.com/cam/capi) 页的 **AppId**（通常 10 位如 `1255…`），**禁止**填账号 ID/Uin（常见 `1000…` 开头）；TTS 仅用 SecretId/Key，ASR/SOE 路径含 AppId。

Tool JSON 字段名稳定；响应 `provider` 为实际供应商码（`iflytek`/`tencent`/`stub`）。

对比见 [findings.md](../findings.md)。

### 5.2 MVP-2 实现状态

| 切片 | 状态 | 说明 |
|---|---|---|
| **MVP-2A** | complete | Server 骨架 + stub + `mcp_*` |
| **MVP-2B1** | complete | Azure REST + CET MCP Client + stream SSE |
| **MVP-2C** | complete | 讯飞/腾讯整栈 + `speech_*` 表 + 管理台主备（无自动切换）；Azure 第三档 |
| **MVP-2B2** | complete | `kidora-web` 录音/播放消费 SSE |
| **词典** | complete | `dictionary_lookup`（`kidora.dictionary.mode=stub\|http`） |

CET 侧：`kidora.mcp.enabled=true` 时经 MCP 调工具；**禁止** cet-* 直连云厂商。

---

## 6. 安全

- MCP 端点鉴权（Bearer）；内网优先。
- Tool 不得回传密钥；ASR 音频临时对象有 TTL。
- 日志不存完整音频 URL 长期明文（或加密）；禁止打印 API Key / 完整 audioBase64。

---

## 7. Registry / Lifecycle / 韧性

- Registry：启动加载 ACTIVE 绑定；变更后进程重启或后续热更新。
- 超时、重试、熔断：分期（对齐参考仓 roadmap）。
- 版本：Tool schema 变更需兼容或显式升 major。
- CET Client：`spring.ai.mcp.client.initialized=false`，避免 MCP Server 未起拖垮进程。

---

## 8. 可观测

- span：`mcp.tool.invoke`；指标：成功/失败/延迟。
- UI 可解释面板：工具名与摘要（儿童端简化展示）。

---

## 9. Roadmap

| 阶段 | 内容 |
|---|---|
| MVP-1 | （文档）Server 骨架 + `echo_ping` |
| **MVP-2A** | Server 落地 + `echo_ping` + ASR/TTS/发音 **stub** + `mcp_*` 表 |
| **MVP-2B1** | Azure REST（可 stub 回退）+ CET MCP Client + stream SSE 契约 |
| **MVP-2B2** | Web 录音/播放闭环 |
| MVP-3+ | 韧性、热更新；Tutor 工具环查词典（可选） |
| MVP-5 | 场景图等扩展 |

---

## 10. 模块结构（MVP-2B1）

```
kidora-mcp-server/
  pom.xml
  src/main/java/com/wuji/kidora/ai/mcp/
    KidoraMcpServerApplication.java
    auth/          # Bearer / X-API-Key（KIDORA_MCP_API_KEY）
    config/        # MethodToolCallbackProvider + SpeechConfiguration
    speech/        # SpeechProvider：StubSpeechProvider | AzureSpeechProvider（REST）
    tools/         # ConnectivityTools + SpeechTools（委托 Provider）

cet-tutor-server/…/mcp/
  McpClientTransportConfiguration  # ACTIVE mcp_server_ref → NamedClientMcpTransport
  McpSpeechToolAdapter             # SpeechToolPort → MCP tools
```

默认端口 **8081**；鉴权 local 关闭（`KIDORA_MCP_AUTH_ENABLED=false`）。
语音默认 `KIDORA_SPEECH_PROVIDER=stub`。

### 10.1 工具契约字段（stub / iflytek / tencent / azure 共用）

| Tool | 关键输出字段 |
|---|---|
| `echo_ping` | `echo`, `ts` |
| `asr_transcribe` | `text`, `confidence`, `locale`, `provider` |
| `tts_synthesize` | `audioBase64`, `mimeType`, `voice`, `locale`, `provider` |
| `pronunciation_score` | `overall`, `accuracy`, `fluency`, `completeness`, `locale`, `referenceText`, `provider` |
| `dictionary_lookup` | `word`, `phonetic`, `definitions[]`, `examples[]`, `provider` |

错误：`{"error":{"code":"...","message":"..."},"provider":"stub|azure|iflytek|tencent|http"}`。

### 10.2 CET stream SSE（2B1）

请求体：`{text?, audioBase64?, locale?, referenceText?}`（text 与 audioBase64 二选一）。

| SSE event | 含义 |
|---|---|
| `message.delta` | Tutor 文本块（兼容现 Web） |
| `audio.tts` | TTS JSON |
| `pronunciation` | 发音评分 JSON（需 audio + referenceText） |
| `plan.updated` | 阶段 Re-plan 后新计划摘要（MVP-3） |
| `done` | 结束 |