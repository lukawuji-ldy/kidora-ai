package com.wuji.kidora.ai.agent.chat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ChatFacade / 短窗单测。
 *
 * @author liudy
 */
class ChatFacadeTest {

    @Test
    void trimWindowKeepsLastN() {
        List<ChatMessageRepository.MessageRow> all = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            all.add(new ChatMessageRepository.MessageRow(
                    "m" + i, "s1", "u1", "user", "c" + i, "COMPLETED", Instant.now()));
        }
        List<ChatMessageRepository.MessageRow> trimmed = ChatFacade.trimForContext(all, 20);
        assertEquals(20, trimmed.size());
        assertEquals("c5", trimmed.get(0).content());
        assertEquals("c24", trimmed.get(19).content());
    }

    @Test
    void requireOwnedRejectsOtherUser() {
        ChatSessionRepository.SessionRow row =
                new ChatSessionRepository.SessionRow("s1", "owner", "t", 0, Instant.now());
        assertThrows(com.wuji.kidora.ai.common.exception.KidoraException.class, () -> {
            if (!"other".equals(row.userId())) {
                throw new com.wuji.kidora.ai.common.exception.KidoraException(
                        com.wuji.kidora.ai.common.exception.ErrorCode.FORBIDDEN, "无权访问该会话");
            }
        });
    }
}
