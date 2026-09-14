package com.wuji.kidora.ai.cet.server.security;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Security 未认证时返回统一 {@code ApiResponse} JSON，避免空 body 导致前端 {@code res.json()} 失败。
 *
 * @author liudy
 */
public final class JsonSecurityErrorWriter {

    private JsonSecurityErrorWriter() {
    }

    public static ServerAuthenticationEntryPoint unauthorizedEntryPoint() {
        return (ServerWebExchange exchange, AuthenticationException ex) ->
                write(exchange.getResponse(), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    static Mono<Void> write(ServerHttpResponse response, HttpStatus status, ErrorCode errorCode) {
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = jsonBytes(errorCode);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    static byte[] jsonBytes(ErrorCode errorCode) {
        String json = "{\"code\":\"" + errorCode.getCode()
                + "\",\"message\":\"" + errorCode.getMessage()
                + "\",\"data\":null}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
