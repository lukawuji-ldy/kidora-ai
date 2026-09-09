package com.wuji.kidora.ai.agent.model;

import com.wuji.kidora.ai.agent.config.KidoraModelProperties;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 llm_config 构建并缓存 ChatClient。
 *
 * @author liudy
 */
@Component
public class LlmClientFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmClientFactory.class);

    private final LlmConfigRepository llmConfigRepository;
    private final KidoraModelProperties modelProperties;
    private final ApiKeyCipherService apiKeyCipherService;
    private final ConcurrentHashMap<String, CachedClients> cache = new ConcurrentHashMap<>();

    public LlmClientFactory(LlmConfigRepository llmConfigRepository,
                            KidoraModelProperties modelProperties,
                            ApiKeyCipherService apiKeyCipherService) {
        this.llmConfigRepository = llmConfigRepository;
        this.modelProperties = modelProperties;
        this.apiKeyCipherService = apiKeyCipherService;
    }

    public ChatClient getChatClient(String configId) {
        return getOrCreate(configId).chatClient();
    }

    public ChatModel getChatModel(String configId) {
        return getOrCreate(configId).chatModel();
    }

    public LlmConfigRecord getConfig(String configId) {
        return getOrCreate(configId).config();
    }

    public void invalidate(String configId) {
        cache.remove(configId);
        log.info("LLM client cache invalidated, configId={}", configId);
    }

    private CachedClients getOrCreate(String configId) {
        return cache.computeIfAbsent(configId, this::build);
    }

    private CachedClients build(String configId) {
        LlmConfigRecord cfg = llmConfigRepository.requireActive(configId, LlmConfigRecord.KIND_CHAT);
        String apiKey = resolveApiKey(cfg);
        if (!StringUtils.hasText(apiKey) || "CHANGE_ME".equals(apiKey)) {
            throw new KidoraException(ErrorCode.MODEL_UNAVAILABLE,
                    "LLM API Key 未配置，请更新 llm_config 或设置环境变量 KIDORA_LLM_API_KEY");
        }

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(apiKey)
                .restClientBuilder(LlmHttpClients.restClientBuilder(modelProperties.getTimeout()))
                .webClientBuilder(LlmHttpClients.webClientBuilder(modelProperties.getTimeout()));
        String completionsPath = LlmExtraJson.text(cfg.getExtraJson(), "chat_completions_path");
        if (StringUtils.hasText(completionsPath)) {
            apiBuilder.completionsPath(completionsPath);
        }
        OpenAiApi openAiApi = apiBuilder.build();
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(cfg.getModel());
        if (cfg.getTemperature() != null) {
            optionsBuilder.temperature(cfg.getTemperature().doubleValue());
        }
        if (cfg.getMaxTokens() != null) {
            optionsBuilder.maxTokens(cfg.getMaxTokens());
        }
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .retryTemplate(LlmHttpClients.noInnerRetry())
                .build();
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        log.info("LLM client initialized, configId={}, model={}", configId, cfg.getModel());
        return new CachedClients(cfg, chatModel, chatClient);
    }

    private String resolveApiKey(LlmConfigRecord cfg) {
        if (StringUtils.hasText(modelProperties.getApiKeyOverride())) {
            return modelProperties.getApiKeyOverride().trim();
        }
        return apiKeyCipherService.decrypt(cfg.getApiKeyCipher());
    }

    private record CachedClients(LlmConfigRecord config, ChatModel chatModel, ChatClient chatClient) {
    }
}
