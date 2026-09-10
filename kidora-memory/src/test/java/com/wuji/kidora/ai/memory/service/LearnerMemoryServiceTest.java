package com.wuji.kidora.ai.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.memory.model.LearnerProfile;
import com.wuji.kidora.ai.memory.port.LearnerProfilePort;
import com.wuji.kidora.ai.memory.port.MemoryWritePort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LearnerMemoryService 合并规则单测。
 *
 * @author liudy
 */
class LearnerMemoryServiceTest {

    @Test
    void mergeExtraJson_mergesProblemsAndTopic() throws Exception {
        LearnerMemoryService service = new LearnerMemoryService(
                new LearnerProfilePort() {
                    @Override
                    public Optional<LearnerProfile> findByLearnerId(String learnerId) {
                        return Optional.empty();
                    }

                    @Override
                    public void updateExtraJson(String learnerId, String extraJson) {
                    }
                },
                new MemoryWritePort() {
                    @Override
                    public String writeSemantic(String learnerId, String content, String sourceSessionId, String extraJson) {
                        return "mem_1";
                    }

                    @Override
                    public List<String> listRecentSemantic(String learnerId, int limit) {
                        return List.of();
                    }
                },
                new ObjectMapper()
        );
        String merged = service.mergeExtraJson(
                "{\"knownVocabulary\":[\"dog\"]}",
                "{\"problems\":[\"he/she + have\"],\"focus\":[\"grammar:s\"],\"childSummary\":\"棒\"}",
                "cute cat"
        );
        assertTrue(merged.contains("he/she + have"));
        assertTrue(merged.contains("grammar:s"));
        assertTrue(merged.contains("dog"));
        assertTrue(merged.contains("cat") || merged.contains("cute"));
        assertTrue(merged.contains("lastSessionTopic"));
    }

    @Test
    void onSessionCompleted_writesProfileAndSemantic() {
        List<String> extras = new ArrayList<>();
        List<String> facts = new ArrayList<>();
        LearnerMemoryService service = new LearnerMemoryService(
                new LearnerProfilePort() {
                    @Override
                    public Optional<LearnerProfile> findByLearnerId(String learnerId) {
                        return Optional.of(new LearnerProfile(
                                learnerId, "u1", "Amy", "6-8", "A1", "emma", "ACTIVE", "{}"));
                    }

                    @Override
                    public void updateExtraJson(String learnerId, String extraJson) {
                        extras.add(learnerId + ":" + extraJson);
                    }
                },
                new MemoryWritePort() {
                    @Override
                    public String writeSemantic(String learnerId, String content, String sourceSessionId, String extraJson) {
                        facts.add(content);
                        return "mem_x";
                    }

                    @Override
                    public List<String> listRecentSemantic(String learnerId, int limit) {
                        return List.of();
                    }
                },
                new ObjectMapper()
        );
        service.onSessionCompleted("lrn_1", "cls_1", "pets",
                "{\"problems\":[\"a/an\"],\"childSummary\":\"今天很棒\"}");
        assertTrue(extras.stream().anyMatch(s -> s.startsWith("lrn_1:") && s.contains("a/an")));
        assertTrue(facts.stream().anyMatch(s -> s.contains("pets")));
        assertTrue(facts.stream().anyMatch(s -> s.contains("a/an")));
    }
}
