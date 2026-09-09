package com.wuji.kidora.ai.agent.model;

import com.wuji.kidora.ai.agent.config.KidoraModelProperties;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 模型主备路由 + 审计包装的 ChatClient 调用。
 *
 * @author liudy
 */
@Component
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final LlmClientFactory llmClientFactory;
    private final KidoraModelProperties modelProperties;
    private final LlmCallAuditor llmCallAuditor;

    public ModelRouter(LlmClientFactory llmClientFactory,
                       KidoraModelProperties modelProperties,
                       LlmCallAuditor llmCallAuditor) {
        this.llmClientFactory = llmClientFactory;
        this.modelProperties = modelProperties;
        this.llmCallAuditor = llmCallAuditor;
    }

    public List<String> orderedConfigIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (StringUtils.hasText(modelProperties.getPrimaryConfigId())) {
            ids.add(modelProperties.getPrimaryConfigId().trim());
        }
        if (modelProperties.getFallbackConfigIds() != null) {
            for (String id : modelProperties.getFallbackConfigIds()) {
                if (StringUtils.hasText(id)) {
                    ids.add(id.trim());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    public Optional<RoutedClient> tryOpen(String configId) {
        if (!StringUtils.hasText(configId)) {
            return Optional.empty();
        }
        try {
            ChatClient client = llmClientFactory.getChatClient(configId);
            LlmConfigRecord cfg = llmClientFactory.getConfig(configId);
            List<String> ordered = orderedConfigIds();
            boolean fallback = !ordered.isEmpty() && !configId.equals(ordered.get(0));
            return Optional.of(new RoutedClient(configId, cfg, client, fallback));
        } catch (KidoraException ex) {
            if (ex.getErrorCode() == ErrorCode.MODEL_UNAVAILABLE || ex.getErrorCode() == ErrorCode.NOT_FOUND) {
                log.warn("skip llm config {}: {}", configId, ex.getMessage());
                return Optional.empty();
            }
            throw ex;
        }
    }

    public RoutedClient requirePrimary() {
        List<String> ordered = orderedConfigIds();
        if (ordered.isEmpty()) {
            throw new KidoraException(ErrorCode.MODEL_UNAVAILABLE, "未配置 kidora.model.primary-config-id");
        }
        for (String id : ordered) {
            Optional<RoutedClient> opened = tryOpen(id);
            if (opened.isPresent()) {
                return opened.get();
            }
        }
        throw new KidoraException(ErrorCode.MODEL_UNAVAILABLE, "无可用 LLM 配置");
    }

    /**
     * 阻塞调用：system + user → 文本；写入审计。
     *
     * @author liudy
     */
    public String callText(CallContext ctx, String systemPrompt, String userPrompt) {
        RoutedClient routed = requirePrimary();
        long start = System.currentTimeMillis();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("system", systemPrompt);
        request.put("user", userPrompt);
        request.put("bizCaller", ctx.bizCaller());
        try {
            String content = routed.chatClient().prompt()
                    .system(systemPrompt == null ? "" : systemPrompt)
                    .user(userPrompt == null ? "" : userPrompt)
                    .call()
                    .content();
            int latency = (int) (System.currentTimeMillis() - start);
            Map<String, Object> response = Map.of("content", content == null ? "" : content);
            llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                    ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                    ctx.bizSource(), ctx.bizRefId(),
                    routed.config().getModel(), routed.config().getProvider(),
                    1, routed.fallback(), "SUCCESS", null, latency, null, null,
                    request, response));
            return content == null ? "" : content;
        } catch (RuntimeException e) {
            int latency = (int) (System.currentTimeMillis() - start);
            llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                    ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                    ctx.bizSource(), ctx.bizRefId(),
                    routed.config().getModel(), routed.config().getProvider(),
                    1, routed.fallback(), "FAILED", e.getClass().getSimpleName(), latency, null, null,
                    request, Map.of("error", String.valueOf(e.getMessage()))));
            throw e;
        }
    }

    /**
     * 流式文本增量；结束后写审计（汇总全文）。
     *
     * @author liudy
     */
    public Flux<String> streamText(CallContext ctx, String systemPrompt, String userPrompt,
                                   Consumer<String> onComplete) {
        RoutedClient routed = requirePrimary();
        long start = System.currentTimeMillis();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("system", systemPrompt);
        request.put("user", userPrompt);
        request.put("bizCaller", ctx.bizCaller());
        StringBuilder full = new StringBuilder();
        return routed.chatClient().prompt()
                .system(systemPrompt == null ? "" : systemPrompt)
                .user(userPrompt == null ? "" : userPrompt)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        full.append(chunk);
                    }
                })
                .doOnComplete(() -> {
                    int latency = (int) (System.currentTimeMillis() - start);
                    String text = full.toString();
                    llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                            ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                            ctx.bizSource(), ctx.bizRefId(),
                            routed.config().getModel(), routed.config().getProvider(),
                            1, routed.fallback(), "SUCCESS", null, latency, null, null,
                            request, Map.of("content", text)));
                    if (onComplete != null) {
                        onComplete.accept(text);
                    }
                })
                .doOnError(e -> {
                    int latency = (int) (System.currentTimeMillis() - start);
                    llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                            ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                            ctx.bizSource(), ctx.bizRefId(),
                            routed.config().getModel(), routed.config().getProvider(),
                            1, routed.fallback(), "FAILED", e.getClass().getSimpleName(), latency, null, null,
                            request, Map.of("error", String.valueOf(e.getMessage()))));
                });
    }

    public record RoutedClient(String configId, LlmConfigRecord config, ChatClient chatClient, boolean fallback) {
    }

    /**
     * @param bizCaller 细分调用方：CET_PLAN|CET_TUTOR|CET_EVAL|SAFETY 等（写入 request_json）
     * @param bizSource 表字段：CHAT|CET
      *
 * @author liudy
 */
public record CallContext(
            String traceId,
            String sessionId,
            String messageId,
            String userId,
            String learnerId,
            String bizSource,
            String bizRefId,
            String bizCaller
    ) {
    }
}
