package com.wuji.kidora.ai.common.util;

/**
 * PostgreSQL 文本 / JSONB 入参清洗。
 *
 * @author liudy
 */
public final class PostgresText {

    private PostgresText() {
    }

    public static String sanitize(String text) {
        if (text == null || text.indexOf('\u0000') < 0) {
            return text;
        }
        return text.replace("\u0000", "");
    }

    public static String sanitizeJson(String json) {
        if (json == null) {
            return null;
        }
        String cleaned = sanitize(json);
        if (cleaned.contains("\\u0000")) {
            cleaned = cleaned.replace("\\u0000", "");
        }
        return cleaned;
    }
}
