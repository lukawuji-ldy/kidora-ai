package com.wuji.kidora.ai.mcp.speech;

import java.util.Arrays;
import java.util.Base64;

/**
 * TTS MP3 尾帧修补：厂商（腾讯 TextToVoice、讯飞流式 lame）返回的最后一帧常缺几个字节。
 * <p>
 * Chrome 的 MP3 解封装器（Symphonia）读到不完整帧会抛
 * {@code PIPELINE_ERROR_DECODE: mpa: invalid packet length}，此时 {@code ended} 不再触发、
 * 播放停在错误点，听感是外教「最后一句没读完就停」。截掉这半帧后浏览器可正常播完并触发
 * {@code ended}。
 *
 * @author liudy
 */
public final class Mp3AudioTail {

    /** MPEG1 Layer III 每帧采样数。 */
    private static final int SAMPLES_V1 = 1152;

    /** MPEG2 / 2.5 Layer III 每帧采样数。 */
    private static final int SAMPLES_V2 = 576;

    private static final int[] BITRATE_V1_L3 = {
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};

    private static final int[] BITRATE_V2_L3 = {
            0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};

    /** 行下标为 version 位（0=MPEG2.5，1=保留，2=MPEG2，3=MPEG1）。 */
    private static final int[][] SAMPLE_RATES = {
            {11025, 12000, 8000},
            {0, 0, 0},
            {22050, 24000, 16000},
            {44100, 48000, 32000}};

    private Mp3AudioTail() {
    }

    /**
     * 截掉结尾不完整的 MP3 帧（以及帧后无法解析的残余字节）。
     *
     * @param mp3 原始 MP3 字节，可空
     * @return 以完整帧结尾的字节；解析不出任何帧时原样返回
     */
    public static byte[] trimIncompleteTrailingFrame(byte[] mp3) {
        if (mp3 == null || mp3.length < 4) {
            return mp3;
        }
        int offset = 0;
        int lastCompleteEnd = -1;
        while (offset + 4 <= mp3.length) {
            int frameLength = layer3FrameLength(mp3, offset);
            if (frameLength <= 0) {
                offset++;
                continue;
            }
            if (offset + frameLength > mp3.length) {
                break;
            }
            offset += frameLength;
            lastCompleteEnd = offset;
        }
        if (lastCompleteEnd <= 0 || lastCompleteEnd == mp3.length) {
            return mp3;
        }
        return Arrays.copyOf(mp3, lastCompleteEnd);
    }

    /**
     * 对 base64 形态的 MP3 做同样修补，解码失败则原样返回。
     *
     * @param audioBase64 base64 音频，可空
     * @return 修补后的 base64
     */
    public static String trimIncompleteTrailingFrame(String audioBase64) {
        if (audioBase64 == null || audioBase64.isBlank()) {
            return audioBase64;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(audioBase64.trim());
            byte[] trimmed = trimIncompleteTrailingFrame(raw);
            if (trimmed == raw) {
                return audioBase64;
            }
            return Base64.getEncoder().encodeToString(trimmed);
        } catch (IllegalArgumentException e) {
            return audioBase64;
        }
    }

    /**
     * 解析 Layer III 帧长度。
     *
     * @param mp3    字节
     * @param offset 起始下标
     * @return 帧字节数；此处不是合法 Layer III 帧头则 -1
     */
    static int layer3FrameLength(byte[] mp3, int offset) {
        if (offset + 4 > mp3.length) {
            return -1;
        }
        if ((mp3[offset] & 0xFF) != 0xFF || (mp3[offset + 1] & 0xE0) != 0xE0) {
            return -1;
        }
        int version = (mp3[offset + 1] >> 3) & 0x03;
        int layer = (mp3[offset + 1] >> 1) & 0x03;
        if (layer != 1 || version == 1) {
            return -1;
        }
        int bitrateIndex = (mp3[offset + 2] >> 4) & 0x0F;
        int sampleRateIndex = (mp3[offset + 2] >> 2) & 0x03;
        int padding = (mp3[offset + 2] >> 1) & 0x01;
        if (bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) {
            return -1;
        }
        boolean mpeg1 = version == 3;
        int bitrate = (mpeg1 ? BITRATE_V1_L3[bitrateIndex] : BITRATE_V2_L3[bitrateIndex]) * 1000;
        int sampleRate = SAMPLE_RATES[version][sampleRateIndex];
        if (bitrate == 0 || sampleRate == 0) {
            return -1;
        }
        int samples = mpeg1 ? SAMPLES_V1 : SAMPLES_V2;
        int frameLength = samples / 8 * bitrate / sampleRate + padding;
        return frameLength <= 4 ? -1 : frameLength;
    }
}
