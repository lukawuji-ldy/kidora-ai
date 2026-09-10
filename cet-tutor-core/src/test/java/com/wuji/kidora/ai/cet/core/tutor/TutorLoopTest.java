package com.wuji.kidora.ai.cet.core.tutor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TutorLoop 开场时段问候与人设展示名单测。
 *
 * @author liudy
 */
class TutorLoopTest {

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
}
