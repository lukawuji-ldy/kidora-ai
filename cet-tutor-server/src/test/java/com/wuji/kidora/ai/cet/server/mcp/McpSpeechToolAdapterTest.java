package com.wuji.kidora.ai.cet.server.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void parseAsr_unwrapsMcpContentList() {
        McpSpeechToolAdapter adapter = new McpSpeechToolAdapter(emptyProviders(), mapper);
        String wrapped = "[{\"type\":\"text\",\"text\":\"{\\\"text\\\":\\\"Hello.\\\",\\\"confidence\\\":0.99,\\\"provider\\\":\\\"stub\\\"}\"}]";
        var result = adapter.parseAsr(wrapped);
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
    void extractErrorMessage_fromVendorPayload() {
        McpSpeechToolAdapter adapter = new McpSpeechToolAdapter(emptyProviders(), mapper);
        assertEquals("MISSING_AUDIO: audio required",
                adapter.extractErrorMessage(
                        "{\"error\":{\"code\":\"MISSING_AUDIO\",\"message\":\"audio required\"},\"provider\":\"stub\"}"));
    }

    @Test
    void preview_truncates() {
        assertEquals("abcdef...", McpSpeechToolAdapter.preview("abcdefghij", 6));
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
    void parseScore_unwrapsMcpContentList() {
        McpSpeechToolAdapter adapter = new McpSpeechToolAdapter(emptyProviders(), mapper);
        String wrapped = "[{\"type\":\"text\",\"text\":\"{\\\"overall\\\":85.0,\\\"accuracy\\\":88.0,\\\"fluency\\\":82.0,\\\"completeness\\\":90.0,\\\"provider\\\":\\\"stub\\\"}\"}]";
        var score = adapter.parseScore(wrapped);
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
    void findTool_matchesPrefixedDefinitionName() {
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("kidoramcpserver_asr_transcribe")
                        .description("asr")
                        .inputSchema("{}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "[]";
            }
        };
        ToolCallbackProvider mcpNamed = new FakeMcpToolCallbackProvider(callback);
        McpSpeechToolAdapter adapter = new McpSpeechToolAdapter(singletonProvider(mcpNamed), mapper);
        assertEquals(callback, adapter.findTool("asr_transcribe"));
    }

    @Test
    void matchesTool_exactAndSuffix() {
        ToolCallback exact = namedCallback("asr_transcribe");
        ToolCallback prefixed = namedCallback("client_asr_transcribe");
        assertTrue(McpSpeechToolAdapter.matchesTool(exact, "asr_transcribe"));
        assertTrue(McpSpeechToolAdapter.matchesTool(prefixed, "asr_transcribe"));
        assertFalse(McpSpeechToolAdapter.matchesTool(exact, "tts_synthesize"));
    }

    @Test
    void isMcpProvider_detectsByClassName() {
        assertTrue(McpSpeechToolAdapter.isMcpProvider(new FakeMcpToolCallbackProvider(null)));
    }

    @Test
    void escape_encodesNewlinesAndQuotes() {
        assertEquals("a\\nb", McpSpeechToolAdapter.escape("a\nb"));
        assertEquals("a\\rb\\tc", McpSpeechToolAdapter.escape("a\rb\tc"));
        assertEquals("say \\\"hi\\\"", McpSpeechToolAdapter.escape("say \"hi\""));
        assertEquals("a\\\\b", McpSpeechToolAdapter.escape("a\\b"));
    }

    @Test
    void buildTtsArgs_multilineChineseIsValidJson() throws Exception {
        String text = "Hi! I'm Emma. Good evening!\n你好！今天我们来聊聊动物。\nDo you have a pet?";
        String json = McpSpeechToolAdapter.buildTtsArgs(text, "602005", null);
        var node = mapper.readTree(json);
        assertEquals(text, node.path("text").asText());
        assertEquals("602005", node.path("voice").asText());
        assertFalse(json.contains("\n"));
    }

    private static ToolCallback namedCallback(String name) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description("d").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return "{}";
            }
        };
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
