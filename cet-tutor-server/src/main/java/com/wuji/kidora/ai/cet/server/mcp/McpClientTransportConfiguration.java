package com.wuji.kidora.ai.cet.server.mcp;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 自建 MCP SSE Transport：优先 ACTIVE mcp_server_ref，空库回落 yml。
 *
 * @author liudy
 */
@Configuration
@ConditionalOnProperty(prefix = "kidora.mcp", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(KidoraMcpProperties.class)
public class McpClientTransportConfiguration {

    /**
     * 注册 NamedClientMcpTransport 列表供 Spring AI MCP Client 消费。
     *
     * @param repository       库表
     * @param transportFactory 工厂
     * @return transports
     */
    @Bean
    public List<NamedClientMcpTransport> kidoraMcpSseTransports(McpServerConnectionRepository repository,
                                                                McpSseTransportFactory transportFactory) {
        List<McpServerConnection> active = repository.listActive();
        if (active.isEmpty()) {
            return List.of(transportFactory.buildFromYmlFallback());
        }
        List<NamedClientMcpTransport> transports = new ArrayList<>();
        for (McpServerConnection conn : active) {
            transports.add(transportFactory.build(conn));
        }
        return List.copyOf(transports);
    }
}
