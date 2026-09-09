package com.wuji.kidora.ai.agent.server.auth;

/**
 * LoginResponse.
 *
 * @author liudy
 */
public record LoginResponse(
        String token,
        String tokenType,
        String userId,
        String username,
        String nickname,
        String role
) {
}
