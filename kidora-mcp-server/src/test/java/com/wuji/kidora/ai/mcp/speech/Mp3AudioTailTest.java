package com.wuji.kidora.ai.mcp.speech;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * TTS MP3 尾帧修补单测（厂商 mp3 末帧缺字节会让浏览器 PIPELINE_ERROR_DECODE 掐掉尾句）。
 *
 * @author liudy
 */
class Mp3AudioTailTest {

    /** MPEG2 Layer III / 16kHz / 32kbps / 单声道 → 每帧 144 字节（腾讯 TextToVoice 实际参数）。 */
    private static final byte[] FRAME_HEADER = {(byte) 0xFF, (byte) 0xF3, (byte) 0x48, (byte) 0xC0};

    private static final int FRAME_LENGTH = 144;

    @Test
    void layer3FrameLength_readsTencentMp3Frame() {
        assertEquals(FRAME_LENGTH, Mp3AudioTail.layer3FrameLength(frames(1), 0));
    }

    @Test
    void trim_dropsPartialTrailingFrame() {
        byte[] complete = frames(3);
        // 腾讯实测：最后一帧只回了 142/144 字节
        byte[] truncated = Arrays.copyOf(frames(4), 3 * FRAME_LENGTH + 142);

        byte[] fixed = Mp3AudioTail.trimIncompleteTrailingFrame(truncated);

        assertEquals(3 * FRAME_LENGTH, fixed.length);
        assertArrayEquals(complete, fixed);
    }

    @Test
    void trim_keepsCompleteStreamUntouched() {
        byte[] complete = frames(5);

        assertSame(complete, Mp3AudioTail.trimIncompleteTrailingFrame(complete));
    }

    @Test
    void trim_returnsInputWhenNoFrameParsed() {
        byte[] garbage = {1, 2, 3, 4, 5, 6};

        assertSame(garbage, Mp3AudioTail.trimIncompleteTrailingFrame(garbage));
    }

    @Test
    void trim_handlesNullAndTiny() {
        assertSame(null, Mp3AudioTail.trimIncompleteTrailingFrame((byte[]) null));
        byte[] tiny = {(byte) 0xFF, (byte) 0xF3};
        assertSame(tiny, Mp3AudioTail.trimIncompleteTrailingFrame(tiny));
    }

    @Test
    void trimBase64_reencodesTrimmedAudio() {
        byte[] truncated = Arrays.copyOf(frames(3), 2 * FRAME_LENGTH + 100);
        String base64 = Base64.getEncoder().encodeToString(truncated);

        String fixed = Mp3AudioTail.trimIncompleteTrailingFrame(base64);

        assertEquals(2 * FRAME_LENGTH, Base64.getDecoder().decode(fixed).length);
    }

    @Test
    void trimBase64_passesThroughBlankAndInvalid() {
        assertSame("", Mp3AudioTail.trimIncompleteTrailingFrame(""));
        assertSame("not base64 ***", Mp3AudioTail.trimIncompleteTrailingFrame("not base64 ***"));
    }

    private static byte[] frames(int count) {
        byte[] out = new byte[count * FRAME_LENGTH];
        for (int i = 0; i < count; i++) {
            System.arraycopy(FRAME_HEADER, 0, out, i * FRAME_LENGTH, FRAME_HEADER.length);
        }
        return out;
    }
}
