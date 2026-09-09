package com.wuji.kidora.ai.mcp.speech;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RoutingSpeechProvider：仅 primary，mode=stub 走 Stub。
 *
 * @author liudy
 */
class RoutingSpeechProviderTest {

    @Test
    void modeStub_usesStub() {
        SpeechProperties props = new SpeechProperties();
        props.setMode("stub");
        StubSpeechProvider stub = new StubSpeechProvider();
        RoutingSpeechProvider router = new RoutingSpeechProvider(props, null, Map.of(), stub);
        assertEquals(SpeechVendorCodes.STUB, router.providerId());
        assertTrue(router.transcribe("AA", null, "en-US").jsonBody().contains("\"provider\":\"stub\""));
    }

    @Test
    void modeDb_usesPrimaryOnly() {
        SpeechProperties props = new SpeechProperties();
        props.setMode("db");
        StubSpeechProvider stub = new StubSpeechProvider();
        SpeechProvider fakeTx = new SpeechProvider() {
            @Override
            public String providerId() {
                return SpeechVendorCodes.TENCENT;
            }

            @Override
            public SpeechOutcome transcribe(String audioBase64, String audioUrl, String locale) {
                return SpeechOutcome.ok("{\"text\":\"tx\",\"provider\":\"tencent\"}");
            }

            @Override
            public SpeechOutcome synthesize(String text, String voice, String locale) {
                return SpeechOutcome.ok("{}");
            }

            @Override
            public SpeechOutcome scorePronunciation(String audioBase64, String referenceText, String locale) {
                return SpeechOutcome.ok("{}");
            }
        };
        SpeechVendorRepository fakeRepo = new FakeRouteRepo("tencent");
        RoutingSpeechProvider router = new RoutingSpeechProvider(
                props, fakeRepo, Map.of(SpeechVendorCodes.TENCENT, fakeTx), stub);
        assertEquals(SpeechVendorCodes.TENCENT, router.providerId());
        assertTrue(router.transcribe("AA", null, null).jsonBody().contains("tencent"));
    }

    @Test
    void mapIseScores_ok() {
        SpeechOutcome out = IFlytekSpeechProvider.mapIseScores(90, 88, 85, 92, "en-US", "Hello");
        assertTrue(out.jsonBody().contains("\"overall\":90"));
        assertTrue(out.jsonBody().contains("\"provider\":\"iflytek\""));
    }

    @Test
    void mapSoeScores_ok() {
        SpeechOutcome out = TencentSpeechProvider.mapSoeScores(80, 81, 79, 82, "en-US", "Hi");
        assertTrue(out.jsonBody().contains("\"provider\":\"tencent\""));
        assertTrue(out.jsonBody().contains("\"accuracy\":81"));
    }

    @Test
    void extractXmlScore() {
        String xml = "<xml total_score=\"87.5\" accuracy_score=\"90\"></xml>";
        assertEquals(87.5, IFlytekSpeechProvider.extractXmlScore(xml, "total_score"));
        assertEquals(90.0, IFlytekSpeechProvider.extractXmlScore(xml, "accuracy_score"));
    }

    /** 仅提供 findRoute 的测试替身 */
    static final class FakeRouteRepo extends SpeechVendorRepository {
        private final String primary;

        FakeRouteRepo(String primary) {
            super(null);
            this.primary = primary;
        }

        @Override
        public Optional<SpeechRouteRecord> findRoute() {
            return Optional.of(new SpeechRouteRecord(primary, "iflytek", "azure"));
        }

        @Override
        public Optional<SpeechVendorRecord> findByCode(String vendorCode) {
            return Optional.empty();
        }
    }
}
