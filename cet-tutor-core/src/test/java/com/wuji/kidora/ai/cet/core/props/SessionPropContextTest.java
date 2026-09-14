package com.wuji.kidora.ai.cet.core.props;

import com.wuji.kidora.ai.cet.core.props.PropAssetResolver.PropAssetView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 会话级道具缓存：一节课只解析一次。
 *
 * @author liudy
 */
class SessionPropContextTest {

    private static final List<PropAssetView> ASSETS =
            List.of(new PropAssetView("dog", "pets", "/api/cet/props/dog"));

    @Test
    void secondCallInSameSession_doesNotHitLoader() {
        SessionPropContext context = new SessionPropContext(3600L, 100);
        AtomicInteger loads = new AtomicInteger();

        context.get("s-1", () -> countingLoad(loads));
        context.get("s-1", () -> countingLoad(loads));
        context.get("s-1", () -> countingLoad(loads));

        assertEquals(1, loads.get());
    }

    @Test
    void differentSessions_loadIndependently() {
        SessionPropContext context = new SessionPropContext(3600L, 100);
        AtomicInteger loads = new AtomicInteger();

        context.get("s-1", () -> countingLoad(loads));
        context.get("s-2", () -> countingLoad(loads));

        assertEquals(2, loads.get());
    }

    @Test
    void evict_forcesReload() {
        SessionPropContext context = new SessionPropContext(3600L, 100);
        AtomicInteger loads = new AtomicInteger();

        context.get("s-1", () -> countingLoad(loads));
        context.evict("s-1");
        context.get("s-1", () -> countingLoad(loads));

        assertEquals(2, loads.get());
    }

    @Test
    void blankSessionId_alwaysLoadsWithoutCaching() {
        SessionPropContext context = new SessionPropContext(3600L, 100);
        AtomicInteger loads = new AtomicInteger();

        context.get("", () -> countingLoad(loads));
        context.get(null, () -> countingLoad(loads));

        assertEquals(2, loads.get());
    }

    private static List<PropAssetView> countingLoad(AtomicInteger loads) {
        loads.incrementAndGet();
        return ASSETS;
    }
}
