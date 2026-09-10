package com.wuji.kidora.ai.mcp.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 讯飞 WS 拼帧、TTS 音频块解码与 ISE 映射单测。
 *
 * @author liudy
 */
class IFlytekSpeechProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void accumulateWsText_buffersUntilLast() throws Exception {
        StringBuilder buf = new StringBuilder();
        // 模拟 ~4KB 边界处被拆开的 JSON（TTS/ISE 常见）
        String part1 = "{\"code\":0,\"data\":{\"audio\":\"" + "A".repeat(4000);
        String part2 = "BB\",\"status\":2}}";

        Optional<String> mid = IFlytekSpeechProvider.accumulateWsText(buf, part1, false);
        assertTrue(mid.isEmpty(), "incomplete frame must not be parsed");

        Optional<String> full = IFlytekSpeechProvider.accumulateWsText(buf, part2, true);
        assertTrue(full.isPresent());
        assertEquals(0, mapper.readTree(full.get()).path("code").asInt(-1));
        assertEquals(2, mapper.readTree(full.get()).path("data").path("status").asInt(-1));
    }

    @Test
    void decodeTtsAudioChunk_decodesEachChunkSeparately() {
        // 官方 demo：每帧 base64.b64decode 再拼二进制；字符串拼接后一次 decode 会因中间 padding 失败
        byte[] a = new byte[]{1};       // → "AQ=="
        byte[] b = new byte[]{2, 3};    // → "AgM="
        String c1 = Base64.getEncoder().encodeToString(a);
        String c2 = Base64.getEncoder().encodeToString(b);
        assertTrue(c1.contains("="), "fixture must include padding to reproduce concat bug");

        assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode(c1 + c2));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IFlytekSpeechProvider.decodeTtsAudioChunk(out, c1);
        IFlytekSpeechProvider.decodeTtsAudioChunk(out, c2);
        assertEquals(3, out.size());
        assertEquals(1, out.toByteArray()[0]);
        assertEquals(3, out.toByteArray()[2]);
    }

    @Test
    void isMp3_and_iatEncoding() {
        byte[] id3 = new byte[]{'I', 'D', '3', 3, 0};
        assertTrue(IFlytekSpeechProvider.isMp3(id3));
        assertEquals("lame", IFlytekSpeechProvider.iatEncoding(id3));

        byte[] mpeg = new byte[]{(byte) 0xFF, (byte) 0xFB, 0x10};
        assertTrue(IFlytekSpeechProvider.isMp3(mpeg));
        assertEquals("lame", IFlytekSpeechProvider.iatEncoding(mpeg));

        byte[] wav = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
        assertTrue(!IFlytekSpeechProvider.isMp3(wav));
        assertEquals("raw", IFlytekSpeechProvider.iatEncoding(wav));
    }

    @Test
    void isM4a_detectsFtyp() {
        byte[] m4a = new byte[]{0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '};
        assertTrue(IFlytekSpeechProvider.isM4a(m4a));
        assertTrue(!IFlytekSpeechProvider.isM4a(new byte[]{'R', 'I', 'F', 'F'}));
    }

    @Test
    void mapIseScores_buildsUnifiedPayload() {
        SpeechOutcome out = IFlytekSpeechProvider.mapIseScores(90, 88, 85, 92, "en-US", "Hello");
        assertTrue(out.success());
        assertTrue(out.jsonBody().contains("\"overall\":90"));
        assertTrue(out.jsonBody().contains("\"provider\":\"iflytek\""));
    }
}
