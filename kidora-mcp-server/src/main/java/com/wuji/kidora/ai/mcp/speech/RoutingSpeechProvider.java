package com.wuji.kidora.ai.mcp.speech;

import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 按 speech_route.primary 路由；mode=stub 固定 Stub；不自动 failover。
 *
 * @author liudy
 */
public class RoutingSpeechProvider implements SpeechProvider {

    private final SpeechProperties properties;
    private final SpeechVendorRepository vendorRepository;
    private final Map<String, SpeechProvider> providersByCode;
    private final StubSpeechProvider stub;

    public RoutingSpeechProvider(SpeechProperties properties,
                                 SpeechVendorRepository vendorRepository,
                                 Map<String, SpeechProvider> providersByCode,
                                 StubSpeechProvider stub) {
        this.properties = properties;
        this.vendorRepository = vendorRepository;
        this.providersByCode = providersByCode;
        this.stub = stub;
    }

    /**
     * 解析当前应使用的 Provider（可单测）。
     *
     * @return provider
     */
    SpeechProvider resolveDelegate() {
        if (properties == null || !"db".equalsIgnoreCase(nullToEmpty(properties.getMode()).trim())) {
            return stub;
        }
        if (vendorRepository == null) {
            return stub;
        }
        String primary = vendorRepository.findRoute()
                .map(SpeechRouteRecord::primaryVendor)
                .filter(StringUtils::hasText)
                .orElse(SpeechVendorCodes.IFLYTEK);
        SpeechProvider delegate = providersByCode.get(primary.trim().toLowerCase());
        if (delegate == null) {
            return new MissingVendorSpeechProvider(primary);
        }
        return delegate;
    }

    @Override
    public String providerId() {
        return resolveDelegate().providerId();
    }

    @Override
    public SpeechOutcome transcribe(String audioBase64, String audioUrl, String locale) {
        return resolveDelegate().transcribe(audioBase64, audioUrl, locale);
    }

    @Override
    public SpeechOutcome synthesize(String text, String voice, String locale) {
        return resolveDelegate().synthesize(text, voice, locale);
    }

    @Override
    public SpeechOutcome scorePronunciation(String audioBase64, String referenceText, String locale) {
        return resolveDelegate().scorePronunciation(audioBase64, referenceText, locale);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 未知 primary 时的失败 Provider。
     */
    static final class MissingVendorSpeechProvider implements SpeechProvider {

        private final String code;

        MissingVendorSpeechProvider(String code) {
            this.code = code == null ? "" : code;
        }

        @Override
        public String providerId() {
            return code;
        }

        @Override
        public SpeechOutcome transcribe(String audioBase64, String audioUrl, String locale) {
            return err();
        }

        @Override
        public SpeechOutcome synthesize(String text, String voice, String locale) {
            return err();
        }

        @Override
        public SpeechOutcome scorePronunciation(String audioBase64, String referenceText, String locale) {
            return err();
        }

        private SpeechOutcome err() {
            return SpeechOutcome.error("UNKNOWN_VENDOR", "Unknown primary speech vendor: " + code, code);
        }
    }
}
