#!/usr/bin/env node
/**
 * 按主题批量导入无版权风险的道具素材：下载 → 校验 → 落盘 → 生成 Flyway seed SQL。
 *
 * 用法：
 *   node scripts/props/import-props.mjs [--manifest scripts/props/manifest.json]
 *                                       [--dest cet-tutor-server/data/cet-props]
 *                                       [--migration-version 32]
 *                                       [--dry-run] [--offline] [--concurrency 6]
 *
 * 许可白名单校验失败的条目一律不落盘，并在末尾报告。
 *
 * @author liudy
 */

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import process from "node:process";
import {
  ALLOWED_LICENSES,
  expandManifest,
  extensionFor,
  inspectAsset,
  sqlLiteral,
} from "./lib/assets.mjs";

const REPO_ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, "$1"), "../..");

function parseArgs(argv) {
  const args = {
    manifest: "scripts/props/manifest.json",
    dest: "cet-tutor-server/data/cet-props",
    migrationDir: "kidora-agent-server/src/main/resources/db/migration",
    lock: "scripts/props/props.lock.json",
    migrationVersion: "",
    concurrency: 6,
    dryRun: false,
    offline: false,
  };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--dry-run") args.dryRun = true;
    else if (arg === "--offline") args.offline = true;
    else if (arg.startsWith("--")) {
      const key = arg.slice(2).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
      args[key] = argv[++i];
    }
  }
  args.concurrency = Math.max(1, Number(args.concurrency) || 6);
  return args;
}

async function mapLimit(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor++;
      results[index] = await worker(items[index], index);
    }
  });
  await Promise.all(runners);
  return results;
}

async function fetchAsset(url) {
  const response = await fetch(url, { redirect: "follow" });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return Buffer.from(await response.arrayBuffer());
}

async function processItem(item, args) {
  const destRoot = path.resolve(REPO_ROOT, args.dest);
  const themeDir = path.join(destRoot, item.theme);

  let buffer;
  const existing = ["png", "webp", "svg", "jpg"]
    .map((ext) => path.join(themeDir, `${item.lemma}.${ext}`))
    .find((candidate) => existsSync(candidate));

  if (args.offline) {
    if (!existing) return { item, error: "offline 模式下本地无已下载文件" };
    buffer = await readFile(existing);
  } else {
    try {
      buffer = await fetchAsset(item.downloadUrl);
    } catch (e) {
      return { item, error: `下载失败 ${item.downloadUrl}: ${e.message}` };
    }
  }

  const inspected = inspectAsset(buffer);
  if (inspected.errors.length > 0) {
    return { item, error: inspected.errors.join("; ") };
  }
  const extension = extensionFor(inspected.contentType);
  if (!extension) {
    return { item, error: `不支持的类型 ${inspected.contentType}` };
  }

  const storagePath = `${item.theme}/${item.lemma}.${extension}`;
  if (!args.dryRun) {
    await mkdir(themeDir, { recursive: true });
    await writeFile(path.join(destRoot, storagePath), buffer);
  }
  return {
    item,
    record: {
      ...item,
      storagePath,
      contentType: inspected.contentType,
      byteSize: buffer.length,
      width: inspected.width,
      height: inspected.height,
      sha256: inspected.sha256,
    },
  };
}

function buildSeedSql(batch, records) {
  const values = records.map((r) => [
    r.id,
    sqlLiteral(r.lemma),
    `${sqlLiteral(JSON.stringify(r.aliases))}::jsonb`,
    sqlLiteral(r.theme),
    sqlLiteral(r.storagePath),
    sqlLiteral(r.contentType),
    r.byteSize,
    "'ACTIVE'",
    sqlLiteral(r.sourceCode),
    sqlLiteral(r.licenseCode),
    sqlLiteral(r.licenseUrl),
    sqlLiteral(r.author),
    sqlLiteral(r.sourceUrl),
    r.attributionRequired ? "TRUE" : "FALSE",
    sqlLiteral(r.sha256),
    r.width,
    r.height,
    "CURRENT_TIMESTAMP",
    "CURRENT_TIMESTAMP",
  ].join(", "));

  return `-- CET 道具素材批次：${batch}
-- 由 scripts/props/import-props.mjs 生成，请勿手改；素材文件需同步部署到 kidora.cet.props.local-dir。
INSERT INTO cet_prop_asset
(id, lemma, aliases_json, theme, storage_path, content_type, byte_size, status,
 source_code, license_code, license_url, author, source_url, attribution_required,
 checksum_sha256, width, height, create_time, update_time)
VALUES
${values.map((v) => `(${v})`).join(",\n")}
ON CONFLICT (lemma) DO UPDATE SET
    aliases_json         = EXCLUDED.aliases_json,
    theme                = EXCLUDED.theme,
    storage_path         = EXCLUDED.storage_path,
    content_type         = EXCLUDED.content_type,
    byte_size            = EXCLUDED.byte_size,
    status               = 'ACTIVE',
    source_code          = EXCLUDED.source_code,
    license_code         = EXCLUDED.license_code,
    license_url          = EXCLUDED.license_url,
    author               = EXCLUDED.author,
    source_url           = EXCLUDED.source_url,
    attribution_required = EXCLUDED.attribution_required,
    checksum_sha256      = EXCLUDED.checksum_sha256,
    width                = EXCLUDED.width,
    height               = EXCLUDED.height,
    update_time          = CURRENT_TIMESTAMP;
`;
}

async function nextMigrationVersion(dir) {
  const { readdir } = await import("node:fs/promises");
  const files = await readdir(dir);
  const max = files
    .map((f) => /^V(\d+)__/.exec(f))
    .filter(Boolean)
    .reduce((acc, m) => Math.max(acc, Number(m[1])), 0);
  return max + 1;
}

async function main() {
  const args = parseArgs(process.argv);
  const manifestPath = path.resolve(REPO_ROOT, args.manifest);
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  const { items, errors } = expandManifest(manifest);

  if (errors.length > 0) {
    console.error(`清单校验失败 ${errors.length} 条（这些条目不会落盘）：`);
    errors.forEach((e) => console.error(`  - ${e}`));
  }
  if (items.length === 0) {
    console.error("没有可导入的条目，退出。");
    process.exit(1);
  }

  console.log(`批次 ${manifest.batch}：待处理 ${items.length} 条，`
    + `许可白名单 ${Object.keys(ALLOWED_LICENSES).join("/")}`);

  const results = await mapLimit(items, args.concurrency, (item) => processItem(item, args));
  const records = results.filter((r) => r.record).map((r) => r.record);
  const failures = results.filter((r) => r.error);

  for (const failure of failures) {
    console.error(`  ✗ ${failure.item.lemma}: ${failure.error}`);
  }
  console.log(`落盘成功 ${records.length} 条，失败 ${failures.length} 条`);

  if (records.length === 0) {
    process.exit(1);
  }
  if (args.dryRun) {
    console.log("dry-run：未写文件、未生成 SQL");
    return;
  }

  const migrationDir = path.resolve(REPO_ROOT, args.migrationDir);
  const version = args.migrationVersion || String(await nextMigrationVersion(migrationDir));
  const sqlName = `V${version}__cet_prop_asset_seed_${manifest.batch.replace(/[^a-zA-Z0-9]+/g, "_")}.sql`;
  await writeFile(path.join(migrationDir, sqlName), buildSeedSql(manifest.batch, records), "utf8");

  const lockPath = path.resolve(REPO_ROOT, args.lock);
  await writeFile(lockPath, `${JSON.stringify({
    batch: manifest.batch,
    generatedAt: new Date().toISOString(),
    migration: sqlName,
    assets: records.map((r) => ({
      lemma: r.lemma,
      theme: r.theme,
      storagePath: r.storagePath,
      contentType: r.contentType,
      byteSize: r.byteSize,
      width: r.width,
      height: r.height,
      sha256: r.sha256,
      sourceCode: r.sourceCode,
      licenseCode: r.licenseCode,
      author: r.author,
      sourceUrl: r.sourceUrl,
      attributionRequired: r.attributionRequired,
    })),
  }, null, 2)}\n`, "utf8");

  console.log(`已生成迁移 ${sqlName}`);
  console.log(`已更新锁文件 ${path.relative(REPO_ROOT, lockPath)}`);
  if (failures.length > 0) process.exitCode = 1;
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
