package com.wuji.kidora.ai.agent.model;

import com.wuji.kidora.ai.agent.config.KidoraModelProperties;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ModelRouter 按 bizCaller 选模单测。
 *
 * @author liudy
 */
class ModelRouterTest {

    @Test
    void requireForCaller_cetTutorUsesMappedConfig() {
        KidoraModelProperties props = new KidoraModelProperties();
        props.setPrimaryConfigId("llm_primary");
        props.setCallerConfigIds(Map.of("CET_TUTOR", "llm_chat_primary"));
        TrackingRouter router = new TrackingRouter(props, Set.of());

        ModelRouter.RoutedClient client = router.requireForCaller("CET_TUTOR");
        assertEquals("llm_chat_primary", client.configId());
        assertEquals(List.of("llm_chat_primary"), router.openedIds);
    }

    @Test
    void requireForCaller_safetyUsesPrimary() {
        KidoraModelProperties props = new KidoraModelProperties();
        props.setPrimaryConfigId("llm_primary");
        props.setCallerConfigIds(Map.of("CET_TUTOR", "llm_chat_primary"));
        TrackingRouter router = new TrackingRouter(props, Set.of());

        ModelRouter.RoutedClient client = router.requireForCaller("SAFETY");
        assertEquals("llm_primary", client.configId());
        assertEquals(List.of("llm_primary"), router.openedIds);
    }

    @Test
    void requireForCaller_mappedUnavailableFallsBackToPrimary() {
        KidoraModelProperties props = new KidoraModelProperties();
        props.setPrimaryConfigId("llm_primary");
        props.setCallerConfigIds(Map.of("CET_TUTOR", "llm_chat_primary"));
        TrackingRouter router = new TrackingRouter(props, Set.of("llm_chat_primary"));

        ModelRouter.RoutedClient client = router.requireForCaller("CET_TUTOR");
        assertEquals("llm_primary", client.configId());
        assertEquals(List.of("llm_chat_primary", "llm_primary"), router.openedIds);
    }

    @Test
    void mappedConfigId_blankCallerEmpty() {
        KidoraModelProperties props = new KidoraModelProperties();
        props.setCallerConfigIds(Map.of("CET_TUTOR", "llm_chat_primary"));
        ModelRouter router = new ModelRouter(null, props, null);
        assertTrue(router.mappedConfigId(null).isEmpty());
        assertTrue(router.mappedConfigId("  ").isEmpty());
        assertEquals("llm_chat_primary", router.mappedConfigId("CET_TUTOR").orElseThrow());
    }

    @Test
    void requirePrimary_noneAvailableThrows() {
        KidoraModelProperties props = new KidoraModelProperties();
        props.setPrimaryConfigId("llm_primary");
        TrackingRouter router = new TrackingRouter(props, Set.of("llm_primary"));
        KidoraException ex = assertThrows(KidoraException.class, router::requirePrimary);
        assertEquals(ErrorCode.MODEL_UNAVAILABLE, ex.getErrorCode());
    }

    /**
     * 记录 tryOpen 顺序；failIds 模拟不可用配置。
     */
    static final class TrackingRouter extends ModelRouter {
        final List<String> openedIds = new ArrayList<>();
        private final Set<String> failIds;

        TrackingRouter(KidoraModelProperties props, Set<String> failIds) {
            super(null, props, null);
            this.failIds = failIds == null ? Set.of() : new HashSet<>(failIds);
        }

        @Override
        public Optional<RoutedClient> tryOpen(String configId) {
            openedIds.add(configId);
            if (failIds.contains(configId)) {
                return Optional.empty();
            }
            LlmConfigRecord cfg = new LlmConfigRecord();
            cfg.setConfigId(configId);
            cfg.setModel("model-" + configId);
            cfg.setProvider("test");
            return Optional.of(new RoutedClient(configId, cfg, (ChatClient) null, false));
        }
    }
}
