package com.wuji.kidora.ai.agent.stream;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

/**
 * 将 ReactAgent 消息流映射为面向用户的文本增量。
 *
 * @author liudy
 */
public final class AgentStreamBridge {

    private AgentStreamBridge() {
    }

    /**
     * 仅透出助手文本；非 AssistantMessage 忽略。
     *
     * @param messages ReactAgent.streamMessages 输出
     * @return 文本增量流
     */
    public static Flux<String> toContentDeltas(Flux<Message> messages) {
        return messages
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .mapNotNull(AssistantMessage::getText)
                .filter(text -> !text.isEmpty());
    }
}
