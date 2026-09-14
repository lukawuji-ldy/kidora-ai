/**
 * 道具素材导入/体检共用逻辑：许可白名单、清单展开、文件校验与像素尺寸解析。
 * 零第三方依赖，Node 18+（依赖全局 fetch）。
 *
 * @author liudy
 */

import { createHash } from "node:crypto";

/** 只允许无版权风险的协议进库；与 ck_cet_prop_asset_license 保持一致。 */
export const ALLOWED_LICENSES = {
  "CC0-1.0": {
    attributionRequired: false,
    url: "https://creativecommons.org/publicdomain/zero/1.0/",
  },
  PD: {
    attributionRequired: false,
    url: "https://en.wikipedia.org/wiki/Public_domain",
  },
  "CC-BY-4.0": {
    attributionRequired: true,
    url: "https://creativecommons.org/licenses/by/4.0/",
  },
  "CC-BY-SA-4.0": {
    attributionRequired: true,
    url: "https://creativecommons.org/licenses/by-sa/4.0/",
  },
  PIXABAY: {
    attributionRequired: false,
    url: "https://pixabay.com/service/license-summary/",
  },
};

/** 与 ck_cet_prop_asset_source 保持一致。 */
export const ALLOWED_SOURCES = new Set([
  "openmoji",
  "openclipart",
  "wikimedia",
  "pixabay",
  "kenney",
  "local",
  "generated",
]);

export const LIMITS = {
  maxBytes: 512 * 1024,
  minPixels: 64,
  maxPixels: 2048,
};

const EXTENSION_BY_TYPE = {
  "image/png": "png",
  "image/webp": "webp",
  "image/jpeg": "jpg",
  "image/svg+xml": "svg",
};

/**
 * 把 manifest 展开为完整条目，逐条做许可与字段校验。
 *
 * @returns {{items: object[], errors: string[]}}
 */
export function expandManifest(manifest) {
  const errors = [];
  const items = [];
  const defaults = manifest.defaults || {};
  const idBase = Number(manifest.idBase);
  if (!Number.isInteger(idBase) || idBase <= 0) {
    errors.push("manifest.idBase 必须是正整数");
  }
  const seenLemmas = new Set();

  (manifest.items || []).forEach((raw, index) => {
    const where = `items[${index}] (${raw.lemma || "?"})`;
    const lemma = String(raw.lemma || "").trim().toLowerCase();
    if (!/^[a-z][a-z0-9_-]{0,63}$/.test(lemma)) {
      errors.push(`${where}: lemma 必须是小写英文，实际 "${raw.lemma}"`);
      return;
    }
    if (seenLemmas.has(lemma)) {
      errors.push(`${where}: lemma 重复`);
      return;
    }
    seenLemmas.add(lemma);

    const sourceCode = raw.sourceCode || defaults.sourceCode;
    const licenseCode = raw.licenseCode || defaults.licenseCode;
    if (!ALLOWED_SOURCES.has(sourceCode)) {
      errors.push(`${where}: 未知来源 "${sourceCode}"`);
      return;
    }
    const license = ALLOWED_LICENSES[licenseCode];
    if (!license) {
      errors.push(
        `${where}: 许可 "${licenseCode}" 不在白名单 ${Object.keys(ALLOWED_LICENSES).join("/")}，拒绝入库`,
      );
      return;
    }
    const author = raw.author || defaults.author || "";
    if (license.attributionRequired && !author) {
      errors.push(`${where}: ${licenseCode} 需要署名，author 不能为空`);
      return;
    }
    const downloadUrl = raw.downloadUrl || fill(defaults.downloadUrlTemplate, raw);
    const sourceUrl = raw.sourceUrl || fill(defaults.sourceUrlTemplate, raw);
    if (!downloadUrl) {
      errors.push(`${where}: 缺少 downloadUrl，且 defaults.downloadUrlTemplate 未配置`);
      return;
    }

    items.push({
      id: idBase + index,
      lemma,
      theme: raw.theme || "default",
      aliases: normalizeAliases(raw.aliases),
      sourceCode,
      licenseCode,
      licenseUrl: raw.licenseUrl || license.url,
      author,
      sourceUrl: sourceUrl || "",
      attributionRequired: license.attributionRequired,
      downloadUrl,
    });
  });

  return { items, errors };
}

function fill(template, raw) {
  if (!template) return "";
  return template.replace(/\{(\w+)\}/g, (_, key) => String(raw[key] ?? ""));
}

function normalizeAliases(aliases) {
  const out = [];
  for (const alias of aliases || []) {
    const value = String(alias || "").trim().toLowerCase();
    if (value && !out.includes(value)) out.push(value);
  }
  return out;
}

/**
 * 按魔数判定真实类型，防止把 HTML 错误页当成图片落盘。
 *
 * @returns {string|null} MIME
 */
export function sniffContentType(buffer) {
  if (buffer.length >= 8 && buffer.subarray(0, 8).equals(
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) {
    return "image/png";
  }
  if (buffer.length >= 12
    && buffer.subarray(0, 4).toString("latin1") === "RIFF"
    && buffer.subarray(8, 12).toString("latin1") === "WEBP") {
    return "image/webp";
  }
  if (buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return "image/jpeg";
  }
  const head = buffer.subarray(0, 512).toString("utf8").trimStart();
  if (head.startsWith("<?xml") || head.startsWith("<svg")) {
    return head.includes("<svg") ? "image/svg+xml" : null;
  }
  return null;
}

export function extensionFor(contentType) {
  return EXTENSION_BY_TYPE[contentType] || null;
}

/**
 * 读取像素尺寸；矢量图或无法解析时返回 0。
 *
 * @returns {{width: number, height: number}}
 */
export function readDimensions(buffer, contentType) {
  try {
    if (contentType === "image/png") {
      return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) };
    }
    if (contentType === "image/webp") {
      return readWebpDimensions(buffer);
    }
    if (contentType === "image/jpeg") {
      return readJpegDimensions(buffer);
    }
    if (contentType === "image/svg+xml") {
      return readSvgDimensions(buffer.toString("utf8"));
    }
  } catch {
    // 尺寸只用于体检报告，解析失败按未知处理
  }
  return { width: 0, height: 0 };
}

function readWebpDimensions(buffer) {
  const format = buffer.subarray(12, 16).toString("latin1");
  if (format === "VP8X") {
    return {
      width: 1 + (buffer[24] | (buffer[25] << 8) | (buffer[26] << 16)),
      height: 1 + (buffer[27] | (buffer[28] << 8) | (buffer[29] << 16)),
    };
  }
  if (format === "VP8L") {
    const bits = buffer.readUInt32LE(21);
    return { width: 1 + (bits & 0x3fff), height: 1 + ((bits >> 14) & 0x3fff) };
  }
  if (format === "VP8 ") {
    return {
      width: buffer.readUInt16LE(26) & 0x3fff,
      height: buffer.readUInt16LE(28) & 0x3fff,
    };
  }
  return { width: 0, height: 0 };
}

function readJpegDimensions(buffer) {
  let offset = 2;
  while (offset < buffer.length - 9) {
    if (buffer[offset] !== 0xff) {
      offset += 1;
      continue;
    }
    const marker = buffer[offset + 1];
    const isSof = marker >= 0xc0 && marker <= 0xcf
      && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;
    if (isSof) {
      return { height: buffer.readUInt16BE(offset + 5), width: buffer.readUInt16BE(offset + 7) };
    }
    offset += 2 + buffer.readUInt16BE(offset + 2);
  }
  return { width: 0, height: 0 };
}

function readSvgDimensions(text) {
  const tag = text.slice(text.indexOf("<svg"), text.indexOf(">", text.indexOf("<svg")) + 1);
  const viewBox = /viewBox\s*=\s*"([^"]+)"/i.exec(tag);
  if (viewBox) {
    const parts = viewBox[1].trim().split(/[\s,]+/).map(Number);
    if (parts.length === 4 && parts.every(Number.isFinite)) {
      return { width: Math.round(parts[2]), height: Math.round(parts[3]) };
    }
  }
  const width = /\bwidth\s*=\s*"(\d+)/i.exec(tag);
  const height = /\bheight\s*=\s*"(\d+)/i.exec(tag);
  return { width: width ? Number(width[1]) : 0, height: height ? Number(height[1]) : 0 };
}

/**
 * SVG 会被浏览器直接渲染，拒绝可执行内容。
 *
 * @returns {string|null} 拒绝原因；通过返回 null
 */
export function rejectUnsafeSvg(buffer) {
  const text = buffer.toString("utf8").toLowerCase();
  const banned = ["<script", "<foreignobject", "javascript:", "onload=", "onerror=", "<iframe"];
  const hit = banned.find((token) => text.includes(token));
  return hit ? `SVG 含可执行内容 "${hit}"` : null;
}

/**
 * 通用文件校验：体积、类型、尺寸、SVG 安全。
 *
 * @returns {{contentType: string, width: number, height: number, sha256: string, errors: string[]}}
 */
export function inspectAsset(buffer) {
  const errors = [];
  const contentType = sniffContentType(buffer);
  if (!contentType) {
    errors.push("无法识别的文件类型（可能下载到了 HTML 错误页）");
    return { contentType: null, width: 0, height: 0, sha256: "", errors };
  }
  if (buffer.length > LIMITS.maxBytes) {
    errors.push(`体积 ${buffer.length} 超过上限 ${LIMITS.maxBytes}`);
  }
  if (contentType === "image/svg+xml") {
    const unsafe = rejectUnsafeSvg(buffer);
    if (unsafe) errors.push(unsafe);
  }
  const { width, height } = readDimensions(buffer, contentType);
  const measurable = width > 0 && height > 0;
  if (measurable && (width < LIMITS.minPixels || height < LIMITS.minPixels)) {
    errors.push(`尺寸 ${width}x${height} 小于 ${LIMITS.minPixels}px，投屏会糊`);
  }
  if (measurable && (width > LIMITS.maxPixels || height > LIMITS.maxPixels)) {
    errors.push(`尺寸 ${width}x${height} 超过 ${LIMITS.maxPixels}px`);
  }
  return {
    contentType,
    width,
    height,
    sha256: createHash("sha256").update(buffer).digest("hex"),
    errors,
  };
}

/** PostgreSQL 字符串字面量转义。 */
export function sqlLiteral(value) {
  if (value === null || value === undefined || value === "") return "NULL";
  return `'${String(value).replace(/'/g, "''")}'`;
}
