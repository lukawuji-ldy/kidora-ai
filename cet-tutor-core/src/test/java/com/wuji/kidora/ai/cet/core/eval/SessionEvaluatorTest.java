package com.wuji.kidora.ai.cet.core.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SessionEvaluatorTest.
 *
 * @author liudy
 */
class SessionEvaluatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseScoresFromJson() throws Exception {
        String json = """
                {"grammar":80,"vocabulary":75,"fluency":70,"encouragement":"Nice!","childSummary":"棒！"}
                """;
        SessionEvaluator.SessionScores scores = SessionEvaluator.parseScores(mapper, json);
        assertEquals(80, scores.grammar());
        assertEquals(75, scores.vocabulary());
        assertEquals(70, scores.fluency());
        assertEquals("棒！", scores.childSummary());
    }

    @Test
    void parseEvalResult_readsDecisionAndFocus() throws Exception {
        String json = """
                {"grammar":60,"vocabulary":55,"fluency":50,"decision":"replan",
                 "focus":["grammar:third_person_s"],"pauseNewVocab":true,
                 "childSummary":"再练练","encouragement":"加油"}
                """;
        SessionEvaluator.EvalResult result = SessionEvaluator.parseEvalResult(
                mapper, json, SessionEvaluator.Decision.CONTINUE);
        assertEquals(SessionEvaluator.Decision.REPLAN, result.decision());
        assertTrue(result.pauseNewVocab());
        assertEquals(1, result.focus().size());
        assertEquals("grammar:third_person_s", result.focus().get(0));
        assertTrue(result.assessmentJson().contains("\"decision\":\"replan\""));
    }

    @Test
    void resolveTargetTurns_fromStagesSum() {
        String plan = """
                {"stages":[{"id":"a","targetTurns":2},{"id":"b","targetTurns":3}]}
                """;
        assertEquals(5, SessionEvaluator.resolveTargetTurns(mapper, plan));
    }

    @Test
    void decisionFrom_defaults() {
        assertEquals(SessionEvaluator.Decision.COMPLETE,
                SessionEvaluator.Decision.from(null, SessionEvaluator.Decision.COMPLETE));
        assertEquals(SessionEvaluator.Decision.CONTINUE,
                SessionEvaluator.Decision.from("continue", SessionEvaluator.Decision.COMPLETE));
    }
}
