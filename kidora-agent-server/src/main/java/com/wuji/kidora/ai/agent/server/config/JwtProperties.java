package com.wuji.kidora.ai.agent.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JwtProperties.
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.security.jwt")
public class JwtProperties {

    private String secret = "change-me-kidora-ai-jwt-secret-key-32bytes!";
    private long expireHours = 72;
    private String issuer = "kidora-ai";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireHours() {
        return expireHours;
    }

    public void setExpireHours(long expireHours) {
        this.expireHours = expireHours;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
