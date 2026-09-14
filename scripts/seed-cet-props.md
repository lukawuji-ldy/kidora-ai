# CET 教具本地目录 seed（已迁移）

手工复制 `kidora-web/public/props` 的做法已废弃——那会产生两份会漂移的素材，且没有来源/许可元数据。

素材导入、许可白名单与体检统一走 **[`scripts/props/README.md`](props/README.md)**：

```bash
node scripts/props/import-props.mjs          # 下载 → 校验 → 落盘 → 生成 Flyway seed SQL
node scripts/props/verify-props.mjs          # 锁文件 / 磁盘 / 数据库三方体检
```

导入后启动 `kidora-agent-server` 跑 Flyway（`V20` 表 + `V21` 初始 seed + `V31` 许可字段 + `V32` 素材批次），再启 `cet-tutor-server`。目录可用 `KIDORA_CET_PROPS_DIR` 覆盖。

设计背景见 [2026-09-14-cet-prop-realtime-stage-design.md](../docs/superpowers/specs/2026-09-14-cet-prop-realtime-stage-design.md)。
