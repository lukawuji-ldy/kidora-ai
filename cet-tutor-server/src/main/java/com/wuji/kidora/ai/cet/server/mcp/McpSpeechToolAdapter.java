package com.wuji.kidora.ai.cet.server.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.cet.core.speech.SpeechToolPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        String json = callTool("asr_transcribe", buildAsrArgs(audioBase64, locale));
        return parseAsr(json);
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
                if (cb != null && cb.getToolDefinition() != null
                        && toolName.equals(cb.getToolDefinition().name())) {
                    return cb;
                }
            }
        }
        return null;
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
        JsonNode root = read(json);
        if (root == null || root.has("error") || !root.has("text")) {
            return Optional.empty();
        }
        return Optional.of(new AsrResult(
                root.path("text").asText(""),
                root.path("confidence").asDouble(0),
                root.path("provider").asText("")));
    }

    Optional<TtsResult> parseTts(String json) {
        JsonNode root = read(json);
        if (root == null || root.has("error")) {
            return Optional.empty();
        }
        return Optional.of(new TtsResult(
                root.path("audioBase64").asText(""),
                root.path("mimeType").asText("audio/wav"),
                root.path("provider").asText("")));
    }

    Optional<PronunciationResult> parseScore(String json) {
        JsonNode root = read(json);
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

    private static String buildAsrArgs(String audioBase64, String locale) {
        StringBuilder sb = new StringBuilder("{\"audioBase64\":\"");
        sb.append(escape(audioBase64)).append("\"");
        if (StringUtils.hasText(locale)) {
            sb.append(",\"locale\":\"").append(escape(locale)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String buildTtsArgs(String text, String voice, String locale) {
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

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
