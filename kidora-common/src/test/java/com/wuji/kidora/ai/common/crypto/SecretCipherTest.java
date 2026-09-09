package com.wuji.kidora.ai.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SecretCipher 单测。
 *
 * @author liudy
 */
class SecretCipherTest {

    private SecretCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new SecretCipher("test-kidora-api-key-secret-32bytes!!");
    }

    @Test
    void encryptDecryptRoundTrip() {
        String plain = "sk-test-secret-key-abcdef";
        String stored = cipher.encrypt(plain);
        assertTrue(stored.startsWith(SecretCipher.PREFIX_V1));
        assertEquals(plain, cipher.decrypt(stored));
    }

    @Test
    void maskShowsLastFour() {
        String plain = "sk-test-secret-key-1234";
        assertEquals("******1234", cipher.mask(cipher.encrypt(plain)));
    }

    @Test
    void maskShortPlain() {
        assertEquals("******", cipher.mask("abc"));
        assertEquals("******", cipher.mask(null));
    }

    @Test
    void maskLegacyPrefix() {
        assertEquals("******wxyz", cipher.mask(SecretCipher.PREFIX_LEGACY + "plain-key-wxyz"));
    }
}
