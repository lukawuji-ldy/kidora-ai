package com.wuji.kidora.ai.cet.server.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.kidora.ai.cet.core.speech.SpeechToolPort;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 经 MCP Client ToolCallback 调用 ASR/TTS/发音工具。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(prefix = "kidora.mcp", name = "enabled", havingValue = "true")
public class McpSpeechToolAdapter implements SpeechToolPort {

    private static final Logger log = LoggerFactory.getLogger(McpSpeechToolAdapter.class);

    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;
    private final ObjectMapper objectMapper;

    public McpSpeechToolAdapter(ObjectProvider<ToolCallbackProvider> toolCallbackProviders,
                                ObjectMapper objectMapper) {
        this.toolCallbackProviders = toolCallbackProviders;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AsrResult> asr(String audioBase64, String locale) {
        int audioBytes = estimateAudioBytes(audioBase64);
        String json = callTool("asr_transcribe", buildAsrArgs(audioBase64, locale));
        Optional<AsrResult> parsed = parseAsr(json);
        if (parsed.isEmpty()) {
            String err = extractErrorMessage(json);
            if (StringUtils.hasText(err)) {
                log.warn("ASR tool returned error (audioBytes≈{}): {}", audioBytes, err);
                throw new KidoraException(ErrorCode.BAD_REQUEST, "ASR失败: " + err);
            }
            log.warn("ASR empty transcript (audioBytes≈{}); payloadPreview={}",
                    audioBytes, preview(json, 240));
            throw new KidoraException(ErrorCode.BAD_REQUEST,
                    "ASR 未能识别语音（音频约 " + audioBytes + " 字节）：请再说长一点，或改用打字");
        }
        return parsed;
    }

    @Override
    public Optional<TtsResult> tts(String text, String voice, String locale) {
        String json = callTool("tts_synthesize", buildTtsArgs(text, voice, locale));
        return parseTts(json);
    }

    @Override
    public Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale) {
        String json = callTool("pronunciation_score", buildScoreArgs(audioBase64, referenceText, locale));
        return parseScore(json);
    }

    String callTool(String toolName, String argsJson) {
        ToolCallback callback = findTool(toolName);
        if (callback == null) {
            log.warn("MCP tool not found: {}", toolName);
            return null;
        }
        try {
            return callback.call(argsJson);
        } catch (Exception e) {
            log.warn("MCP tool {} failed: {}", toolName, e.getMessage());
            return null;
        }
    }

    ToolCallback findTool(String toolName) {
        for (ToolCallbackProvider provider : toolCallbackProviders) {
            if (provider == null || !isMcpProvider(provider)) {
                continue;
            }
            ToolCallback[] callbacks;
            try {
                callbacks = provider.getToolCallbacks();
            } catch (Exception e) {
                log.debug("Skip MCP provider {}: {}", provider.getClass().getSimpleName(), e.getMessage());
                continue;
            }
            if (callbacks == null) {
                continue;
            }
            for (ToolCallback cb : callbacks) {
                if (matchesTool(cb, toolName)) {
                    return cb;
                }
            }
        }
        return null;
    }

    /**
     * Spring AI 可能给工具名加 client 前缀；优先匹配 {@link SyncMcpToolCallback#getOriginalToolName()}。
     */
    static boolean matchesTool(ToolCallback cb, String toolName) {
        if (cb == null || !StringUtils.hasText(toolName)) {
            return false;
        }
        if (cb instanceof SyncMcpToolCallback sync) {
            if (toolName.equals(sync.getOriginalToolName())) {
                return true;
            }
        }
        if (cb.getToolDefinition() == null || !StringUtils.hasText(cb.getToolDefinition().name())) {
            return false;
        }
        String name = cb.getToolDefinition().name();
        return toolName.equals(name) || name.endsWith("_" + toolName) || name.endsWith(toolName);
    }

    static boolean isMcpProvider(ToolCallbackProvider provider) {
        Class<?> type = provider.getClass();
        while (type != null && type != Object.class) {
            String name = type.getName();
            if (name.startsWith("org.springframework.ai.mcp") || name.contains("McpToolCallback")) {
                return true;
            }
            type = type.getSuperclass();
        }
        for (Class<?> iface : provider.getClass().getInterfaces()) {
            String name = iface.getName();
            if (name.startsWith("org.springframework.ai.mcp") || name.contains("McpToolCallback")) {
                return true;
            }
        }
        return false;
    }

    Optional<AsrResult> parseAsr(String json) {
        JsonNode root = unwrapToolPayload(json);
        if (root == null || root.has("error") || !root.has("text")) {
            return Optional.empty();
        }
        String text = root.path("text").asText("");
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }
        return Optional.of(new AsrResult(
                text,
                root.path("confidence").asDouble(0),
                root.path("provider").asText("")));
    }

    Optional<TtsResult> parseTts(String json) {
        JsonNode root = unwrapToolPayload(json);
        if (root == null || root.has("error")) {
            return Optional.empty();
        }
        return Optional.of(new TtsResult(
                root.path("audioBase64").asText(""),
                root.path("mimeType").asText("audio/wav"),
                root.path("provider").asText("")));
    }

    Optional<PronunciationResult> parseScore(String json) {
        JsonNode root = unwrapToolPayload(json);
        if (root == null || root.has("error") || !root.has("overall")) {
            return Optional.empty();
        }
        return Optional.of(new PronunciationResult(
                root.path("overall").asDouble(0),
                root.path("accuracy").asDouble(0),
                root.path("fluency").asDouble(0),
                root.path("completeness").asDouble(0),
                root.path("provider").asText("")));
    }

    /**
     * SyncMcpToolCallback.call 返回 CallToolResult.content 的 JSON 数组，需解出内层业务 JSON。
     */
    JsonNode unwrapToolPayload(String raw) {
        JsonNode root = read(raw);
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            for (JsonNode item : root) {
                JsonNode inner = unwrapTextContentItem(item);
                if (inner != null) {
                    return inner;
                }
            }
            return null;
        }
        JsonNode maybeWrapped = unwrapTextContentItem(root);
        return maybeWrapped != null ? maybeWrapped : root;
    }

    private JsonNode unwrapTextContentItem(JsonNode item) {
        if (item == null || !item.isObject() || !item.has("text")) {
            return null;
        }
        // 已是业务 JSON（含 confidence / provider / overall 等）则不再当 MCP 包装解
        if (item.has("confidence") || item.has("provider") || item.has("overall")
                || item.has("audioBase64") || item.has("error")) {
            return null;
        }
        boolean mcpText = "text".equals(item.path("type").asText(null))
                || item.has("annotations")
                || item.has("meta");
        String text = item.path("text").asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        boolean nestedJson = trimmed.startsWith("{") || trimmed.startsWith("[");
        if (!mcpText && !nestedJson) {
            return null;
        }
        if (nestedJson) {
            JsonNode parsed = read(trimmed);
            if (parsed != null) {
                return parsed;
            }
        }
        if (mcpText) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("text", text);
            return fallback;
        }
        return null;
    }

    private JsonNode read(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Invalid MCP tool JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从工具返回体取出 error.message（兼容 MCP content 包装）。
     */
    String extractErrorMessage(String raw) {
        JsonNode root = unwrapToolPayload(raw);
        if (root == null || !root.has("error")) {
            return null;
        }
        JsonNode err = root.get("error");
        if (err == null || err.isNull()) {
            return null;
        }
        if (err.isTextual()) {
            return err.asText();
        }
        String message = err.path("message").asText("");
        String code = err.path("code").asText("");
        if (StringUtils.hasText(code) && StringUtils.hasText(message)) {
            return code + ": " + message;
        }
        if (StringUtils.hasText(message)) {
            return message;
        }
        if (StringUtils.hasText(code)) {
            return code;
        }
        return err.toString();
    }

    static String preview(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace('\n', ' ').replace('\r', ' ');
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max)) + "...";
    }

    static int estimateAudioBytes(String audioBase64) {
        if (!StringUtils.hasText(audioBase64)) {
            return 0;
        }
        // base64 长度约 = 4/3 * bytes；忽略 padding 误差
        return Math.max(0, audioBase64.trim().length() * 3 / 4);
    }

    private static String buildAsrArgs(String audioBase64, String locale) {
        StringBuilder sb = new StringBuilder("{\"audioBase64\":\"");
        sb.append(escape(audioBase64)).append("\"");
        if (StringUtils.hasText(locale)) {
            sb.append(",\"locale\":\"").append(escape(locale)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    static String buildTtsArgs(String text, String voice, String locale) {
        StringBuilder sb = new StringBuilder("{\"text\":\"");
        sb.append(escape(text)).append("\"");
        if (StringUtils.hasText(voice)) {
            sb.append(",\"voice\":\"").append(escape(voice)).append("\"");
        }
        if (StringUtils.hasText(locale)) {
            sb.append(",\"locale\":\"").append(escape(locale)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String buildScoreArgs(String audioBase64, String referenceText, String locale) {
        StringBuilder sb = new StringBuilder("{\"audioBase64\":\"");
        sb.append(escape(audioBase64)).append("\",\"referenceText\":\"")
                .append(escape(referenceText)).append("\"");
        if (StringUtils.hasText(locale)) {
            sb.append(",\"locale\":\"").append(escape(locale)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * JSON 字符串转义（含换行等控制字符，避免开场多行正文弄坏 MCP 工具入参）。
     *
     * @param raw 原文，可空
     * @return 可嵌入 JSON 双引号的片段
     */
    static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
