package com.wuji.kidora.ai.agent.server.security;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JsonSecurityErrorWriterTest.
 *
 * @author liudy
 */
class JsonSecurityErrorWriterTest {

    @Test
    void unauthorizedJson_containsApiResponseShape() {
        String json = new String(JsonSecurityErrorWriter.jsonBytes(ErrorCode.UNAUTHORIZED), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"code\":\"UNAUTHORIZED\""));
        assertTrue(json.contains("未登录或令牌无效"));
        assertTrue(json.contains("\"data\":null"));
    }

    @Test
    void write_setsStatusAndJsonBody() {
        MockServerHttpResponse response = new MockServerHttpResponse();
        JsonSecurityErrorWriter.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED).block();
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));
        String body = response.getBodyAsString().block();
        assertTrue(body != null && body.contains("UNAUTHORIZED"));
    }
}
