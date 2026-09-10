package com.wuji.kidora.ai.mcp.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.common.crypto.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
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
            if (appId == null || !appId.chars().allMatch(Character::isDigit)) {
                return SpeechOutcome.error("INVALID_CONFIG",
                        "Tencent appId must be numeric, got: " + appId, PROVIDER_ID);
            }
            if (appId.startsWith("1000") && appId.length() >= 12) {
                return SpeechOutcome.error("INVALID_CONFIG",
                        "Tencent appId looks like account Uin (" + appId
                                + "); use AppId from CAM API key page", PROVIDER_ID);
            }
            long ts = System.currentTimeMillis() / 1000;
            String voiceFormat = detectVoiceFormatName(audio);
            String query = buildFlashQuery(c.get("secretId"), ts, voiceFormat);
            String url = "https://asr.cloud.tencent.com/asr/flash/v1/" + appId + "?" + query;
            String sign = signFlash(c.get("secretKey"), appId, query);
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
            String voiceType = StringUtils.hasText(voice) ? voice : "101001";
            long timestamp = Instant.now().getEpochSecond();
            String ttsUrl = System.getenv().getOrDefault("TENCENT_TTS_URL",
                    "https://tts.tencentcloudapi.com");
            String host = hostFromUrl(ttsUrl, "tts.tencentcloudapi.com");
            int voiceTypeInt;
            try {
                voiceTypeInt = Integer.parseInt(voiceType.trim());
            } catch (NumberFormatException e) {
                voiceTypeInt = 101001;
            }
            String payload = objectMapper.createObjectNode()
                    .put("Text", text)
                    .put("SessionId", UUID.randomUUID().toString())
                    .put("VoiceType", voiceTypeInt)
                    .put("Codec", "wav")
                    .toString();
            String authorization = tc3Authorization(c.get("secretId"), c.get("secretKey"), "tts", host, payload, timestamp);
            String body = webClient.post()
                    .uri(ttsUrl)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("Host", host)
                    .header("X-TC-Action", "TextToVoice")
                    .header("X-TC-Version", "2019-08-23")
                    .header("X-TC-Region", "ap-guangzhou")
                    .header("X-TC-Timestamp", String.valueOf(timestamp))
                    .header("Authorization", authorization)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            return mapTts(body, String.valueOf(voiceTypeInt), locale);
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
        if (appId == null || !appId.chars().allMatch(Character::isDigit)) {
            return SpeechOutcome.error("INVALID_CONFIG",
                    "Tencent appId must be numeric, got: " + appId, PROVIDER_ID);
        }
        if (appId.startsWith("1000") && appId.length() >= 12) {
            return SpeechOutcome.error("INVALID_CONFIG",
                    "Tencent appId looks like account Uin (" + appId
                            + "); use AppId from CAM API key page", PROVIDER_ID);
        }
        int voiceFormat = detectSoeVoiceFormat(audio);
        if (voiceFormat < 0) {
            return SpeechOutcome.error("INVALID_AUDIO", "SOE does not support m4a; use wav/mp3/pcm", PROVIDER_ID);
        }
        String secretId = c.get("secretId");
        String secretKey = c.get("secretKey");
        long timestamp = System.currentTimeMillis() / 1000;
        long expired = timestamp + 3600;
        int nonce = (int) (Math.random() * 100000);
        String voiceId = UUID.randomUUID().toString();
        java.util.TreeMap<String, String> params = new java.util.TreeMap<>();
        params.put("eval_mode", "1");
        params.put("expired", String.valueOf(expired));
        params.put("nonce", String.valueOf(nonce));
        // 录音模式允许单片大音频；仍须等握手 ready 后再发，结束发 {"type":"end"}
        params.put("rec_mode", "1");
        params.put("ref_text", referenceText);
        params.put("score_coeff", "1.0");
        params.put("secretid", secretId);
        params.put("server_engine_type", "16k_en");
        params.put("timestamp", String.valueOf(timestamp));
        params.put("voice_format", String.valueOf(voiceFormat));
        params.put("voice_id", voiceId);
        // 签名原文用未编码参数；URL 再 percent-encode（与官方 SOE 文档一致）
        String wsUrl = soeWsUrl("wss://soe.cloud.tencent.com", appId, secretKey, params);

        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        CompletableFuture<Void> handshakeReady = new CompletableFuture<>();
        String[] lastScoreMsg = new String[1];
        HttpClient client = HttpClient.newHttpClient();
        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder buf = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                // 官方协议：等服务端 code=0 握手包后再发音频；onOpen 立即 send 会被丢弃 → 4008
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buf.append(data);
                webSocket.request(1);
                if (last) {
                    String msg = buf.toString();
                    buf.setLength(0);
                    tryParseSoeMessage(msg, handshakeReady, resultFuture, lastScoreMsg);
                }
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                handshakeReady.completeExceptionally(error);
                resultFuture.completeExceptionally(error);
            }
        };
        WebSocket ws = client.newWebSocketBuilder().buildAsync(URI.create(wsUrl), listener)
                .get(20, TimeUnit.SECONDS);
        try {
            try {
                handshakeReady.get(15, TimeUnit.SECONDS);
            } catch (Exception handshakeEx) {
                if (resultFuture.isDone()) {
                    return mapSoeJson(resultFuture.getNow(null), locale, referenceText);
                }
                throw handshakeEx;
            }
            ws.sendBinary(ByteBuffer.wrap(audio), true).get(15, TimeUnit.SECONDS);
            ws.sendText(soeEndText(), true).get(5, TimeUnit.SECONDS);
            String json = resultFuture.get(30, TimeUnit.SECONDS);
            return mapSoeJson(json, locale, referenceText);
        } finally {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "soe done");
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    /**
     * 官方握手成功：code=0 且尚未 final=1；此时才可发送 binary 音频。
     */
    static boolean isSoeHandshakeReady(JsonNode node) {
        return node != null
                && node.path("code").asInt(-1) == 0
                && node.path("final").asInt(0) != 1;
    }

    /** 音频发完后必须发的结束文本帧（官方 OralEvaluator.stop）。 */
    static String soeEndText() {
        return "{\"type\":\"end\"}";
    }

    private void tryParseSoeMessage(String text, CompletableFuture<Void> handshakeReady,
                                    CompletableFuture<String> resultFuture, String[] lastScoreMsg) {
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.path("code").asInt(0) != 0) {
                handshakeReady.completeExceptionally(new IllegalStateException(
                        node.path("message").asText("SOE error")));
                if (!resultFuture.isDone()) {
                    resultFuture.complete(text);
                }
                return;
            }
            if (!handshakeReady.isDone() && isSoeHandshakeReady(node)) {
                handshakeReady.complete(null);
            }
            if (resultFuture.isDone()) {
                return;
            }
            JsonNode result = node.path("result");
            if (!result.isMissingNode() && !result.isNull()) {
                String raw = result.isTextual() ? result.asText() : result.toString();
                if (raw.contains("PronAccuracy") || raw.contains("SuggestedScore")
                        || raw.contains("pron_accuracy") || raw.contains("suggested_score")) {
                    lastScoreMsg[0] = text;
                }
            }
            if (node.has("final") && node.path("final").asInt() == 1) {
                resultFuture.complete(lastScoreMsg[0] != null ? lastScoreMsg[0] : text);
            }
        } catch (Exception ignored) {
            // keep buffering
        }
    }

    SpeechOutcome mapSoeJson(String body, String locale, String referenceText) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (root.path("code").asInt(0) != 0) {
            return SpeechOutcome.error("VENDOR_API_FAILED",
                    root.path("message").asText("SOE error"), PROVIDER_ID);
        }
        JsonNode result = root.has("result") ? root.path("result") : root;
        double accuracy;
        double fluency;
        double completeness;
        double overall;
        if (result.isTextual()) {
            String raw = result.asText("");
            overall = extractGoStructDouble(raw, "SuggestedScore");
            accuracy = extractGoStructDouble(raw, "PronAccuracy");
            fluency = extractGoStructDouble(raw, "PronFluency");
            completeness = extractGoStructDouble(raw, "PronCompletion");
            if (Double.isNaN(overall) && Double.isNaN(accuracy)) {
                return SpeechOutcome.error("VENDOR_UNEXPECTED_PAYLOAD",
                        "SOE response missing score fields", PROVIDER_ID);
            }
            overall = Double.isNaN(overall) ? 0 : overall;
            accuracy = Double.isNaN(accuracy) ? 0 : accuracy;
            fluency = Double.isNaN(fluency) ? 0 : fluency;
            completeness = Double.isNaN(completeness) ? 0 : completeness;
        } else {
            accuracy = result.path("PronAccuracy").asDouble(result.path("pron_accuracy").asDouble(0));
            fluency = result.path("PronFluency").asDouble(result.path("pron_fluency").asDouble(0));
            completeness = result.path("PronCompletion").asDouble(result.path("pron_completion").asDouble(0));
            overall = result.path("SuggestedScore").asDouble(
                    result.path("suggested_score").asDouble((accuracy + fluency + completeness) / 3.0));
        }
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

    static String signFlash(String secretKey, String appId, String sortedQuery) throws Exception {
        String plain = "POSTasr.cloud.tencent.com/asr/flash/v1/" + appId + "?" + sortedQuery;
        return Base64.getEncoder().encodeToString(hmacSha1(secretKey, plain));
    }

    /**
     * 智聆 SOE：签名用未编码字典序 query；请求 URL 再对 value/signature 百分号编码。
     */
    static String soeWsUrl(String wsBase, String appId, String secretKey,
                           java.util.SortedMap<String, String> params) throws Exception {
        String rawQuery = joinQuery(params, false);
        String signStr = "soe.cloud.tencent.com/soe/api/" + appId + "?" + rawQuery;
        String signature = Base64.getEncoder().encodeToString(hmacSha1(secretKey, signStr));
        return wsBase + "/soe/api/" + appId + "?" + joinQuery(params, true)
                + "&signature=" + percentEncode(signature);
    }

    static String joinQuery(java.util.SortedMap<String, String> params, boolean encodeValues) {
        StringBuilder sb = new StringBuilder();
        for (var e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            String value = e.getValue() == null ? "" : e.getValue();
            sb.append(e.getKey()).append('=')
                    .append(encodeValues ? percentEncode(value) : value);
        }
        return sb.toString();
    }

    static String percentEncode(String raw) {
        return java.net.URLEncoder.encode(raw, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    static String buildFlashQuery(String secretId, long timestamp, String voiceFormat) {
        java.util.TreeMap<String, String> params = new java.util.TreeMap<>();
        params.put("convert_num_mode", "1");
        params.put("engine_type", "16k_en");
        params.put("filter_dirty", "0");
        params.put("filter_modal", "0");
        params.put("filter_punc", "0");
        params.put("first_channel_only", "1");
        params.put("secretid", secretId);
        params.put("speaker_diarization", "0");
        params.put("timestamp", String.valueOf(timestamp));
        params.put("voice_format", voiceFormat);
        params.put("word_info", "0");
        StringBuilder sb = new StringBuilder();
        for (var e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    static String detectVoiceFormatName(byte[] audio) {
        if (isWav(audio)) {
            return "wav";
        }
        if (isMp3(audio)) {
            return "mp3";
        }
        if (isM4a(audio)) {
            return "m4a";
        }
        return "pcm";
    }

    static int detectSoeVoiceFormat(byte[] audio) {
        if (isWav(audio)) {
            return 1;
        }
        if (isMp3(audio)) {
            return 2;
        }
        if (isM4a(audio)) {
            return -1;
        }
        return 0;
    }

    static boolean isWav(byte[] audio) {
        return audio != null && audio.length >= 12
                && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F'
                && audio[8] == 'W' && audio[9] == 'A' && audio[10] == 'V' && audio[11] == 'E';
    }

    static boolean isMp3(byte[] audio) {
        if (audio == null || audio.length < 3) {
            return false;
        }
        if (audio[0] == 'I' && audio[1] == 'D' && audio[2] == '3') {
            return true;
        }
        return (audio[0] & 0xFF) == 0xFF && (audio[1] & 0xE0) == 0xE0;
    }

    static boolean isM4a(byte[] audio) {
        return audio != null && audio.length >= 8
                && audio[4] == 'f' && audio[5] == 't' && audio[6] == 'y' && audio[7] == 'p';
    }

    static double extractGoStructDouble(String raw, String field) {
        if (!StringUtils.hasText(raw) || !StringUtils.hasText(field)) {
            return Double.NaN;
        }
        String marker = field + ":";
        int i = raw.indexOf(marker);
        if (i < 0) {
            return Double.NaN;
        }
        int s = i + marker.length();
        int e = s;
        while (e < raw.length()) {
            char ch = raw.charAt(e);
            if ((ch >= '0' && ch <= '9') || ch == '-' || ch == '+' || ch == '.' || ch == 'e' || ch == 'E') {
                e++;
            } else {
                break;
            }
        }
        if (e == s) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(raw.substring(s, e));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    /**
     * 腾讯云 API 3.0 TC3-HMAC-SHA256（TTS TextToVoice）。
     */
    static String tc3Authorization(String secretId, String secretKey, String service, String host,
                                   String payload, long timestamp) throws Exception {
        String date = Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String hashedPayload = sha256Hex(payload);
        String canonicalHeaders = "content-type:application/json\nhost:" + host + "\n";
        String signedHeaders = "content-type;host";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedPayload;
        String credentialScope = date + "/" + service + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, service);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));
        return "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    private static byte[] hmacSha1(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hostFromUrl(String baseUrl, String fallback) {
        try {
            URI uri = URI.create(baseUrl);
            if (StringUtils.hasText(uri.getHost())) {
                return uri.getHost();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return fallback;
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
