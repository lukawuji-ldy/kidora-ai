package com.wuji.kidora.ai.agent.model;

import com.wuji.kidora.ai.agent.config.KidoraSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API Key 加解密与脱敏。
 *
 * @author liudy
 */
class ApiKeyCipherServiceTest {

    private ApiKeyCipherService cipher;

    @BeforeEach
    void setUp() {
        KidoraSecurityProperties props = new KidoraSecurityProperties();
        props.setApiKeySecret("test-kidora-api-key-secret-32bytes!!");
        cipher = new ApiKeyCipherService(props);
    }

    @Test
    void encryptDecryptRoundTrip() {
        String plain = "sk-test-secret-key-abcdef";
        String stored = cipher.encrypt(plain);
        assertTrue(stored.startsWith(ApiKeyCipherService.PREFIX_V1));
        assertEquals(plain, cipher.decrypt(stored));
    }

    @Test
    void maskShowsLastFour() {
        String plain = "sk-test-secret-key-1234";
        String masked = cipher.mask(cipher.encrypt(plain));
        assertEquals("******1234", masked);
    }

    @Test
    void maskShortPlain() {
        assertEquals("******", cipher.mask("abc"));
        assertEquals("******", cipher.mask(null));
    }

    @Test
    void maskLegacyPrefix() {
        assertEquals("******wxyz", cipher.mask(ApiKeyCipherService.PREFIX_LEGACY + "plain-key-wxyz"));
    }
}
