package com.wuji.kidora.ai.mcp.speech;

/**
 * 语音能力供应商（stub / azure）。工具层委托本接口，稳定 JSON 字段名不变。
 *
 * @author liudy
 */
public interface SpeechProvider {

    /**
     * 供应商标识，写入响应 provider 字段。
     *
     * @return stub 或 azure
     */
    String providerId();

    /**
     * 语音转文本。
     *
     * @param audioBase64 音频 base64（与 audioUrl 二选一）
     * @param audioUrl    短时音频 URL
     * @param locale      语言区域
     * @return ASR 结果或错误
     */
    SpeechOutcome transcribe(String audioBase64, String audioUrl, String locale);

    /**
     * 文本转语音。
     *
     * @param text   待合成文本
     * @param voice  音色
     * @param locale 语言区域
     * @return TTS 结果或错误
     */
    SpeechOutcome synthesize(String text, String voice, String locale);

    /**
     * 发音评测。
     *
     * @param audioBase64   音频 base64
     * @param referenceText 参考文本
     * @param locale        语言区域
     * @return 评分结果或错误
     */
    SpeechOutcome scorePronunciation(String audioBase64, String referenceText, String locale);
}
