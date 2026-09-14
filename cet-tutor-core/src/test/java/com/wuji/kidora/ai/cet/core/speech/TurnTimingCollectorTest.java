package com.wuji.kidora.ai.cet.core.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TurnTimingCollector 单测。
 *
 * @author liudy
 */
class TurnTimingCollectorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void safetyModelSkipped_l0Only() {
        assertTrue(TurnTimingCollector.safetyModelSkipped("L0_HARD"));
        assertTrue(TurnTimingCollector.safetyModelSkipped("L0_SOFT"));
        assertFalse(TurnTimingCollector.safetyModelSkipped("L1_MODEL"));
        assertFalse(TurnTimingCollector.safetyModelSkipped("L1_UNAVAILABLE"));
        assertFalse(TurnTimingCollector.safetyModelSkipped("NONE"));
        assertFalse(TurnTimingCollector.safetyModelSkipped(null));
    }

    @Test
    void toJson_containsSchemaFieldsAndSkips() throws Exception {
        TurnTimingCollector c = new TurnTimingCollector("turn", "voice");
        c.addAsrMs(12, false);
        c.addSafetyInMs(3, true);
        c.addTutorMs(100);
        c.addSafetyOutMs(40, false);
        c.addTtsMs(80, false);
        c.addScoreMs(0, true);
        c.addPersistMs(5);
        c.addStageEvalMs(0, true);
        c.markTtsReady();
        JsonNode root = mapper.readTree(c.toJson());
        assertEquals(1, root.path("schemaVersion").asInt());
        assertEquals("turn", root.path("kind").asText());
        assertEquals("voice", root.path("path").asText());
        assertEquals(12, root.path("asrMs").asInt());
        assertEquals(100, root.path("tutorMs").asInt());
        assertEquals(80, root.path("ttsMs").asInt());
        assertTrue(root.path("skipped").path("asr").asBoolean() == false);
        assertTrue(root.path("skipped").path("safetyInModel").asBoolean());
        assertFalse(root.path("skipped").path("safetyOutModel").asBoolean());
        assertTrue(root.path("skipped").path("score").asBoolean());
        assertTrue(root.path("skipped").path("stageEval").asBoolean());
        assertTrue(root.path("client").path("e2eHeardMs").isNull());
        assertTrue(root.path("ttsReadyMs").asInt() >= 0);
        assertTrue(root.path("serverTotalMs").asInt() >= root.path("ttsReadyMs").asInt());
    }

    @Test
    void measureMs_nonNegative() {
        long ms = TurnTimingCollector.measureMs(() -> {
            // no-op
        });
        assertTrue(ms >= 0);
    }
}
