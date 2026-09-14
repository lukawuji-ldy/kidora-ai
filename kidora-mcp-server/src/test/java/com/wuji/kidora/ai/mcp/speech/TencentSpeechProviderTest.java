package com.wuji.kidora.ai.mcp.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 腾讯 ASR/SOE 签名与映射单测。
 *
 * @author liudy
 */
class TencentSpeechProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void soeHandshakeReady_acceptsCode0BeforeFinal() throws Exception {
        assertTrue(TencentSpeechProvider.isSoeHandshakeReady(
                mapper.readTree("{\"code\":0,\"voice_id\":\"v1\",\"message\":\"success\"}")));
        assertFalse(TencentSpeechProvider.isSoeHandshakeReady(
                mapper.readTree("{\"code\":0,\"final\":1,\"result\":{}}")));
        assertFalse(TencentSpeechProvider.isSoeHandshakeReady(
                mapper.readTree("{\"code\":4008,\"message\":\"客户端超过15秒未发送音频数据\"}")));
    }

    @Test
    void soeEndText_isOfficialStopPayload() {
        assertEquals("{\"type\":\"end\"}", TencentSpeechProvider.soeEndText());
    }

    @Test
    void soeWsUrlSignsRawQueryAndEncodesRefTextInUrl() throws Exception {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("eval_mode", "1");
        params.put("expired", "1700003600");
        params.put("nonce", "42");
        params.put("rec_mode", "1");
        params.put("ref_text", "Hello, how are you?");
        params.put("score_coeff", "1.0");
        params.put("secretid", "sid");
        params.put("server_engine_type", "16k_en");
        params.put("timestamp", "1700000000");
        params.put("voice_format", "1");
        params.put("voice_id", "vid");
        String raw = TencentSpeechProvider.joinQuery(params, false);
        assertTrue(raw.contains("ref_text=Hello, how are you?"));
        assertFalse(raw.contains("%20"));
        String url = TencentSpeechProvider.soeWsUrl("wss://soe.cloud.tencent.com", "1259000000", "skey", params);
        assertTrue(url.startsWith("wss://soe.cloud.tencent.com/soe/api/1259000000?"));
        assertTrue(url.contains("ref_text=Hello%2C%20how%20are%20you%3F"));
        assertTrue(url.contains("&signature="));
        assertFalse(url.contains("ref_text=Hello, how are you?"));
    }

    @Test
    void signFlashProducesBase64() throws Exception {
        String query = TencentSpeechProvider.buildFlashQuery("sid", 1700000000L, "wav");
        String sign = TencentSpeechProvider.signFlash("skey", "1259000000", query);
        assertTrue(sign.length() > 10);
        assertFalse(sign.contains(" "));
    }

    @Test
    void buildFlashQuery_usesEngineType() {
        String en = TencentSpeechProvider.buildFlashQuery("sid", 1700000000L, "wav");
        assertTrue(en.contains("engine_type=16k_en"));
        String zh = TencentSpeechProvider.buildFlashQuery("sid", 1700000000L, "wav", "16k_zh");
        assertTrue(zh.contains("engine_type=16k_zh"));
        assertFalse(zh.contains("engine_type=16k_en"));
    }
}
