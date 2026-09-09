package com.wuji.kidora.ai.cet.server.mcp;

import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 按连接行构建 SSE {@link NamedClientMcpTransport}。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(prefix = "kidora.mcp", name = "enabled", havingValue = "true")
public class McpSseTransportFactory {

    private static final Logger log = LoggerFactory.getLogger(McpSseTransportFactory.class);

    private final KidoraMcpProperties mcpProperties;
    private final ObjectMapper objectMapper;

    public McpSseTransportFactory(KidoraMcpProperties mcpProperties, ObjectMapper objectMapper) {
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 从库表连接构建 Transport。
     *
     * @param conn 连接
     * @return Named transport
     */
    public NamedClientMcpTransport build(McpServerConnection conn) {
        WebClient.Builder builder = WebClient.builder().baseUrl(conn.baseUrl());
        if (conn.bearer()) {
            applyBearer(builder);
        }
        String endpoint = StringUtils.hasText(mcpProperties.getSseEndpoint())
                ? mcpProperties.getSseEndpoint().trim() : "/sse";
        var transport = new WebFluxSseClientTransport(
                builder, new JacksonMcpJsonMapper(objectMapper), endpoint);
        log.info("MCP SSE transport ready serverId={} url={} authType={}",
                conn.serverId(), conn.baseUrl(), conn.authType());
        return new NamedClientMcpTransport(conn.serverId(), transport);
    }

    /**
     * yml 空库兜底。
     *
     * @return Named transport
     */
    public NamedClientMcpTransport buildFromYmlFallback() {
        String url = mcpProperties.getServerUrl();
        WebClient.Builder builder = WebClient.builder().baseUrl(url);
        if (mcpProperties.getAuth() != null && mcpProperties.getAuth().isEnabled()) {
            applyBearer(builder);
        }
        String endpoint = StringUtils.hasText(mcpProperties.getSseEndpoint())
                ? mcpProperties.getSseEndpoint().trim() : "/sse";
        var transport = new WebFluxSseClientTransport(
                builder, new JacksonMcpJsonMapper(objectMapper), endpoint);
        log.info("MCP SSE transport ready (yml fallback) url={} authEnabled={}",
                url, mcpProperties.getAuth() != null && mcpProperties.getAuth().isEnabled());
        return new NamedClientMcpTransport("mcp_yml_fallback", transport);
    }

    private void applyBearer(WebClient.Builder builder) {
        String apiKey = mcpProperties.getAuth() != null ? mcpProperties.getAuth().getApiKey() : null;
        if (!StringUtils.hasText(apiKey)) {
            apiKey = System.getenv("KIDORA_MCP_API_KEY");
        }
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        } else {
            log.warn("MCP BEARER requested but KIDORA_MCP_API_KEY empty; calls may get 401");
        }
    }
}
