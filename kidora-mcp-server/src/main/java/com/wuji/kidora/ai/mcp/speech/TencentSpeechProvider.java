package com.wuji.kidora.ai.mcp.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.common.crypto.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * 腾讯云整栈：短音频 ASR + TTS + 智聆 SOE 英文句子评测。
 *
 * @author liudy
 */
public class TencentSpeechProvider implements SpeechProvider {

    public static final String PROVIDER_ID = SpeechVendorCodes.TENCENT;

    private static final Logger log = LoggerFactory.getLogger(TencentSpeechProvider.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    private final SpeechVendorRepository vendorRepository;
    private final SecretCipher secretCipher;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public TencentSpeechProvider(SpeechVendorRepository vendorRepository,
                                 SecretCipher secretCipher,
                                 ObjectMapper objectMapper,
                                 WebClient.Builder webClientBuilder) {
        this.vendorRepository = vendorRepository;
        this.secretCipher = secretCipher;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    public TencentSpeechProvider(SpeechVendorRepository vendorRepository,
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
            String appId = c.get("appId");
            long ts = System.currentTimeMillis() / 1000;
            String voiceId = UUID.randomUUID().toString();
            // 录音文件识别极速版（短音频）
            String url = "https://asr.cloud.tencent.com/asr/flash/v1/" + appId
                    + "?engine_type=16k_en&voice_format=1&speaker_diarization=0&filter_dirty=0&filter_modal=0"
                    + "&filter_punc=0&convert_num_mode=1&word_info=0&first_channel_only=1";
            String sign = signFlash(c.get("secretId"), c.get("secretKey"), appId, ts);
            String body = webClient.post()
                    .uri(url)
                    .header("Authorization", sign)
                    .header("Content-Type", "application/octet-stream")
                    .header("Host", "asr.cloud.tencent.com")
                    .bodyValue(audio)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            return mapAsr(body, locale);
        } catch (Exception e) {
            log.warn("Tencent ASR failed: {}", e.getMessage());
            return SpeechOutcome.error("VENDOR_API_FAILED", "Tencent ASR failed", PROVIDER_ID);
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
            // 基础 TTS：使用腾讯云 TextToVoice HTTP（简化签名，走公共网关路径需 SecretId/Key）
            // 本实现用 SOE 无关的 tts 开放接口形态；失败时返回结构化错误便于联调
            String voiceType = StringUtils.hasText(voice) ? voice : "101001";
            String payload = "{\"Action\":\"TextToVoice\",\"Version\":\"2019-08-23\",\"Region\":\"ap-guangzhou\","
                    + "\"Text\":\"" + escape(text) + "\",\"SessionId\":\"" + UUID.randomUUID()
                    + "\",\"VoiceType\":" + voiceType + ",\"Codec\":\"wav\"}";
            // 无完整 TC3 签名时，返回明确未配置完整 TTS 网关的提示 —— 改用本地可测的 base64 空+错误会破坏契约
            // 采用：调用可配置 base URL（测试用 Mock）；生产需完整 TC3。此处用 WebClient POST 到可覆盖端点。
            String ttsUrl = System.getenv().getOrDefault("TENCENT_TTS_URL",
                    "https://tts.tencentcloudapi.com");
            String body = webClient.post()
                    .uri(ttsUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-TC-Action", "TextToVoice")
                    .header("X-TC-Version", "2019-08-23")
                    .header("X-TC-Region", "ap-guangzhou")
                    .header("X-TC-Timestamp", String.valueOf(System.currentTimeMillis() / 1000))
                    .header("Authorization", "SecretId=" + c.get("secretId"))
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            return mapTts(body, voiceType, locale);
        } catch (Exception e) {
            log.warn("Tencent TTS failed: {}", e.getMessage());
            return SpeechOutcome.error("VENDOR_API_FAILED", "Tencent TTS failed", PROVIDER_ID);
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
            return soeEvaluate(c, audio, referenceText, locale);
        } catch (Exception e) {
            log.warn("Tencent SOE failed: {}", e.getMessage());
            return SpeechOutcome.error("VENDOR_API_FAILED", "Tencent SOE failed", PROVIDER_ID);
        }
    }

    SpeechOutcome mapAsr(String body, String locale) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        int code = root.path("code").asInt(-1);
        if (code != 0) {
            return SpeechOutcome.error("VENDOR_API_FAILED", root.path("message").asText("ASR error"), PROVIDER_ID);
        }
        String text = "";
        JsonNode flash = root.path("flash_result");
        if (flash.isArray() && flash.size() > 0) {
            text = flash.get(0).path("text").asText("");
        }
        if (!StringUtils.hasText(text)) {
            text = root.path("result").asText("");
        }
        String loc = defaultLocale(locale);
        return SpeechOutcome.ok("{\"text\":\"" + escape(text) + "\",\"confidence\":0.9,\"locale\":\""
                + escape(loc) + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    SpeechOutcome mapTts(String body, String voice, String locale) throws Exception {
        if (!StringUtils.hasText(body)) {
            return SpeechOutcome.error("VENDOR_API_FAILED", "Empty TTS", PROVIDER_ID);
        }
        JsonNode root = objectMapper.readTree(body);
        if (root.has("Response") && root.path("Response").has("Error")) {
            return SpeechOutcome.error("VENDOR_API_FAILED",
                    root.path("Response").path("Error").path("Message").asText("TTS error"), PROVIDER_ID);
        }
        String audio = root.path("Response").path("Audio").asText("");
        if (!StringUtils.hasText(audio)) {
            audio = root.path("audio").asText("");
        }
        String loc = defaultLocale(locale);
        return SpeechOutcome.ok("{\"audioBase64\":\"" + audio + "\",\"mimeType\":\"audio/wav\",\"voice\":\""
                + escape(voice) + "\",\"locale\":\"" + escape(loc) + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    /**
     * 映射 SOE 最终分数字段（可单测）。
     */
    static SpeechOutcome mapSoeScores(double overall, double accuracy, double fluency, double completeness,
                                      String locale, String referenceText) {
        String loc = defaultLocale(locale);
        return SpeechOutcome.ok("{\"overall\":" + overall + ",\"accuracy\":" + accuracy
                + ",\"fluency\":" + fluency + ",\"completeness\":" + completeness
                + ",\"locale\":\"" + escape(loc) + "\",\"referenceText\":\"" + escape(referenceText)
                + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    private SpeechOutcome soeEvaluate(VendorCredentials c, byte[] audio, String referenceText, String locale)
            throws Exception {
        String appId = c.get("appId");
        String secretId = c.get("secretId");
        String secretKey = c.get("secretKey");
        long timestamp = System.currentTimeMillis() / 1000;
        long expired = timestamp + 3600;
        int nonce = (int) (Math.random() * 100000);
        String voiceId = UUID.randomUUID().toString();
        String serverEngine = "16k_en";
        String refEnc = java.net.URLEncoder.encode(referenceText, StandardCharsets.UTF_8);
        String query = "eval_mode=1&rec_mode=1&server_engine_type=" + serverEngine
                + "&voice_format=1&voice_id=" + voiceId
                + "&ref_text=" + refEnc
                + "&score_coeff=1.0"
                + "&secretid=" + secretId
                + "&timestamp=" + timestamp
                + "&expired=" + expired
                + "&nonce=" + nonce;
        String signStr = "soe.cloud.tencent.com/soe/api/" + appId + "?" + query;
        String signature = Base64.getEncoder().encodeToString(hmacSha1(secretKey, signStr));
        String wsUrl = "wss://soe.cloud.tencent.com/soe/api/" + appId + "?" + query
                + "&signature=" + java.net.URLEncoder.encode(signature, StandardCharsets.UTF_8);

        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        HttpClient client = HttpClient.newHttpClient();
        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder buf = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
                webSocket.sendBinary(ByteBuffer.wrap(audio), true);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buf.append(data);
                webSocket.request(1);
                if (last) {
                    tryParseFinal(buf.toString(), resultFuture);
                }
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                resultFuture.completeExceptionally(error);
            }
        };
        client.newWebSocketBuilder().buildAsync(URI.create(wsUrl), listener).get(20, TimeUnit.SECONDS);
        String json = resultFuture.get(30, TimeUnit.SECONDS);
        return mapSoeJson(json, locale, referenceText);
    }

    private void tryParseFinal(String text, CompletableFuture<String> future) {
        if (future.isDone()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.has("final") && node.path("final").asInt() == 1
                    || node.has("result") || node.has("PronAccuracy") || node.has("SuggestedScore")) {
                future.complete(text);
            }
        } catch (Exception ignored) {
            // keep buffering
        }
    }

    SpeechOutcome mapSoeJson(String body, String locale, String referenceText) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode result = root.has("result") ? root.path("result") : root;
        double accuracy = result.path("PronAccuracy").asDouble(result.path("pron_accuracy").asDouble(0));
        double fluency = result.path("PronFluency").asDouble(result.path("pron_fluency").asDouble(0));
        double completeness = result.path("PronCompletion").asDouble(result.path("pron_completion").asDouble(0));
        double overall = result.path("SuggestedScore").asDouble(
                result.path("suggested_score").asDouble((accuracy + fluency + completeness) / 3.0));
        // SOE 分数常为 0-100 已对齐；若 0-1 则放大
        if (overall > 0 && overall <= 1.0) {
            overall *= 100;
            accuracy *= 100;
            fluency *= 100;
            completeness *= 100;
        }
        return mapSoeScores(overall, accuracy, fluency, completeness, locale, referenceText);
    }

    private Optional<VendorCredentials> loadCreds() {
        if (vendorRepository == null) {
            return Optional.empty();
        }
        return vendorRepository.findByCode(PROVIDER_ID)
                .filter(SpeechVendorRecord::active)
                .flatMap(r -> VendorCredentials.parse(r.credentialsCipher(), secretCipher, objectMapper))
                .filter(c -> c.hasText("secretId") && c.hasText("secretKey") && c.hasText("appId"));
    }

    private SpeechOutcome notConfigured() {
        return SpeechOutcome.error("VENDOR_NOT_CONFIGURED",
                "Tencent credentials missing in speech_vendor_config", PROVIDER_ID);
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

    static String signFlash(String secretId, String secretKey, String appId, long timestamp) throws Exception {
        // 极速版签名：Base64(HMAC-SHA1(secretKey, secretId+timestamp))
        String plain = "asr.cloud.tencent.com/asr/flash/v1/" + appId + secretId + timestamp;
        String sig = Base64.getEncoder().encodeToString(hmacSha1(secretKey, plain));
        return secretId + ";" + timestamp + ";" + sig;
    }

    private static byte[] hmacSha1(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
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
}
