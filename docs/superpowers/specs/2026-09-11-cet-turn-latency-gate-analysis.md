# CET 一轮耗时门禁分析（2026-09-11）

- **仓库：** `kidora-ai`
- **关联：** [2026-09-11-cet-turn-latency-metrics-design.md](./2026-09-11-cet-turn-latency-metrics-design.md)、[2026-09-11-cet-turn-latency-o1-safety-design.md](./2026-09-11-cet-turn-latency-o1-safety-design.md)
- **库：** 本地 `kidora_ai`（`127.0.0.1:5432`）
- **查询：** 规格 §6.2 分位 SQL + `e2eHeardMs - ttsReadyMs` gap

## 1. 样本量

| 口径 | 数量 | 门禁要求 | 结论 |
|---|---|---|---|
| `path=voice` 且 `timing_json` 非空 | **6** | ≥ 30 | **不足**，正式大优化单暂不强制开工 |
| 非空 `client.e2eHeardMs` | **6** | ≥ 20 | **不足** |

仍输出分位作为**早期方向信号**（不可替代门禁）。

## 2. 语音轮分位（N=6）

| 指标 | p50 (ms) | p95 (ms) |
|---|---|---|
| asr | 1265 | 2259 |
| tutor | **10809** | 17146 |
| safetyIn+Out | **7379** | 9646 |
| tts | 3088 | 5298 |
| score | 1750 | 2640 |
| ttsReady | **25240** | 35365 |
| e2eHeard | 25506 | 35666 |
| e2e − ttsReady (gap) | **296** | — |

近期单行示例（turn）：`asr≈1s` + `safety≈5–10s` + `tutor≈4–17s` + `tts≈2–6s` + `score≈1–3s` ≈ `ready≈13–36s`。

说明：上述 `ttsReady` **含**当时实现里同步 score 的墙钟；P0（O3）落地后新样本的 `ttsReadyMs` 应大约少掉 `scoreMs`（约 1–3s），但仍远高于儿童可接受开口延迟。

## 3. 门禁对照（§6.3）

| # | 条件 | 实测（早期） | 判定 |
|---|---|---|---|
| 1 | 段 p95&lt;300ms 且占 ready&lt;15% → 暂不优化 | 各主段均远超 | 不适用「跳过」 |
| 2 | safety p50 ≥ 0.7 × tutor p50 → **O1** | 7379 / 10809 ≈ **0.68** | **逼近触发**；部分单轮 &gt;0.7 |
| 3 | tts p50 ≥ 0.5 × tutor p50 → **O2** | 3088 / 10809 ≈ **0.29** | **未触发** |
| 4 | score p95 ≥ 500ms 且 UI 不展示 → **O3** | score p95≈2640 | **已触发且 P0 已落地** |
| 5 | (e2e − ready) p50 ≥ 800ms → 前端/网络 | gap p50≈296 | **未触发**（优先改服务端） |

## 4. 结论与下一单

1. **P0/O3 必要且已做**：score 曾挡在 `audio.tts` / `ttsReady` 上；移出后每轮听感约少 1–3s。
2. **绝对瓶颈是 Tutor（~11s）与 Safety 双调用（~7s）**；前端 gap 很小，不必先查自动播放。
3. 正式门禁样本不足 → **不**立刻上 Tutor 真流式 + 句级 TTS 全家桶（O2）。
4. 下一份可评审规格锁定为 **O1 Safety 听感路径减负**（见 [o1-safety 规格](./2026-09-11-cet-turn-latency-o1-safety-design.md)）：工程面小于 O2、儿童安全可评审、且早期 ratio 已逼近 0.7；实现后用 N≥30 复测，若 tutor 仍主导再开 O2。

**实现回写（2026-09-11）：** O1 已落地于 [`SafetyGuard`](../../../cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/safety/SafetyGuard.java)（`L0_FAST_ALLOW` / `L0_OUT_SKIP_MODEL`）。请在积累 voice N≥30 后重跑 §5 SQL，核对 safety p50 / tutor p50 是否下降。

**Tutor 模型（同日）：** CET 陪练已可经 `caller-config-ids.CET_TUTOR=llm_chat_primary` 换更快模型；换模后应用同一套 timing SQL 对比 `CET_TUTOR` 的 `latency_ms` / `tutorMs`。

## 5. 复测 SQL（样本够后再跑）

沿用耗时规格 §6.2；另加：

```sql
SELECT
  percentile_cont(0.5) WITHIN GROUP (ORDER BY
    (timing_json->'client'->>'e2eHeardMs')::int
    - (timing_json->>'ttsReadyMs')::int)
    FILTER (WHERE timing_json->'client'->>'e2eHeardMs' IS NOT NULL) AS gap_p50,
  COUNT(*) AS voice_n
FROM cet_tutor_turn
WHERE timing_json IS NOT NULL
  AND timing_json->>'path' = 'voice'
  AND create_time > now() - interval '7 days';
```
