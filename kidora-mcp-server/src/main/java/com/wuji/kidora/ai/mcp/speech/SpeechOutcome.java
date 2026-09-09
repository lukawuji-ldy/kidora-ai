package com.wuji.kidora.ai.mcp.speech;

/**
 * 语音调用结果：成功 payload 或结构化错误。
 *
 * @author liudy
 */
public record SpeechOutcome(boolean success, String jsonBody) {

    /**
     * 成功结果。
     *
     * @param jsonBody 成功 JSON（不含外层包装）
     * @return outcome
     */
    public static SpeechOutcome ok(String jsonBody) {
        return new SpeechOutcome(true, jsonBody);
    }

    /**
     * 错误结果。
     *
     * @param code       错误码
     * @param message    说明
     * @param providerId 供应商
     * @return outcome
     */
    public static SpeechOutcome error(String code, String message, String providerId) {
        return new SpeechOutcome(false, "{\"error\":{\"code\":\"" + escape(code)
                + "\",\"message\":\"" + escape(message) + "\"},\"provider\":\""
                + escape(providerId) + "\"}");
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
