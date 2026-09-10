package com.wuji.kidora.ai.mcp.config;

import com.wuji.kidora.ai.mcp.tools.ConnectivityTools;
import com.wuji.kidora.ai.mcp.tools.DictionaryTools;
import com.wuji.kidora.ai.mcp.tools.SpeechTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server Tool 装配。
 *
 * @author liudy
 */
@Configuration
public class McpServerConfiguration {

    /**
     * 注册连通性工具。
     *
     * @param connectivityTools 工具服务
     * @return ToolCallbackProvider
     */
    @Bean
    public ToolCallbackProvider connectivityToolProvider(ConnectivityTools connectivityTools) {
        return MethodToolCallbackProvider.builder().toolObjects(connectivityTools).build();
    }

    /**
     * 注册语音 stub 工具（ASR / TTS / 发音）。
     *
     * @param speechTools 工具服务
     * @return ToolCallbackProvider
     */
    @Bean
    public ToolCallbackProvider speechToolProvider(SpeechTools speechTools) {
        return MethodToolCallbackProvider.builder().toolObjects(speechTools).build();
    }

    /**
     * 注册词典工具。
     *
     * @param dictionaryTools 工具服务
     * @return ToolCallbackProvider
     */
    @Bean
    public ToolCallbackProvider dictionaryToolProvider(DictionaryTools dictionaryTools) {
        return MethodToolCallbackProvider.builder().toolObjects(dictionaryTools).build();
    }
}
