package com.wuji.kidora.ai.cet.core.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlanLearningHints} 单测。
 *
 * @author liudy
 */
class PlanLearningHintsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void childGoals_prefersPlanArray() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode goals = root.putArray("childGoals");
        goals.add("认识 pet");
        goals.add("会说 I have a dog");
        goals.add("用起来问答");
        List<String> out = PlanLearningHints.childGoals(root, "宠物");
        assertEquals(List.of("认识 pet", "会说 I have a dog", "用起来问答"), out);
    }

    @Test
    void childGoals_synthesizesWhenMissing() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("topic", "颜色");
        ObjectNode objectives = root.putObject("objectives");
        ArrayNode vocab = objectives.putArray("vocabulary");
        vocab.add("red");
        vocab.add("blue");
        ArrayNode patterns = objectives.putArray("patterns");
        patterns.add("It is red");
        List<String> out = PlanLearningHints.childGoals(root, "颜色");
        assertEquals(3, out.size());
        assertTrue(out.get(0).contains("red"));
        assertTrue(out.get(1).contains("It is red"));
        assertTrue(out.get(2).contains("颜色"));
    }

    @Test
    void vocabHints_fromObjectivesObject() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode objectives = root.putObject("objectives");
        ArrayNode vocab = objectives.putArray("vocabulary");
        vocab.add("dog");
        vocab.add("cat");
        vocab.add("cute");
        vocab.add("pet");
        vocab.add("fish");
        vocab.add("bird");
        assertEquals(List.of("dog", "cat", "cute", "pet", "fish"), PlanLearningHints.vocabHints(root));
    }

    @Test
    void vocabHints_fallsBackToTopic() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("topic", "我的宠物狗");
        assertEquals(List.of("我的宠物狗"), PlanLearningHints.vocabHints(root));
    }

    @Test
    void propCandidateHints_prependsTopicWhenVocabIsGreetingOnly() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("topic", "Animals and pets");
        ObjectNode objectives = root.putObject("objectives");
        ArrayNode vocab = objectives.putArray("vocabulary");
        vocab.add("hello");
        vocab.add("please");

        List<String> hints = PlanLearningHints.propCandidateHints(root, "Animals and pets");

        assertEquals(List.of("animals", "pets"), hints);
    }

    @Test
    void propCandidateHints_usesSessionTopicOverEmptyPlanTopic() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode objectives = root.putObject("objectives");
        objectives.putArray("vocabulary").add("hello");

        List<String> hints = PlanLearningHints.propCandidateHints(root, "animals and pets");

        assertEquals("animals", hints.get(0));
    }

    @Test
    void isPropStopWord_sharedByResolverAndQueue() {
        assertTrue(PlanLearningHints.isPropStopWord("hello"));
        assertTrue(PlanLearningHints.isPropStopWord("what"));
        assertTrue(!PlanLearningHints.isPropStopWord("rabbit"));
        assertTrue(!PlanLearningHints.isPropStopWord(null));
    }

    @Test
    void propCandidateHints_filtersTeachingGlueAndKeepsMissingNouns() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("topic", "Animals and pets");
        ObjectNode objectives = root.putObject("objectives");
        objectives.putArray("vocabulary")
                .add("hello")
                .add("please")
                .add("rabbit")
                .add("What color is this?");

        List<String> candidates = PlanLearningHints.propCandidateHints(root, null);

        assertTrue(candidates.contains("animals"));
        assertTrue(candidates.contains("pets"));
        assertTrue(candidates.contains("rabbit"));
        assertTrue(!candidates.contains("hello"));
        assertTrue(!candidates.contains("please"));
        assertTrue(!candidates.contains("what"));
    }

    @Test
    void propTheme_usesTopicThemeForMissingAssetTasks() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        assertEquals("pets", PlanLearningHints.propTheme(root, "Animals and pets"));
        assertEquals("colors", PlanLearningHints.propTheme(root, "颜色"));
        assertEquals("food", PlanLearningHints.propTheme(root, "fruit"));
        assertEquals("default", PlanLearningHints.propTheme(root, "school"));
    }

    @Test
    void ensureChildGoals_writesThree() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("topic", "食物");
        PlanLearningHints.ensureChildGoals(root, "食物");
        assertEquals(3, root.path("childGoals").size());
    }
}
