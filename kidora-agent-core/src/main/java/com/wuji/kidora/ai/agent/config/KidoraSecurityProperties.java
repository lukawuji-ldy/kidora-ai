package com.wuji.kidora.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API Key 加解密密钥材料。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.security")
public class KidoraSecurityProperties {

    private String apiKeySecret = "change-me-kidora-api-key-secret-32bytes!";

    public String getApiKeySecret() {
        return apiKeySecret;
    }

    public void setApiKeySecret(String apiKeySecret) {
        this.apiKeySecret = apiKeySecret;
    }
}
