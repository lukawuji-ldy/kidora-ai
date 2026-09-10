package com.wuji.kidora.ai.mcp.dictionary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 词典配置：stub | http。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.dictionary")
public class DictionaryProperties {

    /**
     * stub：本地/CI；http：免费词典 API。
     */
    private String mode = "stub";

    /**
     * Free Dictionary API 基址。
     */
    private String httpBaseUrl = "https://api.dictionaryapi.dev/api/v2/entries/en";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getHttpBaseUrl() {
        return httpBaseUrl;
    }

    public void setHttpBaseUrl(String httpBaseUrl) {
        this.httpBaseUrl = httpBaseUrl;
    }
}
