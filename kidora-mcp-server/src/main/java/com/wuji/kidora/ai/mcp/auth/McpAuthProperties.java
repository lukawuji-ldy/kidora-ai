package com.wuji.kidora.ai.mcp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP Server API Key 鉴权配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.mcp.auth")
public class McpAuthProperties {

    /**
     * 是否启用鉴权；local 默认 false，生产建议 true。
     */
    private boolean enabled = false;

    /**
     * 共享密钥，优先环境变量 {@code KIDORA_MCP_API_KEY}。
     */
    private String apiKey = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
