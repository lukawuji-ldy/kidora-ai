package com.wuji.kidora.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型路由配置（主备、重试）。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.model")
public class KidoraModelProperties {

    private String primaryConfigId = "llm_primary";
    private List<String> fallbackConfigIds = new ArrayList<>();
    private String apiKeyOverride = "";
    private Retry retry = new Retry();
    private Duration timeout = Duration.ofSeconds(60);

    public String getPrimaryConfigId() {
        return primaryConfigId;
    }

    public void setPrimaryConfigId(String primaryConfigId) {
        this.primaryConfigId = primaryConfigId;
    }

    public List<String> getFallbackConfigIds() {
        return fallbackConfigIds;
    }

    public void setFallbackConfigIds(List<String> fallbackConfigIds) {
        this.fallbackConfigIds = fallbackConfigIds != null ? fallbackConfigIds : new ArrayList<>();
    }

    public String getApiKeyOverride() {
        return apiKeyOverride;
    }

    public void setApiKeyOverride(String apiKeyOverride) {
        this.apiKeyOverride = apiKeyOverride;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public static class Retry {
        private int maxAttempts = 2;
        private Duration backoff = Duration.ofSeconds(1);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            this.backoff = backoff;
        }
    }
}
