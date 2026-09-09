package com.wuji.kidora.ai.cet.core.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SessionEvaluatorTest.
 *
 * @author liudy
 */
class SessionEvaluatorTest {

    @Test
    void parseScoresFromJson() throws Exception {
        String json = """
                {"grammar":80,"vocabulary":75,"fluency":70,"encouragement":"Nice!","childSummary":"棒！"}
                """;
        SessionEvaluator.SessionScores scores = SessionEvaluator.parseScores(new ObjectMapper(), json);
        assertEquals(80, scores.grammar());
        assertEquals(75, scores.vocabulary());
        assertEquals(70, scores.fluency());
        assertEquals("棒！", scores.childSummary());
    }
}
