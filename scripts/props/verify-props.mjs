#!/usr/bin/env node
/**
 * 道具库体检：锁文件 / 磁盘 / 数据库三方比对。
 *
 * 用法：
 *   node scripts/props/verify-props.mjs [--dest cet-tutor-server/data/cet-props]
 *                                       [--lock scripts/props/props.lock.json]
 *                                       [--dsn postgres://user:pw@host:5432/kidora_ai]
 *
 * 无 --dsn 时只比对锁文件与磁盘；有 --dsn 且 psql 在 PATH 上时，额外检查
 * 「库里 ACTIVE 但磁盘缺文件」与「磁盘有文件但库里没有」。
 * 发现问题以退出码 1 结束，便于接 CI。
 *
 * @author liudy
 */

import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { readFile, readdir, stat } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const REPO_ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, "$1"), "../..");

function parseArgs(argv) {
  const args = {
    dest: "cet-tutor-server/data/cet-props",
    lock: "scripts/props/props.lock.json",
    dsn: "",
  };
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i].startsWith("--")) args[argv[i].slice(2)] = argv[++i];
  }
  return args;
}

async function listFiles(root) {
  const out = [];
  async function walk(dir) {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) await walk(full);
      else out.push(path.relative(root, full).split(path.sep).join("/"));
    }
  }
  if (existsSync(root)) await walk(root);
  return out.sort();
}

function queryDatabase(dsn) {
  try {
    const sql = "SELECT lemma || '\t' || storage_path || '\t' "
      + "|| COALESCE(checksum_sha256, '') || '\t' || byte_size "
      + "FROM cet_prop_asset WHERE status = 'ACTIVE' ORDER BY lemma";
    const stdout = execFileSync("psql", [dsn, "-t", "-A", "-c", sql], { encoding: "utf8" });
    return stdout.split("\n").map((l) => l.trim()).filter(Boolean).map((line) => {
      const [lemma, storagePath, checksum, byteSize] = line.split("\t");
      return { lemma, storagePath, checksum, byteSize: Number(byteSize) };
    });
  } catch (e) {
    console.warn(`跳过数据库比对：psql 调用失败（${e.message.split("\n")[0]}）`);
    return null;
  }
}

async function main() {
  const args = parseArgs(process.argv);
  const destRoot = path.resolve(REPO_ROOT, args.dest);
  const lockPath = path.resolve(REPO_ROOT, args.lock);
  const problems = [];

  const diskFiles = await listFiles(destRoot);
  console.log(`磁盘根目录 ${destRoot}：${diskFiles.length} 个文件`);

  let lockAssets = [];
  if (existsSync(lockPath)) {
    lockAssets = JSON.parse(await readFile(lockPath, "utf8")).assets || [];
    console.log(`锁文件 ${path.relative(REPO_ROOT, lockPath)}：${lockAssets.length} 条记录`);
  } else {
    console.warn(`未找到锁文件 ${path.relative(REPO_ROOT, lockPath)}，跳过校验和比对`);
  }

  for (const asset of lockAssets) {
    const file = path.join(destRoot, asset.storagePath);
    if (!existsSync(file)) {
      problems.push(`缺文件：${asset.lemma} -> ${asset.storagePath}`);
      continue;
    }
    const buffer = await readFile(file);
    const sha256 = createHash("sha256").update(buffer).digest("hex");
    if (asset.sha256 && sha256 !== asset.sha256) {
      problems.push(`校验和不匹配：${asset.storagePath}（锁文件 ${asset.sha256.slice(0, 12)}…，磁盘 ${sha256.slice(0, 12)}…）`);
    }
    const size = (await stat(file)).size;
    if (asset.byteSize && size !== asset.byteSize) {
      problems.push(`体积不匹配：${asset.storagePath}（锁文件 ${asset.byteSize}，磁盘 ${size}）`);
    }
  }

  const dbRows = args.dsn ? queryDatabase(args.dsn) : null;
  if (dbRows) {
    console.log(`数据库 ACTIVE 行：${dbRows.length} 条`);
    const dbPaths = new Set();
    for (const row of dbRows) {
      dbPaths.add(row.storagePath);
      if (!existsSync(path.join(destRoot, row.storagePath))) {
        problems.push(`库里有行但磁盘缺文件：${row.lemma} -> ${row.storagePath}`);
      }
      if (!row.byteSize) {
        problems.push(`byte_size 为 0，未回填元数据：${row.lemma}`);
      }
    }
    for (const file of diskFiles) {
      if (!dbPaths.has(file)) {
        problems.push(`孤儿文件（库里无对应 ACTIVE 行）：${file}`);
      }
    }
  }

  if (problems.length === 0) {
    console.log("体检通过，未发现问题。");
    return;
  }
  console.error(`\n发现 ${problems.length} 个问题：`);
  problems.forEach((p) => console.error(`  - ${p}`));
  process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
