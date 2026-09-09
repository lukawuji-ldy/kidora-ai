package com.wuji.kidora.ai.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.common.crypto.SecretCipher;
import com.wuji.kidora.ai.mcp.speech.AzureSpeechProvider;
import com.wuji.kidora.ai.mcp.speech.IFlytekSpeechProvider;
import com.wuji.kidora.ai.mcp.speech.RoutingSpeechProvider;
import com.wuji.kidora.ai.mcp.speech.SpeechProperties;
import com.wuji.kidora.ai.mcp.speech.SpeechProvider;
import com.wuji.kidora.ai.mcp.speech.SpeechVendorCodes;
import com.wuji.kidora.ai.mcp.speech.SpeechVendorRepository;
import com.wuji.kidora.ai.mcp.speech.StubSpeechProvider;
import com.wuji.kidora.ai.mcp.speech.TencentSpeechProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 语音供应商装配：RoutingSpeechProvider（stub 或按 speech_route.primary）。
 *
 * @author liudy
 */
@Configuration
@EnableConfigurationProperties(SpeechProperties.class)
public class SpeechConfiguration {

    @Bean
    public SecretCipher speechSecretCipher(SpeechProperties properties) {
        String material = properties.getApiKeySecret();
        if (!StringUtils.hasText(material)) {
            material = System.getenv().getOrDefault("KIDORA_API_KEY_SECRET",
                    "change-me-kidora-api-key-secret-32bytes!");
        }
        return new SecretCipher(material);
    }

    @Bean
    public StubSpeechProvider stubSpeechProvider() {
        return new StubSpeechProvider();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kidora.speech", name = "mode", havingValue = "db")
    public IFlytekSpeechProvider iFlytekSpeechProvider(SpeechVendorRepository repository,
                                                       SecretCipher speechSecretCipher,
                                                       ObjectMapper objectMapper,
                                                       WebClient.Builder webClientBuilder) {
        return new IFlytekSpeechProvider(repository, speechSecretCipher, objectMapper, webClientBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "kidora.speech", name = "mode", havingValue = "db")
    public TencentSpeechProvider tencentSpeechProvider(SpeechVendorRepository repository,
                                                       SecretCipher speechSecretCipher,
                                                       ObjectMapper objectMapper,
                                                       WebClient.Builder webClientBuilder) {
        return new TencentSpeechProvider(repository, speechSecretCipher, objectMapper, webClientBuilder);
    }

    @Bean
    public AzureSpeechProvider azureSpeechProvider(SpeechProperties properties,
                                                   WebClient.Builder webClientBuilder,
                                                   ObjectMapper objectMapper) {
        return new AzureSpeechProvider(properties.getAzure(), webClientBuilder, objectMapper);
    }

    /**
     * 对外统一 SpeechProvider。
     */
    @Bean
    public SpeechProvider speechProvider(SpeechProperties properties,
                                         StubSpeechProvider stubSpeechProvider,
                                         AzureSpeechProvider azureSpeechProvider,
                                         ObjectProvider<IFlytekSpeechProvider> iFlytek,
                                         ObjectProvider<TencentSpeechProvider> tencent,
                                         ObjectProvider<SpeechVendorRepository> repository) {
        Map<String, SpeechProvider> map = new HashMap<>();
        map.put(SpeechVendorCodes.STUB, stubSpeechProvider);
        map.put(SpeechVendorCodes.AZURE, azureSpeechProvider);
        IFlytekSpeechProvider xf = iFlytek.getIfAvailable();
        if (xf != null) {
            map.put(SpeechVendorCodes.IFLYTEK, xf);
        }
        TencentSpeechProvider tx = tencent.getIfAvailable();
        if (tx != null) {
            map.put(SpeechVendorCodes.TENCENT, tx);
        }
        return new RoutingSpeechProvider(properties, repository.getIfAvailable(), map, stubSpeechProvider);
    }
}
