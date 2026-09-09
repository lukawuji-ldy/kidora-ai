package com.wuji.kidora.ai.cet.server.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * McpSpeechToolAdapter JSON 解析与工具查找单测。
 *
 * @author liudy
 */
class McpSpeechToolAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseAsr_success() {
        McpSpeechToolAdapter adapter = new McpSpeechToolAdapter(emptyProviders(), mapper);
        var result = adapter.parseAsr("{\"text\":\"Hello.\",\"confidence\":0.99,\"provider\":\"stub\"}");
        assertTrue(result.isPresent());
        assertEquals("Hello.", result.get().text());
        assertEquals("stub", result.get().provider());
    }

    @Test
    void parseAsr_errorEmpty() {
        McpSpeechToolAdapter adapter = new McpSpeechToolAdapter(emptyProviders(), mapper);
        assertTrue(adapter.parseAsr("{\"error\":{\"code\":\"MISSING_AUDIO\"},\"provider\":\"stub\"}").isEmpty());
    }

    @Test
    void parseTts_andScore() {
        McpSpeechToolAdapter adapter = new McpSpeechToolAdapter(emptyProviders(), mapper);
        var tts = adapter.parseTts("{\"audioBase64\":\"\",\"mimeType\":\"audio/wav\",\"provider\":\"stub\"}");
        assertTrue(tts.isPresent());
        assertEquals("audio/wav", tts.get().mimeType());
        var score = adapter.parseScore(
                "{\"overall\":85.0,\"accuracy\":88.0,\"fluency\":82.0,\"completeness\":90.0,\"provider\":\"stub\"}");
        assertTrue(score.isPresent());
        assertEquals(85.0, score.get().overall());
    }

    @Test
    void findTool_fromMcpProvider() {
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("asr_transcribe").description("asr").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return "{\"text\":\"Hi\",\"confidence\":1.0,\"provider\":\"stub\"}";
            }
        };
        ToolCallbackProvider mcpNamed = new FakeMcpToolCallbackProvider(callback);
        McpSpeechToolAdapter adapter = new McpSpeechToolAdapter(singletonProvider(mcpNamed), mapper);
        assertEquals(callback, adapter.findTool("asr_transcribe"));
        String json = adapter.callTool("asr_transcribe", "{\"audioBase64\":\"AA\"}");
        assertTrue(json.contains("\"text\":\"Hi\""));
    }

    @Test
    void isMcpProvider_detectsByClassName() {
        assertTrue(McpSpeechToolAdapter.isMcpProvider(new FakeMcpToolCallbackProvider(null)));
    }

    private static ObjectProvider<ToolCallbackProvider> emptyProviders() {
        return singletonProvider(null);
    }

    private static ObjectProvider<ToolCallbackProvider> singletonProvider(ToolCallbackProvider provider) {
        return new ObjectProvider<>() {
            @Override
            public ToolCallbackProvider getObject() {
                return provider;
            }

            @Override
            public ToolCallbackProvider getObject(Object... args) {
                return provider;
            }

            @Override
            public Stream<ToolCallbackProvider> stream() {
                return provider == null ? Stream.empty() : Stream.of(provider);
            }

            @Override
            public ToolCallbackProvider getIfAvailable() {
                return provider;
            }

            @Override
            public ToolCallbackProvider getIfUnique() {
                return provider;
            }
        };
    }

    /**
     * 类名含 McpToolCallback，满足 isMcpProvider。
     */
    static final class FakeMcpToolCallbackProvider implements ToolCallbackProvider {
        private final ToolCallback callback;

        FakeMcpToolCallbackProvider(ToolCallback callback) {
            this.callback = callback;
        }

        @Override
        public ToolCallback[] getToolCallbacks() {
            return callback == null ? new ToolCallback[0] : new ToolCallback[]{callback};
        }
    }
}
