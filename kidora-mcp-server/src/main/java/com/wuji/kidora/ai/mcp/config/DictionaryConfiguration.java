package com.wuji.kidora.ai.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.mcp.dictionary.DictionaryProperties;
import com.wuji.kidora.ai.mcp.dictionary.DictionaryProvider;
import com.wuji.kidora.ai.mcp.dictionary.HttpDictionaryProvider;
import com.wuji.kidora.ai.mcp.dictionary.StubDictionaryProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 词典供应商装配：stub | http。
 *
 * @author liudy
 */
@Configuration
@EnableConfigurationProperties(DictionaryProperties.class)
public class DictionaryConfiguration {

    @Bean
    public StubDictionaryProvider stubDictionaryProvider() {
        return new StubDictionaryProvider();
    }

    @Bean
    public HttpDictionaryProvider httpDictionaryProvider(WebClient.Builder webClientBuilder,
                                                         ObjectMapper objectMapper,
                                                         DictionaryProperties properties) {
        return new HttpDictionaryProvider(webClientBuilder, objectMapper, properties);
    }

    /**
     * 对外统一 DictionaryProvider。
     *
     * @param properties             配置
     * @param stubDictionaryProvider stub
     * @param httpDictionaryProvider http
     * @return provider
     */
    @Bean
    public DictionaryProvider dictionaryProvider(DictionaryProperties properties,
                                                 StubDictionaryProvider stubDictionaryProvider,
                                                 HttpDictionaryProvider httpDictionaryProvider) {
        String mode = StringUtils.hasText(properties.getMode()) ? properties.getMode().trim() : "stub";
        if ("http".equalsIgnoreCase(mode)) {
            return httpDictionaryProvider;
        }
        return stubDictionaryProvider;
    }
}
