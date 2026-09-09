package com.wuji.kidora.ai.mcp.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP Bearer API Key Filter 单测。
 *
 * @author liudy
 */
class McpApiKeyWebFilterTest {

    @Test
    void disabled_passesThrough() {
        McpAuthProperties props = new McpAuthProperties();
        props.setEnabled(false);
        props.setApiKey("secret");
        McpApiKeyWebFilter filter = new McpApiKeyWebFilter(props);

        AtomicBoolean continued = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            continued.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/sse").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(continued.get());
    }

    @Test
    void enabled_missingKey_returns401() {
        McpAuthProperties props = new McpAuthProperties();
        props.setEnabled(true);
        props.setApiKey("secret");
        McpApiKeyWebFilter filter = new McpApiKeyWebFilter(props);

        AtomicBoolean continued = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            continued.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/sse").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(false, continued.get());
    }

    @Test
    void enabled_validBearer_passesThrough() {
        McpAuthProperties props = new McpAuthProperties();
        props.setEnabled(true);
        props.setApiKey("secret");
        McpApiKeyWebFilter filter = new McpApiKeyWebFilter(props);

        AtomicBoolean continued = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            continued.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer secret")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(continued.get());
    }
}
