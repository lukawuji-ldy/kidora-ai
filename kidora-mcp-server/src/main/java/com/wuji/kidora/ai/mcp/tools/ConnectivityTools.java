package com.wuji.kidora.ai.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * MCP 连通性工具。
 *
 * @author liudy
 */
@Service
public class ConnectivityTools {

    /**
     * 连通性检查。
     *
     * @param message 回显内容
     * @return JSON：echo + ts
     */
    @Tool(name = "echo_ping", description = "Connectivity check; echoes the input message with timestamp")
    public String echoPing(@ToolParam(description = "Message to echo") String message) {
        String safe = message == null ? "" : message;
        return "{\"echo\":\"" + jsonEscape(safe) + "\",\"ts\":\"" + Instant.now() + "\"}";
    }

    private static String jsonEscape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
