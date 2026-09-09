package com.wuji.kidora.ai.cet.core.speech;

/**
 * 陪练轮次输入：text 与 audioBase64 二选一。
 *
 * @param text          文本（可空）
 * @param audioBase64   音频（可空）
 * @param locale        语言区域
 * @param referenceText 发音参考（可选）
 * @author liudy
 */
public record TurnInput(String text, String audioBase64, String locale, String referenceText) {

    /**
     * 纯文本输入。
     *
     * @param text 文本
     * @return input
     */
    public static TurnInput ofText(String text) {
        return new TurnInput(text, null, null, null);
    }
}
