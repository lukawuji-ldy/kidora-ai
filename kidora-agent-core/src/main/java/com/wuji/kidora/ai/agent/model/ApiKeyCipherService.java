package com.wuji.kidora.ai.agent.model;

import com.wuji.kidora.ai.agent.config.KidoraSecurityProperties;
import com.wuji.kidora.ai.common.crypto.SecretCipher;
import org.springframework.stereotype.Component;

/**
 * llm_config.api_key_cipher 加解密（委托 {@link SecretCipher}）。
 *
 * @author liudy
 */
@Component
public class ApiKeyCipherService {

    public static final String PREFIX_V1 = SecretCipher.PREFIX_V1;
    public static final String PREFIX_LEGACY = SecretCipher.PREFIX_LEGACY;

    private final SecretCipher secretCipher;

    public ApiKeyCipherService(KidoraSecurityProperties securityProperties) {
        this.secretCipher = new SecretCipher(securityProperties.getApiKeySecret());
    }

    public String encrypt(String plain) {
        return secretCipher.encrypt(plain);
    }

    public String decrypt(String stored) {
        return secretCipher.decrypt(stored);
    }

    /**
     * 脱敏展示：****** + 明文末 4 位。
     */
    public String mask(String stored) {
        return secretCipher.mask(stored);
    }
}
