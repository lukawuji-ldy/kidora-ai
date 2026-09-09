package com.wuji.kidora.ai.mcp.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.common.crypto.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * 讯飞整栈：听写 ASR + 合成 TTS + ISE 发音（英文句子）。
 * <p>
 * 听写/合成走开放平台 WebAPI；ISE 使用评测 HTTP（兼容短音频），分数映射到统一字段。
 *
 * @author liudy
 */
public class IFlytekSpeechProvider implements SpeechProvider {

    public static final String PROVIDER_ID = SpeechVendorCodes.IFLYTEK;

    private static final Logger log = LoggerFactory.getLogger(IFlytekSpeechProvider.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    private final SpeechVendorRepository vendorRepository;
    private final SecretCipher secretCipher;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public IFlytekSpeechProvider(SpeechVendorRepository vendorRepository,
                                 SecretCipher secretCipher,
                                 ObjectMapper objectMapper,
                                 WebClient.Builder webClientBuilder) {
        this.vendorRepository = vendorRepository;
        this.secretCipher = secretCipher;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    /** 测试注入 */
    public IFlytekSpeechProvider(SpeechVendorRepository vendorRepository,
                                 SecretCipher secretCipher,
                                 ObjectMapper objectMapper,
                                 WebClient webClient) {
        this.vendorRepository = vendorRepository;
        this.secretCipher = secretCipher;
        this.objectMapper = objectMapper;
        this.webClient = webClient;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public SpeechOutcome transcribe(String audioBase64, String audioUrl, String locale) {
        Optional<VendorCredentials> creds = loadCreds();
        if (creds.isEmpty()) {
            return notConfigured();
        }
        try {
            byte[] audio = resolveAudio(audioBase64, audioUrl);
            VendorCredentials c = creds.get();
            // 开放平台一句话听写（短音频）HTTP：X-Appid / X-CurTime / X-Param / X-CheckSum
            String curTime = String.valueOf(System.currentTimeMillis() / 1000);
            String paramJson = "{\"engine_type\":\"sms16k\",\"aue\":\"raw\"}";
            String paramBase64 = Base64.getEncoder().encodeToString(paramJson.getBytes(StandardCharsets.UTF_8));
            String checksum = md5(c.get("apiKey") + curTime + paramBase64);
            String body = webClient.post()
                    .uri("https://api.xfyun.cn/v1/service/v1/iat")
                    .header("X-Appid", c.get("appId"))
                    .header("X-CurTime", curTime)
                    .header("X-Param", paramBase64)
                    .header("X-CheckSum", checksum)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                    .bodyValue("audio=" + urlEncode(Base64.getEncoder().encodeToString(audio)))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            return mapAsr(body, locale);
        } catch (Exception e) {
            log.warn("iFlytek ASR failed: {}", e.getMessage());
            return SpeechOutcome.error("VENDOR_API_FAILED", "iFlytek ASR failed", PROVIDER_ID);
        }
    }

    @Override
    public SpeechOutcome synthesize(String text, String voice, String locale) {
        Optional<VendorCredentials> creds = loadCreds();
        if (creds.isEmpty()) {
            return notConfigured();
        }
        try {
            VendorCredentials c = creds.get();
            String curTime = String.valueOf(System.currentTimeMillis() / 1000);
            String vcn = StringUtils.hasText(voice) ? voice : "x2_xiaoyan";
            String paramJson = "{\"aue\":\"lame\",\"auf\":\"audio/L16;rate=16000\",\"voice_name\":\""
                    + vcn + "\",\"engine_type\":\"intp65\"}";
            String paramBase64 = Base64.getEncoder().encodeToString(paramJson.getBytes(StandardCharsets.UTF_8));
            String checksum = md5(c.get("apiKey") + curTime + paramBase64);
            byte[] audio = webClient.post()
                    .uri("https://api.xfyun.cn/v1/service/v1/tts")
                    .header("X-Appid", c.get("appId"))
                    .header("X-CurTime", curTime)
                    .header("X-Param", paramBase64)
                    .header("X-CheckSum", checksum)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                    .bodyValue("text=" + urlEncode(text))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(TIMEOUT);
            if (audio == null || audio.length == 0) {
                return SpeechOutcome.error("VENDOR_API_FAILED", "Empty TTS body", PROVIDER_ID);
            }
            // 可能返回 JSON 错误
            if (audio.length > 0 && audio[0] == '{') {
                return SpeechOutcome.error("VENDOR_API_FAILED", "iFlytek TTS error payload", PROVIDER_ID);
            }
            String loc = defaultLocale(locale);
            return SpeechOutcome.ok("{\"audioBase64\":\"" + Base64.getEncoder().encodeToString(audio)
                    + "\",\"mimeType\":\"audio/mpeg\",\"voice\":\"" + escape(vcn)
                    + "\",\"locale\":\"" + escape(loc) + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
        } catch (Exception e) {
            log.warn("iFlytek TTS failed: {}", e.getMessage());
            return SpeechOutcome.error("VENDOR_API_FAILED", "iFlytek TTS failed", PROVIDER_ID);
        }
    }

    @Override
    public SpeechOutcome scorePronunciation(String audioBase64, String referenceText, String locale) {
        Optional<VendorCredentials> creds = loadCreds();
        if (creds.isEmpty()) {
            return notConfigured();
        }
        try {
            byte[] audio = resolveAudio(audioBase64, null);
            VendorCredentials c = creds.get();
            String curTime = String.valueOf(System.currentTimeMillis() / 1000);
            String paramJson = "{\"aue\":\"raw\",\"result_level\":\"entirety\",\"language\":\"en_us\","
                    + "\"category\":\"read_sentence\",\"extra_ability\":\"multi_dimension\"}";
            String paramBase64 = Base64.getEncoder().encodeToString(paramJson.getBytes(StandardCharsets.UTF_8));
            String checksum = md5(c.get("apiKey") + curTime + paramBase64);
            String form = "audio=" + urlEncode(Base64.getEncoder().encodeToString(audio))
                    + "&text=" + urlEncode(referenceText);
            String body = webClient.post()
                    .uri("https://api.xfyun.cn/v1/service/v1/ise")
                    .header("X-Appid", c.get("appId"))
                    .header("X-CurTime", curTime)
                    .header("X-Param", paramBase64)
                    .header("X-CheckSum", checksum)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            return mapIse(body, locale, referenceText);
        } catch (Exception e) {
            log.warn("iFlytek ISE failed: {}", e.getMessage());
            return SpeechOutcome.error("VENDOR_API_FAILED", "iFlytek ISE failed", PROVIDER_ID);
        }
    }

    SpeechOutcome mapAsr(String body, String locale) throws Exception {
        if (!StringUtils.hasText(body)) {
            return SpeechOutcome.error("VENDOR_API_FAILED", "Empty ASR", PROVIDER_ID);
        }
        JsonNode root = objectMapper.readTree(body);
        if (root.has("code") && root.path("code").asInt() != 0) {
            return SpeechOutcome.error("VENDOR_API_FAILED", root.path("desc").asText("ASR error"), PROVIDER_ID);
        }
        String text = root.path("data").asText("");
        if (!StringUtils.hasText(text) && root.has("result")) {
            text = root.path("result").asText("");
        }
        String loc = defaultLocale(locale);
        return SpeechOutcome.ok("{\"text\":\"" + escape(text) + "\",\"confidence\":0.9,\"locale\":\""
                + escape(loc) + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    /**
     * 映射 ISE XML/JSON 结果中的总分维度（可单测）。
     */
    static SpeechOutcome mapIseScores(double overall, double accuracy, double fluency, double completeness,
                                      String locale, String referenceText) {
        String loc = defaultLocale(locale);
        return SpeechOutcome.ok("{\"overall\":" + overall + ",\"accuracy\":" + accuracy
                + ",\"fluency\":" + fluency + ",\"completeness\":" + completeness
                + ",\"locale\":\"" + escape(loc) + "\",\"referenceText\":\"" + escape(referenceText)
                + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    SpeechOutcome mapIse(String body, String locale, String referenceText) throws Exception {
        if (!StringUtils.hasText(body)) {
            return SpeechOutcome.error("VENDOR_API_FAILED", "Empty ISE", PROVIDER_ID);
        }
        // 部分接口返回 XML；尝试从 JSON data 或正则提取
        if (body.trim().startsWith("{")) {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("code") && root.path("code").asInt() != 0) {
                return SpeechOutcome.error("VENDOR_API_FAILED", root.path("desc").asText("ISE error"), PROVIDER_ID);
            }
            JsonNode data = root.path("data");
            double overall = firstDouble(data, "total_score", "overall");
            double accuracy = firstDouble(data, "accuracy_score", "accuracy");
            double fluency = firstDouble(data, "fluency_score", "fluency");
            double completeness = firstDouble(data, "integrity_score", "completeness");
            if (overall == 0 && accuracy == 0) {
                overall = extractXmlScore(body, "total_score");
                accuracy = extractXmlScore(body, "accuracy_score");
                fluency = extractXmlScore(body, "fluency_score");
                completeness = extractXmlScore(body, "integrity_score");
            }
            return mapIseScores(overall, accuracy, fluency, completeness, locale, referenceText);
        }
        double overall = extractXmlScore(body, "total_score");
        double accuracy = extractXmlScore(body, "accuracy_score");
        double fluency = extractXmlScore(body, "fluency_score");
        double completeness = extractXmlScore(body, "integrity_score");
        return mapIseScores(overall, accuracy, fluency, completeness, locale, referenceText);
    }

    private Optional<VendorCredentials> loadCreds() {
        if (vendorRepository == null) {
            return Optional.empty();
        }
        return vendorRepository.findByCode(PROVIDER_ID)
                .filter(SpeechVendorRecord::active)
                .flatMap(r -> VendorCredentials.parse(r.credentialsCipher(), secretCipher, objectMapper))
                .filter(c -> c.hasText("appId") && c.hasText("apiKey"));
    }

    private SpeechOutcome notConfigured() {
        return SpeechOutcome.error("VENDOR_NOT_CONFIGURED",
                "iFlytek credentials missing in speech_vendor_config", PROVIDER_ID);
    }

    private byte[] resolveAudio(String audioBase64, String audioUrl) {
        if (StringUtils.hasText(audioBase64)) {
            return Base64.getDecoder().decode(audioBase64.trim());
        }
        if (!StringUtils.hasText(audioUrl)) {
            throw new IllegalArgumentException("audio required");
        }
        byte[] bytes = webClient.get().uri(audioUrl.trim()).retrieve().bodyToMono(byte[].class).block(TIMEOUT);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("empty audio url");
        }
        return bytes;
    }

    private static double firstDouble(JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.has(k)) {
                return node.path(k).asDouble(0);
            }
        }
        return 0;
    }

    static double extractXmlScore(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int i = xml.indexOf(open);
        if (i < 0) {
            // attribute form total_score="85.0"
            String attr = tag + "=\"";
            int a = xml.indexOf(attr);
            if (a < 0) {
                return 0;
            }
            int s = a + attr.length();
            int e = xml.indexOf('"', s);
            if (e < 0) {
                return 0;
            }
            try {
                return Double.parseDouble(xml.substring(s, e));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        int s = i + open.length();
        int e = xml.indexOf(close, s);
        if (e < 0) {
            return 0;
        }
        try {
            return Double.parseDouble(xml.substring(s, e).trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String defaultLocale(String locale) {
        return StringUtils.hasText(locale) ? locale.trim() : "en-US";
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String md5(String raw) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : dig) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
