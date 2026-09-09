package com.wuji.kidora.ai.mcp.speech;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音配置：mode=stub|db；Azure 保留第三档参数。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.speech")
public class SpeechProperties {

    /**
     * stub：固定 Stub；db：读 speech_route.primary。
     */
    private String mode = "stub";

    /**
     * 兼容旧配置；mode=stub 时忽略。若显式 azure 且 mode 非 db，可强制 Azure（调试）。
     */
    private String provider = "stub";

    /**
     * 与运行时共用的加解密密钥材料。
     */
    private String apiKeySecret = "";

    private final Azure azure = new Azure();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKeySecret() {
        return apiKeySecret;
    }

    public void setApiKeySecret(String apiKeySecret) {
        this.apiKeySecret = apiKeySecret;
    }

    public Azure getAzure() {
        return azure;
    }

    /**
     * Azure Speech 连接参数（第三档暂不启用）。
     */
    public static class Azure {

        private String key = "";

        private String region = "";

        private String defaultVoice = "en-US-AvaNeural";

        private String defaultLocale = "en-US";

        private String sttBaseUrl = "";

        private String ttsBaseUrl = "";

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getDefaultVoice() {
            return defaultVoice;
        }

        public void setDefaultVoice(String defaultVoice) {
            this.defaultVoice = defaultVoice;
        }

        public String getDefaultLocale() {
            return defaultLocale;
        }

        public void setDefaultLocale(String defaultLocale) {
            this.defaultLocale = defaultLocale;
        }

        public String getSttBaseUrl() {
            return sttBaseUrl;
        }

        public void setSttBaseUrl(String sttBaseUrl) {
            this.sttBaseUrl = sttBaseUrl;
        }

        public String getTtsBaseUrl() {
            return ttsBaseUrl;
        }

        public void setTtsBaseUrl(String ttsBaseUrl) {
            this.ttsBaseUrl = ttsBaseUrl;
        }
    }
}
