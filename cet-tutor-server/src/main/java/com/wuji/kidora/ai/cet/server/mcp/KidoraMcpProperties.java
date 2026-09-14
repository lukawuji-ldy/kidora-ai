package com.wuji.kidora.ai.cet.server.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CET MCP Client 配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.mcp")
public class KidoraMcpProperties {

    /**
     * 是否启用 MCP Client（本地默认 true，配合 Web 录音；无 mcp-server 时仍可启动）。
     */
    private boolean enabled = true;

    /**
     * 空库兜底 base URL。
     */
    private String serverUrl = "http://127.0.0.1:8081";

    /**
     * SSE 端点。
     */
    private String sseEndpoint = "/sse";

    private final Auth auth = new Auth();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getSseEndpoint() {
        return sseEndpoint;
    }

    public void setSseEndpoint(String sseEndpoint) {
        this.sseEndpoint = sseEndpoint;
    }

    public Auth getAuth() {
        return auth;
    }

    /**
     * Bearer 鉴权（与 mcp-server kidora.mcp.auth 对齐；密钥走环境变量）。
     */
    public static class Auth {

        private boolean enabled = false;

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
}
