# CET 道具：外教声明主图 + 道具根目录工作目录容错（设计）

- **日期：** 2026-09-14
- **范围：** `cet-tutor-core` / `cet-tutor-server`
- **取代：** [2026-09-14-cet-prop-realtime-stage-design.md](2026-09-14-cet-prop-realtime-stage-design.md) §3「唯一规则」与 §3.1 升级路径（该文其余部分仍有效）

---

## 1. 问题

线上表现：外教说「今天我们来聊聊动物和宠物（animals and pets）。Look! Is this a cat or a dog?」，孩子屏幕上主图是 `pet`（爪印泛称），且三张图全渲染成前端占位星星。两个独立缺陷叠在一起。

### 1.1 主图选错：字幕顺序 ≠ 教学焦点

`PropStageDirector` 按字幕出现位置排序、`activeLemma` 取第一个。`pet` 的 `aliases_json` 含 `animal / animals / 宠物 / 动物`，教学句里泛称几乎总在具体词之前出现（先点题再提问），于是泛称稳定抢占主图位，被真正问到的 `cat` / `dog` 沦为缩略图。这不是别名配错——**「最早被点名」这个代理指标本身就不等于「本轮要孩子看的那张」**。

### 1.2 图取不到：`local-dir` 相对进程工作目录

`kidora.cet.props.local-dir` 默认 `./data/cet-props`，相对**进程工作目录**。素材实际在 `cet-tutor-server/data/cet-props`，因此只有工作目录恰为该模块时才对得上。IDE 运行配置默认工作目录是仓库根时，`PropFileLocator.resolve` 每次都落到不存在的路径，`GET /api/cet/props/{lemma}` 全部 404「道具文件缺失」，前端 `AuthedPropImg` 回退到 `/props/default/star.svg`。启动时 `PropLibraryIntegrityChecker` 会打 WARN，但不阻断，容易被忽略。

## 2. 目标

1. 主图 = 外教本轮真正指向的那个词，且判定**不依赖**任何字幕句式正则、颜色线索或主题特判。
2. 裸问句（`Look! What is this?`）也能出图。
3. 相对 `local-dir` 在两种常见工作目录下都能命中素材，命中不了时日志给出绝对路径。

## 3. 非目标

- 不把 Tutor 整体输出改成 JSON（安全闸门、SSE 分块、TTS 全部按纯文本处理）
- 不给 `cet_prop_asset` 加「泛化词」标记类字段
- 不改 `PropAssetResolver` / `SessionPropContext` / 前端渲染

## 4. 方案

### 4.1 Tutor 输出协议：`[[PROP:词]]`

`{{propInstruction}}` 要求外教在回复末尾另起一行声明本轮展示哪张图：

```
Look! Is this a cat or a dog?（看，这是猫还是狗？）
[[PROP:dog]]
```

- 词只能取 `{{availablePropLemmas}}` 里的一个，且必须是本轮问题真正指向的那个（提示词明确点出「问 `Is this a cat or a dog?` 时选 `cat` 或 `dog`，不要选 `pet` 这种泛称」）。
- 本轮不需要图片 → `[[PROP:none]]`。没有可用道具时，`propInstruction` 走禁图分支并要求恒写 `[[PROP:none]]`。

`TutorLoop.parseReply(raw)` 返回 `TutorReply{text, propLemma}`：大小写与空格容错，多个标记取最后一个，`none` 归一为 `null`，**所有标记从文本中剥离**。标记只是后端与模型之间的协议，必须在输出安全闸门、`cet_tutor_turn` 落库、SSE `message.delta`、TTS 之前消失，因此 `generateReply` / `generateOpening` 的返回类型从 `String` 改为 `TutorReply`，`CetLessonService` 用 `reply.text()` 进闸门、`reply.propLemma()` 进舞台判定。告别话术（`cet.tutor.wrapup.*`）不注入 `propInstruction`，恒发 `personaFocus`，不参与本协议。

### 4.2 判定优先级

`PropStageDirector.direct(declaredLemma, tutorText, availableAssets)`：

| 情形 | `activeLemma` | `assets` |
|---|---|---|
| 声明命中本课可用道具 | 声明词 | 声明词在首，其后接被点名的其他道具（按字幕顺序，去重，≤3） |
| 声明缺失 / `none` / 不在本课可用集合 | 最早被点名的词 | 被点名的道具按字幕顺序（≤3），与旧口径一致 |
| 既无有效声明也无点名 | `null` | 空，`personaFocus` |

声明词先过 `PropVocabulary.normalize`，所以模型写别名（`puppy` / `小狗`）也能归一到 `dog`。声明词**不要求**在字幕里出现——这正是裸问句能出图的原因。保留 `direct(tutorText, assets)` 两参重载委托到三参版本，兜底口径可独立回归。

### 4.3 道具根目录解析

`PropFileLocator.resolveRoot(localDir, workingDir)`：

1. 配置值是绝对路径 → 直接用（生产用 `KIDORA_CET_PROPS_DIR` 指向持久化卷，行为不变）
2. `workingDir/<相对路径>` 是目录 → 用它
3. 否则 `workingDir/cet-tutor-server/<相对路径>` 是目录 → 用它，并 WARN 提示设 `KIDORA_CET_PROPS_DIR` 或修正工作目录
4. 两处都不存在 → 返回第 2 步的路径，让 `PropLibraryIntegrityChecker` 的 WARN 指向用户实际配的位置

根目录在 bean 构造时解析一次并缓存；`root()` 语义不变，路径穿越校验不变。

## 5. 验收

1. 声明 `dog` + 字幕「…动物和宠物…Is this a cat or a dog?」→ `activeLemma=dog`，`assets=[dog, pet, cat]`
2. 声明别名 `小狗` → 归一为 `dog`
3. 声明 `cat` + 裸问句 `Look! What is this?` → `propFocus`，`activeLemma=cat`
4. 声明 `dog` 但本课只发布了 `cat` → 退回点名，`activeLemma=cat`
5. 声明为空 / `none` 且无点名 → `personaFocus`
6. 声明 + 多个点名 → 仍 ≤3 张，声明词在首
7. `[[PROP:…]]` 不出现在 `TutorReply.text()` 中（进而不进闸门、不落库、不进 SSE 与 TTS）
8. 相对 `local-dir`：工作目录命中优先；缺失时回退模块子目录；两处都无时保留配置路径
