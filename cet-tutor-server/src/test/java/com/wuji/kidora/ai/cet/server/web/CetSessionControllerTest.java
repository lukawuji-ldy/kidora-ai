package com.wuji.kidora.ai.cet.server.web;

import com.wuji.kidora.ai.cet.core.speech.CetStreamEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        ServerSentEvent<String> asr = CetSessionController.toSse(
                CetStreamEvent.asr("{\"text\":\"Hi\",\"locale\":\"en-US\",\"provider\":\"stub\"}"));
        assertEquals("asr.transcript", asr.event());
        assertTrue(asr.data().contains("\"text\":\"Hi\""));

        ServerSentEvent<String> tts = CetSessionController.toSse(CetStreamEvent.tts("{}"));
        assertEquals("audio.tts", tts.event());

        ServerSentEvent<String> pron = CetSessionController.toSse(CetStreamEvent.pronunciation("{}"));
        assertEquals("pronunciation", pron.event());

        ServerSentEvent<String> plan = CetSessionController.toSse(CetStreamEvent.planUpdated("{\"decision\":\"replan\"}"));
        assertEquals("plan.updated", plan.event());

        ServerSentEvent<String> timing = CetSessionController.toSse(
                CetStreamEvent.timing("{\"schemaVersion\":1,\"kind\":\"turn\"}"));
        assertEquals("turn.timing", timing.event());
        assertTrue(timing.data().contains("schemaVersion"));

        ServerSentEvent<String> wrapUp = CetSessionController.toSse(
                CetStreamEvent.wrapUp("{\"phase\":\"await_child_farewell\",\"step\":1}"));
        assertEquals("session.wrapup", wrapUp.event());

        ServerSentEvent<String> completed = CetSessionController.toSse(
                CetStreamEvent.sessionCompleted("{\"sessionId\":\"cls_1\",\"status\":\"COMPLETED\"}"));
        assertEquals("session.completed", completed.event());

        ServerSentEvent<String> prop = CetSessionController.toSse(
                CetStreamEvent.prop("{\"layout\":\"propFocus\",\"activeLemma\":\"dog\"}"));
        assertEquals("turn.prop", prop.event());
        assertTrue(prop.data().contains("\"activeLemma\":\"dog\""));
    }

    @Test
    void toTurnMap_includesTextsAndIndex() {
        var row = new com.wuji.kidora.ai.cet.core.repo.CetTutorTurnRepository.TurnRow(
                "turn_1", 2, "warmup", "Hello!", "Hi", java.time.Instant.parse("2026-09-10T10:00:00Z"));
        var map = CetSessionController.toTurnMap(row);
        assertEquals(2, map.get("turnIndex"));
        assertEquals("warmup", map.get("stageId"));
        assertEquals("Hello!", map.get("tutorText"));
        assertEquals("Hi", map.get("childText"));
        assertEquals("2026-09-10T10:00:00Z", map.get("createTime"));
    }

    @Test
    void toSessionListMap_mapsFields() {
        var item = new com.wuji.kidora.ai.cet.core.repo.CetLessonSessionRepository.SessionListItem(
                "cls_1", "lrn_1", "Animals", "emma", "A1", "COMPLETED",
                java.time.Instant.parse("2026-09-10T09:00:00Z"),
                java.time.Instant.parse("2026-09-10T09:00:00Z"),
                java.time.Instant.parse("2026-09-10T09:30:00Z"),
                true);
        var map = CetSessionController.toSessionListMap(item);
        assertEquals("cls_1", map.get("sessionId"));
        assertEquals("Animals", map.get("topic"));
        assertEquals(true, map.get("hasReport"));
        assertEquals("COMPLETED", map.get("status"));
    }

    @Test
    void toPropAssetMaps_mapsLemmaThemeUrl() {
        var views = java.util.List.of(
                new com.wuji.kidora.ai.cet.core.props.PropAssetResolver.PropAssetView(
                        "dog", "pets", "/api/cet/props/dog"));
        var maps = CetSessionController.toPropAssetMaps(views);
        assertEquals(1, maps.size());
        assertEquals("dog", maps.get(0).get("lemma"));
        assertEquals("pets", maps.get(0).get("theme"));
        assertEquals("/api/cet/props/dog", maps.get(0).get("url"));
        assertTrue(CetSessionController.toPropAssetMaps(null).isEmpty());
    }
}
