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
                 "childSummary":"再练练","encouragement":"加油",
                 "parentSummary":"本节重点练第三人称单数，建议回家再复述两句。"}
                """;
        SessionEvaluator.EvalResult result = SessionEvaluator.parseEvalResult(
                mapper, json, SessionEvaluator.Decision.CONTINUE);
        assertEquals(SessionEvaluator.Decision.REPLAN, result.decision());
        assertTrue(result.pauseNewVocab());
        assertEquals(1, result.focus().size());
        assertEquals("grammar:third_person_s", result.focus().get(0));
        assertTrue(result.assessmentJson().contains("\"decision\":\"replan\""));
        assertEquals("本节重点练第三人称单数，建议回家再复述两句。", result.parentSummary());
    }

    @Test
    void parseEvalResult_synthesizesParentSummaryWhenMissing() throws Exception {
        String json = """
                {"grammar":80,"vocabulary":70,"fluency":75,"decision":"complete",
                 "problems":["I has a dog → I have a dog"],
                 "focus":["grammar:have_has"],
                 "pauseNewVocab":false,
                 "childSummary":"你说「I has a dog」→「I have a dog」更准确。","encouragement":"很棒！"}
                """;
        SessionEvaluator.EvalResult result = SessionEvaluator.parseEvalResult(
                mapper, json, SessionEvaluator.Decision.COMPLETE);
        assertTrue(result.parentSummary().contains("语法 80"));
        assertTrue(result.parentSummary().contains("I has a dog"));
        assertTrue(result.parentSummary().contains("have_has")
                || result.parentSummary().contains("grammar:have_has"));
    }

    @Test
    void synthesizeParentSummary_fromScoresAndProblems() throws Exception {
        String json = """
                {"grammar":90,"vocabulary":85,"fluency":80,
                 "problems":["cat → It's a cat"],
                 "focus":["vocabulary:animals"],
                 "pauseNewVocab":true,
                 "childSummary":"今天认识了动物词。","encouragement":"加油"}
                """;
        String summary = SessionEvaluator.synthesizeParentSummary(mapper.readTree(json));
        assertTrue(summary.contains("词汇 85"));
        assertTrue(summary.contains("cat → It's a cat"));
        assertTrue(summary.contains("暂缓引入新词") || summary.contains("新词"));
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
