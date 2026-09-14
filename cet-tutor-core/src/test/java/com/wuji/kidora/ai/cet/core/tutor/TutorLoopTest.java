package com.wuji.kidora.ai.cet.core.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.cet.core.repo.CetTutorTurnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TutorLoop 开场问候、人设名、计划摘要与近期已问句抽取单测。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class TutorLoopTest {

    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private ModelRouter modelRouter;

    private TutorLoop tutorLoop;

    @BeforeEach
    void setUp() {
        tutorLoop = new TutorLoop(promptTemplateService, modelRouter, new ObjectMapper());
    }

    @Test
    void dayGreetingForHour_boundaries() {
        assertEquals("Good morning", TutorLoop.dayGreetingForHour(0));
        assertEquals("Good morning", TutorLoop.dayGreetingForHour(11));
        assertEquals("Good afternoon", TutorLoop.dayGreetingForHour(12));
        assertEquals("Good afternoon", TutorLoop.dayGreetingForHour(17));
        assertEquals("Good evening", TutorLoop.dayGreetingForHour(18));
        assertEquals("Good evening", TutorLoop.dayGreetingForHour(23));
    }

    @Test
    void personaDisplayName_knownSix() {
        assertEquals("Emma", TutorLoop.personaDisplayName("emma"));
        assertEquals("Mike", TutorLoop.personaDisplayName("mike"));
        assertEquals("Lily", TutorLoop.personaDisplayName("lily"));
        assertEquals("Tom", TutorLoop.personaDisplayName("tom"));
        assertEquals("Coco", TutorLoop.personaDisplayName("coco"));
        assertEquals("Alex", TutorLoop.personaDisplayName("alex"));
    }

    @Test
    void personaDisplayName_nullBlankAndUnknown() {
        assertEquals("Emma", TutorLoop.personaDisplayName(null));
        assertEquals("Emma", TutorLoop.personaDisplayName(""));
        assertEquals("Emma", TutorLoop.personaDisplayName("   "));
        assertEquals("Nova", TutorLoop.personaDisplayName("nova"));
        assertEquals("Mike", TutorLoop.personaDisplayName("MIKE"));
    }

    @Test
    void summarizePlan_includesVocabPatternsAndChildGoals() {
        TutorLoop local = new TutorLoop(null, null, new ObjectMapper());
        String plan = """
                {"topic":"pets","objectives":{"vocabulary":["dog","cat"],"patterns":["It's ...","big/small"]},
                "childGoals":["认识狗","会说 It's a dog","用起来问答"],
                "stages":[{"id":"dialog","name":"Dialog","goal":"Practice colors with choice and compare","targetTurns":5}]}
                """;
        String summary = local.summarizePlan(plan);
        assertTrue(summary.contains("topic=pets"));
        assertTrue(summary.contains("vocabulary="));
        assertTrue(summary.contains("dog"));
        assertTrue(summary.contains("patterns="));
        assertTrue(summary.contains("childGoals="));
    }

    @Test
    void resolveStageGoal_matchesStageId() {
        TutorLoop local = new TutorLoop(null, null, new ObjectMapper());
        String plan = """
                {"stages":[
                  {"id":"warmup","goal":"Greet and name one color"},
                  {"id":"dialog","goal":"Practice colors with choice and compare"}
                ]}
                """;
        assertEquals("Practice colors with choice and compare", local.resolveStageGoal(plan, "dialog"));
        assertEquals("Greet and name one color", local.resolveStageGoal(plan, "warmup"));
        assertEquals("", local.resolveStageGoal(plan, "missing"));
    }

    @Test
    void extractAsksFromTutorText_stripsParenAndKeepsQuestions() {
        List<String> asks = TutorLoop.extractAsksFromTutorText(
                "Great! Now: What color is this?（这是什么颜色？） You can say \"It's red.\"");
        assertEquals(1, asks.size());
        assertTrue(asks.get(0).contains("What color is this?"));
    }

    @Test
    void extractAsksFromTutorText_capturesSayPrompt() {
        List<String> asks = TutorLoop.extractAsksFromTutorText(
                "Nice! Say: a big red dog.");
        assertEquals(1, asks.size());
        assertTrue(asks.get(0).toLowerCase().startsWith("say:"));
    }

    @Test
    void extractRecentAsks_joinsAcrossTurns() {
        List<CetTutorTurnRepository.TurnRow> rows = List.of(
                new CetTutorTurnRepository.TurnRow("t1", 1, "dialog",
                        "Do you like dogs?（你喜欢狗吗？）", "yes"),
                new CetTutorTurnRepository.TurnRow("t2", 2, "dialog",
                        "Great! What color is your dog?", "red")
        );
        String asks = TutorLoop.extractRecentAsks(rows);
        assertTrue(asks.contains("Do you like dogs?"));
        assertTrue(asks.contains("What color is your dog?"));
    }

    @Test
    void extractRecentAsks_emptyIsNone() {
        assertEquals("(none)", TutorLoop.extractRecentAsks(List.of()));
        assertEquals("(none)", TutorLoop.extractRecentAsks(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateOpening_injectsDisplayName() {
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(modelRouter.callText(any(), anyString(), anyString())).thenReturn("Hi Amy!");

        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "tr", "cls", "msg", "u1", "lrn1", "CET", "cls", "CET_TUTOR");
        tutorLoop.generateOpening("{\"topic\":\"pets\",\"stages\":[]}", "A1", "emma", "Amy", "pets", ctx);

        ArgumentCaptor<Map<String, String>> varsCap = ArgumentCaptor.forClass(Map.class);
        verify(promptTemplateService).loadAndRender(eq("cet.tutor.opening.system"), varsCap.capture(), anyString());
        assertEquals("Amy", varsCap.getValue().get("displayName"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateReply_injectsDisplayName() {
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(modelRouter.callText(any(), anyString(), anyString())).thenReturn("Nice!");

        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "tr", "cls", "msg", "u1", "lrn1", "CET", "cls", "CET_TUTOR");
        tutorLoop.generateReply("{\"topic\":\"pets\",\"stages\":[]}", "A1", "emma", "小明",
                "hi", List.of(), ctx);

        ArgumentCaptor<Map<String, String>> varsCap = ArgumentCaptor.forClass(Map.class);
        verify(promptTemplateService).loadAndRender(eq("cet.tutor.system"), varsCap.capture(), anyString());
        assertEquals("小明", varsCap.getValue().get("displayName"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateReply_withoutProps_disablesPropPointingQuestions() {
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(modelRouter.callText(any(), anyString(), anyString())).thenReturn("Nice!");

        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "tr", "cls", "msg", "u1", "lrn1", "CET", "cls", "CET_TUTOR");
        tutorLoop.generateReply("{\"topic\":\"pets\",\"stages\":[]}", "A1", "emma", "小明",
                "hi", List.of(), ctx);

        ArgumentCaptor<Map<String, String>> varsCap = ArgumentCaptor.forClass(Map.class);
        verify(promptTemplateService).loadAndRender(eq("cet.tutor.system"), varsCap.capture(), anyString());
        assertEquals("false", varsCap.getValue().get("hasPropAssets"));
        assertTrue(varsCap.getValue().get("propInstruction").contains("禁止"));
    }

    @Test
    void parseReply_stripsDirectiveAndKeepsLemma() {
        TutorLoop.TutorReply reply = TutorLoop.parseReply(
                "Look! Is this a cat or a dog?\n[[PROP:dog]]");

        assertEquals("Look! Is this a cat or a dog?", reply.text());
        assertEquals("dog", reply.propLemma());
    }

    @Test
    void parseReply_toleratesSpacingCaseAndTrailingNoise() {
        assertEquals("cat", TutorLoop.parseReply("Look at the cat! [[ prop : CAT ]]").propLemma());
        assertEquals("Look at the cat!", TutorLoop.parseReply("Look at the cat! [[ prop : CAT ]]").text());
    }

    @Test
    void parseReply_noneMeansNoProp() {
        TutorLoop.TutorReply reply = TutorLoop.parseReply("Nice job!\n\n[[PROP:none]]");

        assertEquals("Nice job!", reply.text());
        assertNull(reply.propLemma());
    }

    @Test
    void parseReply_missingDirectiveLeavesTextUntouched() {
        TutorLoop.TutorReply reply = TutorLoop.parseReply("Nice job! Say it again.");

        assertEquals("Nice job! Say it again.", reply.text());
        assertNull(reply.propLemma());
    }

    @Test
    void parseReply_lastDirectiveWinsAndAllAreStripped() {
        TutorLoop.TutorReply reply = TutorLoop.parseReply(
                "[[PROP:cat]] Look at the dog! [[PROP:dog]]");

        assertEquals("dog", reply.propLemma());
        assertFalse(reply.text().contains("[["));
    }

    @Test
    void parseReply_blankInputIsPassedThrough() {
        assertNull(TutorLoop.parseReply(null).text());
        assertNull(TutorLoop.parseReply(null).propLemma());
        assertEquals("", TutorLoop.parseReply("").text());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateReply_withProps_requiresPropDirective() {
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(modelRouter.callText(any(), anyString(), anyString()))
                .thenReturn("Look! Is this a cat or a dog?\n[[PROP:dog]]");

        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "tr", "cls", "msg", "u1", "lrn1", "CET", "cls", "CET_TUTOR");
        TutorLoop.TutorReply reply = tutorLoop.generateReply(
                "{\"topic\":\"pets\",\"stages\":[]}", "A1", "emma", "小明",
                "hi", List.of(), List.of("dog", "cat", "pet"), ctx);

        assertEquals("dog", reply.propLemma());
        assertEquals("Look! Is this a cat or a dog?", reply.text());

        ArgumentCaptor<Map<String, String>> varsCap = ArgumentCaptor.forClass(Map.class);
        verify(promptTemplateService).loadAndRender(eq("cet.tutor.system"), varsCap.capture(), anyString());
        assertTrue(varsCap.getValue().get("propInstruction").contains("[[PROP:"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateWrapUpReply_usesWrapUpPrompts() {
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(modelRouter.callText(any(), anyString(), anyString())).thenReturn("Bye!");

        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "tr", "cls", "msg", "u1", "lrn1", "CET", "cls", "CET_TUTOR");
        tutorLoop.generateWrapUpReply(1, "{\"topic\":\"pets\"}", "A1", "emma", "Amy",
                "pets", "", List.of(), ctx);

        ArgumentCaptor<Map<String, String>> varsCap = ArgumentCaptor.forClass(Map.class);
        verify(promptTemplateService).loadAndRender(eq("cet.tutor.wrapup.system"), varsCap.capture(), anyString());
        assertEquals("1", varsCap.getValue().get("wrapUpStep"));
    }
}
