package com.wuji.kidora.ai.mcp.speech;

/**
 * speech_route 单行。
 *
 * @param primaryVendor  主
 * @param backupVendor   备（不自动切换）
 * @param tertiaryVendor 第三档
 * @author liudy
 */
public record SpeechRouteRecord(
        String primaryVendor,
        String backupVendor,
        String tertiaryVendor
) {
}
