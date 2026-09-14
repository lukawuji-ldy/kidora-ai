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
        ASR,
        TTS,
        PRONUNCIATION,
        PLAN_UPDATED,
        TIMING,
        WRAPUP,
        SESSION_COMPLETED,
        PROP
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
     * ASR 识别结果 JSON。
     *
     * @param json payload
     * @return 事件
     */
    public static CetStreamEvent asr(String json) {
        return new CetStreamEvent(Type.ASR, json == null ? "{}" : json);
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

    /**
     * 一轮分段耗时 JSON（schemaVersion=1）。
     *
     * @param json payload
     * @return 事件
     */
    public static CetStreamEvent timing(String json) {
        return new CetStreamEvent(Type.TIMING, json == null ? "{}" : json);
    }

    /**
     * 告别阶段进度 JSON。
     *
     * @param json payload（phase/step）
     * @return 事件
     */
    public static CetStreamEvent wrapUp(String json) {
        return new CetStreamEvent(Type.WRAPUP, json == null ? "{}" : json);
    }

    /**
     * 会话已结课 JSON。
     *
     * @param json payload（sessionId/status/childSummary）
     * @return 事件
     */
    public static CetStreamEvent sessionCompleted(String json) {
        return new CetStreamEvent(Type.SESSION_COMPLETED, json == null ? "{}" : json);
    }

    /**
     * 本轮教具舞台 JSON（layout/activeLemma/assets），由后端按外教文本判定。
     *
     * @param json payload
     * @return 事件
     */
    public static CetStreamEvent prop(String json) {
        return new CetStreamEvent(Type.PROP, json == null ? "{}" : json);
    }
}
