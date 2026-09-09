package com.wuji.kidora.ai.agent.chat;

import com.wuji.kidora.ai.agent.config.KidoraAgentProperties;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通用 Chat 门面：短窗 + ChatClient 流式，无工具环。
 *
 * @author liudy
 */
@Service
public class ChatFacade {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final KidoraAgentProperties agentProperties;

    public ChatFacade(ChatSessionRepository sessionRepository,
                      ChatMessageRepository messageRepository,
                      PromptTemplateService promptTemplateService,
                      ModelRouter modelRouter,
                      KidoraAgentProperties agentProperties) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
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
        String history = recent.stream()
                .map(m -> m.role() + ": " + m.content())
                .collect(Collectors.joining("\n"));

        String system = promptTemplateService.loadAndRender("chat.system", Map.of(),
                "You are Kidora assistant. Be helpful, concise, and family-friendly.");
        String user = promptTemplateService.loadAndRender("chat.user",
                Map.of("history", history, "text", userText.trim()),
                "Conversation:\n{{history}}\n\nUser: {{text}}\nAssistant:");

        String messageId = IdGenerator.nextBizId("msg_");
        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                IdGenerator.nextBizId("tr_"),
                sessionId,
                messageId,
                userId,
                null,
                "CHAT",
                sessionId,
                "CHAT"
        );

        return modelRouter.streamText(ctx, system, user, full -> {
            messageRepository.insert(sessionId, userId, "assistant",
                    full == null ? "" : full, "COMPLETED");
            sessionRepository.touchAndIncrement(sessionId, 1);
        });
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
