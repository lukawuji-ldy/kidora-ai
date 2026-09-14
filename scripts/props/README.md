# CET 道具素材导入与维护

道具库 = **磁盘文件**（`kidora.cet.props.local-dir`，默认 `cet-tutor-server/data/cet-props`）
+ **元数据表** `cet_prop_asset`。两者必须成对更新，本目录的脚本负责保证这一点。

本仓库不暴露 `/api/admin/**`；日常上传与审核在旁路仓库 `kidora-ai-manage`，
批量按主题引入素材走这里的离线脚本。

## 1. 许可白名单（硬校验）

导入脚本与 `ck_cet_prop_asset_license` 使用同一份白名单，不在表内的协议**直接拒绝落盘**：

| `license_code` | 是否需要署名 | 典型来源 |
|---|---|---|
| `CC0-1.0` | 否 | Openclipart、Kenney.nl |
| `PD` | 否 | Wikimedia 公有领域、本仓自绘 |
| `CC-BY-4.0` | 是 | 部分 Wikimedia / 独立作者 |
| `CC-BY-SA-4.0` | 是 | OpenMoji |
| `PIXABAY` | 否 | Pixabay Content License |

需要署名的素材会置 `attribution_required = TRUE`，由 `GET /api/cet/props/credits`
输出到前台 `/legal/credits` 页面。**漏署名等于违反协议**，不要手工把这个字段改成 FALSE。

推荐来源（儿童启蒙场景，单体实物图最合适）：

- **OpenMoji**（CC-BY-SA 4.0）：动物 / 食物 / 天气 / 学校 / 衣物 / 交通覆盖最全，风格统一
- **Openclipart**（CC0）：线稿剪贴画，无需署名
- **Kenney.nl**（CC0）：游戏素材包，图标风格
- **Wikimedia Commons** 公有领域分类：真实照片
- **Pixabay**：照片与插画，注意逐张确认是 Content License

## 2. 清单格式

`manifest.json`：

```jsonc
{
  "batch": "2026-09-14-openmoji-core",   // 批次名，用于生成迁移文件名
  "idBase": 51000,                        // 本批 id 起始值，不同批次不能重叠
  "defaults": {
    "sourceCode": "openmoji",
    "licenseCode": "CC-BY-SA-4.0",
    "author": "OpenMoji",
    "downloadUrlTemplate": "https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/color/618x618/{code}.png",
    "sourceUrlTemplate": "https://openmoji.org/library/emoji-{code}/"
  },
  "items": [
    { "lemma": "dog", "theme": "pets", "code": "1F436", "aliases": ["puppy", "狗", "小狗"] }
  ]
}
```

- `lemma` 必须是小写英文且全局唯一，它同时是 `GET /api/cet/props/{lemma}` 的路径。
- `aliases` 是**运行时词表的唯一权威**：后端 `PropVocabulary` 只认库里的别名，代码中不再有硬编码映射。英文复数由通用规则还原，不必逐个列 `dogs`。
- 单条可用 `sourceCode` / `licenseCode` / `author` / `downloadUrl` / `sourceUrl` 覆盖 `defaults`。
- `credits` 是保留字，不能作为 lemma。

## 3. 导入

```bash
# 预演：只下载校验，不写文件也不生成 SQL
node scripts/props/import-props.mjs --dry-run

# 正式导入
node scripts/props/import-props.mjs
```

流程：下载 → 按魔数识别真实类型（防止把 HTML 错误页存成 png）→ 体积 ≤ 512KB、
边长 64–2048px、SVG 拒绝内嵌脚本 → 落盘 `{theme}/{lemma}.{ext}` → 算 SHA-256 →
生成 `kidora-agent-server/src/main/resources/db/migration/V{next}__cet_prop_asset_seed_{batch}.sql`
（`ON CONFLICT (lemma) DO UPDATE`，可反复执行）→ 更新 `scripts/props/props.lock.json`。

常用参数：`--manifest` `--dest` `--migration-version` `--concurrency` `--offline`（跳过下载，用本地已有文件重算元数据）。

导入后重启 `kidora-agent-server` 跑 Flyway，再重启 `cet-tutor-server`
（或等 `kidora.cet.props.vocab-ttl-seconds` 到期）即可让新词表生效。

## 4. 体检

```bash
# 锁文件 ↔ 磁盘
node scripts/props/verify-props.mjs

# 加上数据库三方比对（需要 psql 在 PATH 上）
node scripts/props/verify-props.mjs --dsn postgres://kidora:pw@127.0.0.1:5432/kidora_ai
```

检查项：缺文件、SHA-256 / 体积漂移、`byte_size = 0` 未回填、孤儿文件（磁盘有但库里没有 ACTIVE 行）。
有任何问题以退出码 1 结束，可直接接 CI。

`cet-tutor-server` 启动时也会做一次轻量检查（`PropLibraryIntegrityChecker`），
只打 WARN 日志，不阻断启动。

## 5. 部署注意

- **图片二进制不进 Git**（`cet-tutor-server/data/cet-props/` 已在 `.gitignore`）。进 Git 的是 `manifest.json` + `props.lock.json` + 生成的 seed SQL，所以任何一份检出都能用 `node scripts/props/import-props.mjs` 原样重建素材，并用 `verify-props.mjs` 核对校验和。
- `cet-tutor-server/data/cet-props` 是本地开发目录，生产环境用 `KIDORA_CET_PROPS_DIR` 指向持久化卷，并把同一批文件同步过去。
- 迁移只写元数据，不含图片二进制。**只跑迁移不同步文件**会导致运行时 404，`verify-props.mjs` 就是用来提前发现这种情况的。
- 缺失道具由运行时写入 `cet_prop_asset_generation_task`（`QUEUED`）；本仓不生成图片，生成与审核在 `kidora-ai-manage`。把常用词补进本清单后，该队列应保持为空。
