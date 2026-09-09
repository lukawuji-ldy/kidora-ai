package com.wuji.kidora.ai.mcp.speech;

/**
 * speech_vendor_config 行。
 *
 * @param vendorCode         供应商码
 * @param name               展示名
 * @param status             ACTIVE/DISABLED
 * @param credentialsCipher  密文
 * @param extraJson          扩展 JSON 文本
 * @author liudy
 */
public record SpeechVendorRecord(
        String vendorCode,
        String name,
        String status,
        String credentialsCipher,
        String extraJson
) {
    public boolean active() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
