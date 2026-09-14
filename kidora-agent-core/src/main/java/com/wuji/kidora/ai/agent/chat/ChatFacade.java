package com.wuji.kidora.ai.agent.chat;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.wuji.kidora.ai.agent.AgentFactory;
import com.wuji.kidora.ai.agent.config.KidoraAgentProperties;
import com.wuji.kidora.ai.agent.model.LlmCallAuditor;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.KidoraSystemPromptInterceptor;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.agent.stream.AgentStreamBridge;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 Chat 门面：短窗 + 有界 ReactAgent 流式（PostgresSaver Checkpoint），无工具环。
 *
 * @author liudy
 */
@Service
public class ChatFacade {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final AgentFactory agentFactory;
    private final LlmCallAuditor llmCallAuditor;
    private final KidoraAgentProperties agentProperties;

    public ChatFacade(ChatSessionRepository sessionRepository,
                      ChatMessageRepository messageRepository,
                      PromptTemplateService promptTemplateService,
                      ModelRouter modelRouter,
                      AgentFactory agentFactory,
                      LlmCallAuditor llmCallAuditor,
                      KidoraAgentProperties agentProperties) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.agentFactory = agentFactory;
        this.llmCallAuditor = llmCallAuditor;
        this.agentProperties = agentProperties;
    }

    public String createSession(String userId, String title) {
        if (!StringUtils.hasText(userId)) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED);
        }
        return sessionRepository.insert(userId, title);
    }

    public List<ChatSessionRepository.SessionRow> listSessions(String userId) {
        return sessionRepository.listByUser(userId);
    }

    public List<ChatMessageRepository.MessageRow> listMessages(String userId, String sessionId) {
        sessionRepository.requireOwned(sessionId, userId);
        return messageRepository.listBySession(sessionId);
    }

    public Flux<String> streamReply(String userId, String sessionId, String userText) {
        if (!StringUtils.hasText(userText)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "text 不能为空");
        }
        sessionRepository.requireOwned(sessionId, userId);
        messageRepository.insert(sessionId, userId, "user", userText.trim(), "COMPLETED");
        sessionRepository.touchAndIncrement(sessionId, 1);

        int window = agentProperties.getChatWindowSize();
        List<ChatMessageRepository.MessageRow> recent = messageRepository.listRecent(sessionId, window);

        String system = promptTemplateService.loadAndRender("chat.system", Map.of(),
                "You are Kidora assistant. Be helpful, concise, and family-friendly.");

        // 仅 user/assistant 进入 ReactAgent messages；system 走 metadata
        List<Message> messages = new ArrayList<>();
        for (ChatMessageRepository.MessageRow m : recent) {
            if ("user".equalsIgnoreCase(m.role())) {
                messages.add(new UserMessage(m.content() == null ? "" : m.content()));
            } else if ("assistant".equalsIgnoreCase(m.role())) {
                messages.add(new AssistantMessage(m.content() == null ? "" : m.content()));
            }
        }
        // listRecent 已含刚写入的 user；若裁剪导致缺失则补上
        if (messages.isEmpty() || !(messages.get(messages.size() - 1) instanceof UserMessage)) {
            messages.add(new UserMessage(userText.trim()));
        }

        String messageId = IdGenerator.nextBizId("msg_");
        String traceId = IdGenerator.nextBizId("tr_");
        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                traceId,
                sessionId,
                messageId,
                userId,
                null,
                "CHAT",
                sessionId,
                "CHAT"
        );

        ModelRouter.RoutedClient routed = modelRouter.requireForCaller("CHAT");
        ReactAgent agent = agentFactory.getOrCreate(routed.configId());
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(userId + ":" + sessionId)
                .addMetadata(KidoraSystemPromptInterceptor.META_SYSTEM_PROMPT, system)
                .build();

        long start = System.currentTimeMillis();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("system", system);
        request.put("user", userText.trim());
        request.put("bizCaller", "CHAT");
        request.put("threadId", userId + ":" + sessionId);
        StringBuilder full = new StringBuilder();

        Flux<String> deltas;
        try {
            deltas = AgentStreamBridge.toContentDeltas(agent.streamMessages(messages, runnableConfig));
        } catch (Exception ex) {
            throw AgentFactory.mapLimitException(ex);
        }

        return deltas
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        full.append(chunk);
                    }
                })
                .doOnComplete(() -> {
                    String text = full.toString();
                    int latency = (int) (System.currentTimeMillis() - start);
                    llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                            ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                            ctx.bizSource(), ctx.bizRefId(),
                            routed.config().getModel(), routed.config().getProvider(),
                            1, routed.fallback(), "SUCCESS", null, latency, null, null,
                            request, Map.of("content", text)));
                    messageRepository.insert(sessionId, userId, "assistant", text, "COMPLETED");
                    sessionRepository.touchAndIncrement(sessionId, 1);
                })
                .doOnError(e -> {
                    Throwable mapped = AgentFactory.mapLimitException(e);
                    int latency = (int) (System.currentTimeMillis() - start);
                    llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                            ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                            ctx.bizSource(), ctx.bizRefId(),
                            routed.config().getModel(), routed.config().getProvider(),
                            1, routed.fallback(), "FAILED", mapped.getClass().getSimpleName(), latency, null, null,
                            request, Map.of("error", String.valueOf(mapped.getMessage()))));
                })
                .onErrorMap(AgentFactory::mapLimitException);
    }

    /**
     * 短窗裁剪（单测）。
     *
     * @author liudy
     */
    public static List<ChatMessageRepository.MessageRow> trimForContext(
            List<ChatMessageRepository.MessageRow> messages, int windowSize) {
        return ChatMessageRepository.trimWindow(messages, windowSize);
    }
}
