package com.wuji.kidora.ai.cet.core.speech;

import java.util.Optional;

/**
 * CET 语音工具端口（由 MCP Client 适配实现；缺省无 Bean 则纯文本）。
 *
 * @author liudy
 */
public interface SpeechToolPort {

    /**
     * ASR。
     *
     * @param audioBase64 音频
     * @param locale      区域
     * @return 识别结果
     */
    Optional<AsrResult> asr(String audioBase64, String locale);

    /**
     * TTS。
     *
     * @param text   文本
     * @param voice  音色
     * @param locale 区域
     * @return 合成结果
     */
    Optional<TtsResult> tts(String text, String voice, String locale);

    /**
     * 发音评测。
     *
     * @param audioBase64   音频
     * @param referenceText 参考文本
     * @param locale        区域
     * @return 评分
     */
    Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale);

    /**
     * ASR 结果。
     *
     * @param text       文本
     * @param confidence 置信度
     * @param provider   供应商
     */
    record AsrResult(String text, double confidence, String provider) {
    }

    /**
     * TTS 结果。
     *
     * @param audioBase64 音频
     * @param mimeType    MIME
     * @param provider    供应商
     */
    record TtsResult(String audioBase64, String mimeType, String provider) {
    }

    /**
     * 发音评分。
     *
     * @param overall      总分
     * @param accuracy     准确
     * @param fluency      流利
     * @param completeness 完整
     * @param provider     供应商
     */
    record PronunciationResult(double overall, double accuracy, double fluency, double completeness, String provider) {
    }
}
