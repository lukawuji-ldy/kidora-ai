package com.wuji.kidora.ai.agent.stream;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * AgentStreamBridge 单元测试。
 *
 * @author liudy
 */
class AgentStreamBridgeTest {

    @Test
    void toContentDeltas_keepsAssistantTextOnly() {
        Flux<String> deltas = AgentStreamBridge.toContentDeltas(Flux.just(
                new UserMessage("hi"),
                new AssistantMessage("Hel"),
                new AssistantMessage("lo"),
                new AssistantMessage("")
        ));
        StepVerifier.create(deltas)
                .expectNext("Hel", "lo")
                .verifyComplete();
    }
}
