# CET Tutor 中文脚手架 + TTS 括号不读 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在约定场景下让 CET 外教用「中文脚手架 + 英文示范句」回复；气泡可保留英文后的 `(中文注释)`，TTS 朗读中英正文但不读括号注释。

**Architecture:** 行为由 Prompt（Flyway 更新 `cet.tutor.system` / `cet.tutor.opening.system`）驱动；朗读稿仍走现有 `CetLessonService.speakableForTts`（剥含汉字括号、保留其余正文）。本期不改 SSE、不加表、不做结构化 JSON。

**Tech Stack:** JDK 17；`cet-tutor-core` JUnit；`kidora-agent-server` Flyway（PostgreSQL `prompt_template`）；文档 `docs/cet-tutor-design.md` + 本规格。

**Spec:** `docs/superpowers/specs/2026-09-10-cet-tutor-zh-scaffold-tts-design.md`

## Global Constraints

- 不新增 DB 业务表；仅 Prompt 迁移（建议 `V12__cet_tutor_zh_scaffold.sql`）
- 不改 SSE 事件结构 / 前端拼装
- 不改 Safety / Planner / Eval 主逻辑
- 提示词正文须中文；`{{变量}}` 保持英文键
- Java 类型级 Javadoc 含 `@author liudy`（修改已有方法注释时保留）
- 改对外行为/文档须同次交付同步 `docs/`
- 子工程独立 Maven；验证命令在对应模块目录执行

---

## File map

| File | Responsibility |
|---|---|
| `cet-tutor-core/.../CetLessonService.java` | `speakableForTts` / `buildSpeechExtras` 注释口径改为「读中英正文、不读括号」 |
| `cet-tutor-core/.../CetLessonServiceTest.java` | 脚手架混合句 TTS 单测；重命名过时的 `ttsUsesEnglishOnly` |
| `kidora-agent-server/.../V12__cet_tutor_zh_scaffold.sql` | 更新 Tutor / Opening 系统提示并同步 published version 行 |
| `docs/cet-tutor-design.md` | 纠错/中英比例/TTS 文档与规格对齐 |
| `docs/superpowers/specs/2026-09-10-cet-tutor-zh-scaffold-tts-design.md` | 状态 → Accepted，实现完成后 → Implemented |

`docs/cet-lesson-flow.md`：当前无气泡/TTS 专节，**本期不改**（除非实现时发现过时句子）。

---

### Task 1: TTS 单测与注释口径（中英正文 + 剥括号）

**Files:**
- Modify: `cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/service/CetLessonServiceTest.java`
- Modify: `cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/service/CetLessonService.java`（仅 Javadoc，逻辑不变除非单测暴露缺口）
- Test: 同上 Test 类

**Interfaces:**
- Consumes: `CetLessonService.speakableForTts(String)`；`buildSpeechExtras(...)`
- Produces: 单测锁定「中文脚手架 + 英文 + 括号注释」的 TTS 行为

- [ ] **Step 1: 扩展失败/先写断言（TDD）**

在 `speakableForTts_stripsChineseParentheticalHints` 末尾追加：

```java
assertEquals(
        "说得不错！你可以说 \"My dog is white\"。What color is your dog?",
        CetLessonService.speakableForTts(
                "说得不错！你可以说 \"My dog is white\"。What color is your dog? (你的狗是什么颜色？)"));
```

将 `buildSpeechExtras_ttsUsesEnglishOnly` **重命名**为 `buildSpeechExtras_ttsStripsParentheticalKeepsChineseAndEnglish`，并把 stub 期望改为混合句：

```java
@Test
void buildSpeechExtras_ttsStripsParentheticalKeepsChineseAndEnglish() {
    SpeechToolPort port = new SpeechToolPort() {
        @Override
        public Optional<AsrResult> asr(String audioBase64, String locale) {
            return Optional.empty();
        }

        @Override
        public Optional<TtsResult> tts(String text, String voice, String locale) {
            assertEquals(
                    "说得不错！你可以说 \"My dog is white\"。What color is your dog?",
                    text);
            return Optional.of(new TtsResult("xx", "audio/wav", "stub"));
        }

        @Override
        public Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale) {
            return Optional.empty();
        }
    };
    List<CetStreamEvent> extras = CetLessonService.buildSpeechExtras(
            port,
            "说得不错！你可以说 \"My dog is white\"。What color is your dog? (你的狗是什么颜色？)",
            null, "en-US", null, null);
    assertEquals(1, extras.size());
}
```

保留原有英文+括号用例（`Do you have a pet? (你有宠物吗？)`）作为回归。

- [ ] **Step 2: 跑测试确认现状**

Run（在 `cet-tutor-core` 目录）:

```powershell
mvn -q -Dtest=CetLessonServiceTest#speakableForTts_stripsChineseParentheticalHints,CetLessonServiceTest#buildSpeechExtras_ttsStripsParentheticalKeepsChineseAndEnglish test
```

Expected: **PASS**（现有 `speakableForTts` 已剥括号并保留中英）。若 FAIL，进入 Step 3 修正则；若 PASS，Step 3 只改注释。

- [ ] **Step 3: 更新 Javadoc（逻辑通常无需改）**

`buildSpeechExtras` 注释改为：

```java
/**
 * 构建 TTS / 发音附加事件。
 * TTS 朗读气泡正文（中文脚手架 + 英文例句），去掉含中文的括号注释；气泡原文仍完整下发。
 *
 * @param voice 人设映射音色；null 表示不传 voice（厂商默认）
 */
```

`speakableForTts` 注释改为：

```java
/**
 * 供 TTS 朗读：去掉含中文的括号注释，保留中文主句与英文例句。
 * 例如 {@code What color is your dog? (你的狗是什么颜色？)} → {@code What color is your dog?}；
 * {@code 说得不错！My dog is white。} 保持不变。
 * 发音评测仍由调用方传入独立 {@code referenceText}（通常为英文目标句），本方法仅对其做同样剥括号。
 *
 * @param text 外教气泡全文或参考句
 * @return 适合朗读的正文；若剥离后为空则回退原文
 * @author liudy
 */
```

若 Step 2 FAIL：检查正则 `[（(][^）)]*[\\u4e00-\\u9fff][^）)]*[）)]` 是否误伤引号内内容；以「只删含汉字的括号段」为原则微调，勿删中文正文。

- [ ] **Step 4: 再跑整类测试**

```powershell
mvn -q -Dtest=CetLessonServiceTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```powershell
git add cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/service/CetLessonServiceTest.java cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/service/CetLessonService.java
git commit -m "test(cet): lock TTS speakable text as Chinese+English minus parentheticals"
```

---

### Task 2: Flyway Prompt — 中文脚手架规则

**Files:**
- Create: `kidora-agent-server/src/main/resources/db/migration/V12__cet_tutor_zh_scaffold.sql`
- Modify: none in Java（运行时读库表）

**Interfaces:**
- Consumes: 既有 `prompt_template.code` = `cet.tutor.system`、`cet.tutor.opening.system`
- Produces: 库内已发布正文含 §2 触发条件与表达约束

- [ ] **Step 1: 新建迁移文件**

Create `kidora-agent-server/src/main/resources/db/migration/V12__cet_tutor_zh_scaffold.sql`（完整文件如下，与 V10 同步 published version 的写法一致）：

```sql
-- CET 中文脚手架：A0/A1 / 中文求助 / 显式纠错；TTS 读中英正文、不读括号注释

UPDATE prompt_template SET
    name = 'CET 陪练系统提示',
    content = '你是面向儿童的友好 AI 英语外教，人设为 {{personaId}}。请停留在当前计划阶段 {{stageId}}。难度对齐 CEFR {{cefr}}。当前计划摘要：{{planSummary}}。

交互原则（必须遵守）：
1. 每轮简短：先鼓励，再按需纠错，最后只提一个下一问；禁止长篇讲课或一次问多个问题。
2. 默认隐式纠错：用正确英语自然复述孩子的意思，再追问；不要考试腔，不要列出分数或错误清单。
3. 显式纠错（目标语法反复出错或严重影响理解）：短鼓励 → 1～3 句中文讲解（为什么）→ 英文关键点/正确句 → 请孩子再说一次。中文讲解控制在 1～3 句。
4. 中文脚手架（以下任一即启用，优先于「A2+ 英文为主」）：
   - CEFR 为 A0 或 A1；
   - 孩子用中文求助如何用英语表达；
   - 本轮显式纠错。
   启用时：鼓励、讲解、引导、追问用中文；示范句与关键点用英文（可给 1～2 个短句对照）。A0/A1 下一问默认中文；必要时可用英文问句并在后附（中文注释）。
5. A2 及以上且未触发脚手架：以英文为主，必要时一句中文点拨。
6. 英文句子后可以附（中文注释）供屏幕阅读；禁止整段「英文主句 + 逐句括号翻译」堆叠。系统朗读会去掉含中文的括号，并朗读中文正文与英文例句。
7. 禁止讨论成人或不安全话题。',
    update_time = TIMESTAMPTZ '2026-09-10 10:00:00+00'
WHERE code = 'cet.tutor.system';

UPDATE prompt_template SET
    name = 'CET 开场系统提示',
    content = '你是面向儿童的友好 AI 英语外教，人设为 {{personaId}}。难度对齐 CEFR {{cefr}}。主题：{{topic}}。计划摘要：{{planSummary}}。
请用口语化短句：先打招呼，点一下今天主题，再只提一个简单开场问题。
若 CEFR 为 A0/A1：打招呼与点题可用中文，下一问默认中文；示范词汇可点出英文。英文句子后可附（中文注释）供屏幕阅读。
A2+：主句偏英文，括号中文最多 1～2 处。
禁止长篇、禁止一次问多个问题、禁止不安全话题。系统朗读会去掉括号中文并朗读中英正文。',
    update_time = TIMESTAMPTZ '2026-09-10 10:00:00+00'
WHERE code = 'cet.tutor.opening.system';

UPDATE prompt_template_version v
SET name = t.name, content = t.content
FROM prompt_template t
WHERE v.code = t.code
  AND v.version = t.published_version
  AND t.code IN ('cet.tutor.system', 'cet.tutor.opening.system');
```

本期**不改** `cet.tutor.user` / opening.user（规格允许保持结构）。

- [ ] **Step 2: 自检 SQL 文件**

确认文件中：

- 两个 `UPDATE prompt_template`（`cet.tutor.system`、`cet.tutor.opening.system`）+ 一个 `UPDATE prompt_template_version`
- 正文含脚手架三条触发 + 禁止英文+括号堆叠 + TTS 剥括号说明

- [ ] **Step 3: Commit**

```powershell
git add kidora-agent-server/src/main/resources/db/migration/V12__cet_tutor_zh_scaffold.sql
git commit -m "feat(cet): prompt Chinese scaffolding for A0/A1, help, and correction"
```

（本地已有库时，需重启/跑 `kidora-agent-server` Flyway 使 Prompt 生效；本任务不强制起服务，但交付说明中写明。）

---

### Task 3: 文档与规格状态

**Files:**
- Modify: `docs/cet-tutor-design.md`（约 §4 纠错话术段，现 L90–95）
- Modify: `docs/superpowers/specs/2026-09-10-cet-tutor-zh-scaffold-tts-design.md`（状态行）

**Interfaces:**
- Consumes: 规格 §2–§3 已确认决策
- Produces: 设计文档与实现一致；规格标 Implemented

- [ ] **Step 1: 替换 `docs/cet-tutor-design.md` 纠错话术段**

将现有：

```markdown
**纠错话术（Prompt 约束，非独立 Agent）：**

- 默认**隐式纠错**：正确英语复述 + 追问，不考试腔、不列分数。  
- 目标语法反复错或严重影响理解时**显式纠错**：短鼓励 → 1～3 句中文讲解 → 英文关键点/正确句 → 请孩子再说一次。  
- 中英比例随 CEFR（低龄偏中文解释，高水平少中文）。  
- **气泡**可带少量括号中文提示；**TTS / 发音参考**只取英文主句（`speakableForTts` 去掉括号中文），避免外教把翻译念出来。
```

替换为：

```markdown
**纠错话术（Prompt 约束，非独立 Agent）：**

- 默认**隐式纠错**：正确英语复述 + 追问，不考试腔、不列分数。  
- 目标语法反复错或严重影响理解时**显式纠错**：短鼓励 → 1～3 句中文讲解 → 英文关键点/正确句 → 请孩子再说一次。  
- **中文脚手架**（A0/A1 默认；孩子中文求助；显式纠错）：鼓励/讲解/引导/追问用中文，示范句与关键点用英文；A0/A1 下一问默认中文。A2+ 平常仍以英文为主。  
- **气泡**可在英文后保留 `(中文注释)`；**禁止**整段「英文主句 + 逐句括号翻译」堆叠。  
- **TTS**（`speakableForTts`）：朗读中文正文 + 英文例句，去掉含中文的括号注释。发音评测仍用独立英文 `referenceText`，不把整段中文讲解当跟读稿。
```

- [ ] **Step 2: 更新规格状态**

文件头改为：

```markdown
- **状态：** Implemented（2026-09-10）
```

（若实现日不同，用实际日期。）

- [ ] **Step 3: Commit**

```powershell
git add docs/cet-tutor-design.md docs/superpowers/specs/2026-09-10-cet-tutor-zh-scaffold-tts-design.md
git commit -m "docs(cet): align tutor zh-scaffold and TTS parenthetical rules"
```

---

## Plan self-review

| Spec 要求 | 对应任务 |
|---|---|
| A0/A1 / 求助 / 纠错中文脚手架 | Task 2 Prompt |
| 英文后括号保留、禁止堆叠 | Task 2 + Task 3 文档 |
| TTS 读中英、不读括号 | Task 1 单测 + 注释（逻辑已存在） |
| 发音参考不整段中文讲解 | Task 1 Javadoc 说明；`referenceText` 仍由调用方传入，不改协议 |
| 不改 SSE/新表 | 全局约束；无相关任务 |
| `cet-tutor-design` 同步 | Task 3 |
| `cet-lesson-flow` | 无 TTS 专节 → 明确不改 |

**Placeholder scan:** 无 TBD/TODO；Task 2 SQL 为完整可复制文件。

**Type consistency:** `speakableForTts(String)` / `buildSpeechExtras` 签名不变。

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-10-cet-tutor-zh-scaffold-tts.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — 每任务新开子代理，任务间复核，迭代快  
2. **Inline Execution** — 本会话按 executing-plans 连续执行，设检查点  

Which approach?
