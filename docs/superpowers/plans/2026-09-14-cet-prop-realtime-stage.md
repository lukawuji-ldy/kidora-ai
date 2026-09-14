# CET 道具：实时动态下发 + 素材管理重构（实施记录）

设计见 [2026-09-14-cet-prop-realtime-stage-design.md](../specs/2026-09-14-cet-prop-realtime-stage-design.md)。

## 阶段一：收敛现有实现（重构，无新功能）

| # | 任务 | 产出 | 状态 |
|---|---|---|---|
| 1 | 统一 hint 口径 | 删除 `PlanLearningHints.propResolveHints`；`getSession` 改用 `propCandidateHints`；新增 `isPropStopWord`；删除 `PropAssetResolver.PROP_STOP_WORDS` | 完成 |
| 2 | 别名下沉到 DB | 新增 `PropVocabulary`（TTL 缓存，重载失败沿用旧快照）+ `CetPropAssetRepository.listActive()`；删除 `PropAssetResolver.ALIAS_TO_LEMMA`；中文别名迁入 `aliases_json`（V31） | 完成 |
| 3 | 素材单一来源 | `kidora-web/public/props` 只留 `default/star.svg`；`PersonaStage` 回退简化为「取图失败 → 占位图 → 移除该 lemma」 | 完成 |

## 阶段二：实时动态下发（后端权威 + SSE）

| # | 任务 | 产出 | 状态 |
|---|---|---|---|
| 4 | SSE 事件 | `CetStreamEvent.PROP` + `prop()`；`CetSessionController.toSse` 映射 `turn.prop` | 完成 |
| 5 | 后端选图 | 新增 `PropStageDirector`（点名即展示，字幕顺序、≤3 张、无颜色/宠物特判）+ `SessionPropContext` 会话级缓存；`resolveAvailablePropLemmas` → `resolveAvailableProps` 返回 `PropAssetView` | 完成 |
| 6 | 接入四条流 | `streamOpening` / `streamTurn` / 告别第 1 句 / 告别第 2 句在 `deltas` 后 `concatWith` prop 事件；判定异常只 WARN | 完成 |
| 7 | 提示词收紧 | `TutorLoop.putPropVars`：要求外教在句中说出目标英文词 | 完成 |
| 8 | 前端瘦身 | `lessonProps.ts` 只剩类型 + `toPropStageView` + `goalStepFromTurnCount`；session page 由 `onProp` 驱动 `propStage`；`api.ts` 合并两份重复 SSE 分发为 `dispatchSseData` 并新增 `onProp` | 完成 |
| 9 | 取图缓存 | `CetPropController` ETag/304（`checksum_sha256` 前 32 位）；`fetchAuthedBlobUrl` 模块级 blob 缓存 + `prefetchAuthedBlob` | 完成 |

## 阶段三：道具管理与维护

| # | 任务 | 产出 | 状态 |
|---|---|---|---|
| 10 | DDL 扩展 | `V31__cet_prop_asset_license.sql`；同步 `schema/25_cet_prop_asset.sql`、`schema/all.sql`（补漏 `26_`），全字段 `COMMENT ON` | 完成 |
| 11 | 离线导入 | `scripts/props/manifest.json` + `lib/assets.mjs` + `import-props.mjs`（零依赖，许可白名单硬校验，生成 seed SQL 与 `props.lock.json`） | 完成 |
| 12 | 体检 | `scripts/props/verify-props.mjs`（锁文件/磁盘/可选 DB 三方）+ `PropLibraryIntegrityChecker` 启动 WARN | 完成 |
| 13 | 首批素材 | OpenMoji 60 个，10 主题；生成 `V32__cet_prop_asset_seed_2026_09_14_openmoji_core.sql`；磁盘 23 个旧孤儿 SVG 已清理 | 完成 |
| 14 | 署名合规 | `GET /api/cet/props/credits` + `kidora-web /legal/credits` | 完成 |

## 阶段四：文档同步

`docs/ui-design.md`（舞台态改为 SSE 驱动，删颜色线索规则）、`docs/cet-lesson-flow.md`（新增 `turn.prop`）、`docs/database-design.md`（新字段 / 白名单 / V31·V32 / 生成任务边界）、`docs/cet-tutor-design.md`、`AGENTS.md` 索引、`scripts/props/README.md`（取代 `scripts/seed-cet-props.md`）；旧 spec 标 Superseded。

## 验证结果

| 项 | 命令 | 结果 |
|---|---|---|
| 后端核心单测 | `mvn -f cet-tutor-core/pom.xml test` | 121 通过（新增 `PropStageDirectorTest` 13、`PropVocabularyTest` 5、`SessionPropContextTest` 4、`propStageJson` 2） |
| 后端服务单测 | `mvn -f cet-tutor-server/pom.xml test` | 27 通过（`CetPropControllerTest` 扩为 ETag/304/404） |
| 前端类型 | `npx tsc --noEmit` | 无新增错误（3 个 `.ts` 扩展名告警为既有） |
| 前端单测 | `npm run test:unit` | 23 通过（新增 `lessonProps.test.ts` 5） |
| 前端构建 / Lint | `npx next build` / `next lint` | 成功、无告警 |
| 素材体检 | `node scripts/props/verify-props.mjs` | 60/60 通过，无孤儿文件 |
