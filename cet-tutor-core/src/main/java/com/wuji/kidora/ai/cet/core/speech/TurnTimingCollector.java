package com.wuji.kidora.ai.cet.core.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 一轮陪练墙钟分段计时累加器（schemaVersion=1）。
 *
 * @author liudy
 */
public final class TurnTimingCollector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final long t0Nanos;
    private final String kind;
    private final String path;

    private long asrMs;
    private long safetyInMs;
    private long tutorMs;
    private long safetyOutMs;
    private long ttsMs;
    private long scoreMs;
    private long persistMs;
    private long stageEvalMs;
    private Long ttsReadyMs;

    private boolean skippedAsr = true;
    private boolean skippedTts = true;
    private boolean skippedScore = true;
    private boolean skippedSafetyInModel = true;
    private boolean skippedSafetyOutModel = true;
    private boolean skippedStageEval = true;

    /**
     * @param kind opening | turn
     * @param path voice | text
     */
    public TurnTimingCollector(String kind, String path) {
        this.t0Nanos = System.nanoTime();
        this.kind = kind == null ? "turn" : kind;
        this.path = path == null ? "text" : path;
    }

    /**
     * 测量一段同步调用。
     *
     * @param runnable 调用
     * @return 毫秒（四舍五入）
     */
    public static long measureMs(Runnable runnable) {
        long start = System.nanoTime();
        runnable.run();
        return Math.round((System.nanoTime() - start) / 1_000_000.0);
    }

    /**
     * Safety 是否跳过模型：L0_* 为 true。
     *
     * @param eventType SafetyDecision.eventType
     * @return skip model
     */
    public static boolean safetyModelSkipped(String eventType) {
        return eventType != null && eventType.startsWith("L0_");
    }

    public void addAsrMs(long ms, boolean skipped) {
        this.asrMs += ms;
        this.skippedAsr = skipped;
    }

    public void addSafetyInMs(long ms, boolean modelSkipped) {
        this.safetyInMs += ms;
        this.skippedSafetyInModel = modelSkipped;
    }

    public void addSafetyOutMs(long ms, boolean modelSkipped) {
        this.safetyOutMs += ms;
        this.skippedSafetyOutModel = modelSkipped;
    }

    public void addTutorMs(long ms) {
        this.tutorMs += ms;
    }

    public void addPersistMs(long ms) {
        this.persistMs += ms;
    }

    public void addTtsMs(long ms, boolean skipped) {
        this.ttsMs += ms;
        this.skippedTts = skipped;
    }

    public void addScoreMs(long ms, boolean skipped) {
        this.scoreMs += ms;
        this.skippedScore = skipped;
    }

    public void addStageEvalMs(long ms, boolean skipped) {
        this.stageEvalMs += ms;
        this.skippedStageEval = skipped;
    }

    /** 标记 TTS 就绪（或无 TTS 时字幕可展示完成）。 */
    public void markTtsReady() {
        this.ttsReadyMs = elapsedMs();
    }

    /**
     * @return 已标记的 ttsReadyMs；未标记返回 -1
     */
    public long ttsReadyMsOrMinusOne() {
        return ttsReadyMs == null ? -1L : ttsReadyMs;
    }

    /** @return 累计 scoreMs */
    public long scoreMs() {
        return scoreMs;
    }

    public long elapsedMs() {
        return Math.round((System.nanoTime() - t0Nanos) / 1_000_000.0);
    }

    /**
     * 组装落库/SSE JSON（client 字段为 null）。
     *
     * @return JSON 字符串
     */
    public String toJson() {
        if (ttsReadyMs == null) {
            markTtsReady();
        }
        long serverTotalMs = elapsedMs();
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("schemaVersion", 1);
            root.put("kind", kind);
            root.put("path", path);
            root.put("asrMs", asrMs);
            root.put("safetyInMs", safetyInMs);
            root.put("tutorMs", tutorMs);
            root.put("safetyOutMs", safetyOutMs);
            root.put("ttsMs", ttsMs);
            root.put("scoreMs", scoreMs);
            root.put("persistMs", persistMs);
            root.put("stageEvalMs", stageEvalMs);
            root.put("serverTotalMs", serverTotalMs);
            root.put("ttsReadyMs", ttsReadyMs);
            ObjectNode skipped = root.putObject("skipped");
            skipped.put("asr", skippedAsr);
            skipped.put("tts", skippedTts);
            skipped.put("score", skippedScore);
            skipped.put("safetyInModel", skippedSafetyInModel);
            skipped.put("safetyOutModel", skippedSafetyOutModel);
            skipped.put("stageEval", skippedStageEval);
            ObjectNode client = root.putObject("client");
            client.putNull("e2eHeardMs");
            client.putNull("reportedAt");
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"schemaVersion\":1,\"kind\":\"" + kind + "\",\"path\":\"" + path + "\"}";
        }
    }
}
