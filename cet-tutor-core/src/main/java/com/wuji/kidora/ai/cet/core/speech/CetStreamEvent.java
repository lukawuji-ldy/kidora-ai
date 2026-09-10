package com.wuji.kidora.ai.cet.core.speech;

/**
 * CET stream 事件（映射到 SSE event 名）。
 *
 * @param type 类型
 * @param data JSON 或文本块
 * @author liudy
 */
public record CetStreamEvent(Type type, String data) {

    /**
     * 事件类型。
     */
    public enum Type {
        DELTA,
        TTS,
        PRONUNCIATION,
        PLAN_UPDATED
    }

    /**
     * 文本增量。
     *
     * @param chunk 文本
     * @return 事件
     */
    public static CetStreamEvent delta(String chunk) {
        return new CetStreamEvent(Type.DELTA, chunk == null ? "" : chunk);
    }

    /**
     * TTS 音频 JSON。
     *
     * @param json payload
     * @return 事件
     */
    public static CetStreamEvent tts(String json) {
        return new CetStreamEvent(Type.TTS, json == null ? "{}" : json);
    }

    /**
     * 发音评分 JSON。
     *
     * @param json payload
     * @return 事件
     */
    public static CetStreamEvent pronunciation(String json) {
        return new CetStreamEvent(Type.PRONUNCIATION, json == null ? "{}" : json);
    }

    /**
     * 计划已修订。
     *
     * @param json payload（planId/version/childSummary）
     * @return 事件
     */
    public static CetStreamEvent planUpdated(String json) {
        return new CetStreamEvent(Type.PLAN_UPDATED, json == null ? "{}" : json);
    }
}
