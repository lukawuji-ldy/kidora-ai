package com.wuji.kidora.ai.mcp.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * MCP 路径 Bearer / X-API-Key 校验；鉴权关闭或非 MCP 路径时放行。
 *
 * @author liudy
 */
@Component
public class McpApiKeyWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(McpApiKeyWebFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<PathPattern> MCP_PATHS = List.of(
            PathPatternParser.defaultInstance.parse("/sse"),
            PathPatternParser.defaultInstance.parse("/sse/**"),
            PathPatternParser.defaultInstance.parse("/mcp"),
            PathPatternParser.defaultInstance.parse("/mcp/**")
    );

    private final McpAuthProperties authProperties;

    public McpApiKeyWebFilter(McpAuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!authProperties.isEnabled()) {
            return chain.filter(exchange);
        }
        if (!isMcpPath(exchange.getRequest().getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }
        if (!StringUtils.hasText(authProperties.getApiKey())) {
            log.warn("MCP auth enabled but api-key empty, reject {}", exchange.getRequest().getURI());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        String provided = extractKey(exchange.getRequest().getHeaders());
        if (authProperties.getApiKey().equals(provided)) {
            return chain.filter(exchange);
        }
        log.warn("MCP auth failed for {}", exchange.getRequest().getPath());
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    static boolean isMcpPath(PathContainer path) {
        for (PathPattern pattern : MCP_PATHS) {
            if (pattern.matches(path)) {
                return true;
            }
        }
        return false;
    }

    static String extractKey(HttpHeaders headers) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization)
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        String apiKey = headers.getFirst("X-API-Key");
        return StringUtils.hasText(apiKey) ? apiKey.trim() : null;
    }
}
