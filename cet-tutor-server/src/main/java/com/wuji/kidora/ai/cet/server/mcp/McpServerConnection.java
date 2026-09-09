package com.wuji.kidora.ai.cet.server.mcp;

/**
 * MCP Server 连接（对齐 mcp_server_ref）。
 *
 * @param serverId 业务键
 * @param name     展示名
 * @param baseUrl  基址
 * @param authType NONE|BEARER
 * @param status   ACTIVE|INACTIVE
 * @author liudy
 */
public record McpServerConnection(
        String serverId,
        String name,
        String baseUrl,
        String authType,
        String status
) {
    public boolean bearer() {
        return "BEARER".equalsIgnoreCase(authType);
    }
}
