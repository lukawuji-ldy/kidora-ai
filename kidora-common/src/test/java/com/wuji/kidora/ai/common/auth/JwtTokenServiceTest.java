package com.wuji.kidora.ai.common.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JwtTokenServiceTest.
 *
 * @author liudy
 */
class JwtTokenServiceTest {

    @Test
    void issueAndParseRoundTrip() {
        JwtTokenService service = new JwtTokenService(
                "change-me-kidora-ai-jwt-secret-key-32bytes!",
                "kidora-ai",
                72);
        AuthUser user = new AuthUser("u_demo", "parent1", "Demo Parent", "parent");
        String token = service.issueToken(user);
        AuthUser parsed = service.parse(token);
        assertEquals(user.userId(), parsed.userId());
        assertEquals(user.username(), parsed.username());
        assertEquals(user.nickname(), parsed.nickname());
        assertEquals(user.role(), parsed.role());
    }
}
