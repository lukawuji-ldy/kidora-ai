package com.wuji.kidora.ai.cet.server.web;

import com.wuji.kidora.ai.cet.core.speech.CetStreamEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SSE 事件名映射单测。
 *
 * @author liudy
 */
class CetSessionControllerTest {

    @Test
    void toSse_mapsEventNames() {
        ServerSentEvent<String> delta = CetSessionController.toSse(CetStreamEvent.delta("hi"));
        assertEquals("message.delta", delta.event());
        assertEquals("hi", delta.data());

        ServerSentEvent<String> tts = CetSessionController.toSse(CetStreamEvent.tts("{}"));
        assertEquals("audio.tts", tts.event());

        ServerSentEvent<String> pron = CetSessionController.toSse(CetStreamEvent.pronunciation("{}"));
        assertEquals("pronunciation", pron.event());

        ServerSentEvent<String> plan = CetSessionController.toSse(CetStreamEvent.planUpdated("{\"decision\":\"replan\"}"));
        assertEquals("plan.updated", plan.event());
    }
}
