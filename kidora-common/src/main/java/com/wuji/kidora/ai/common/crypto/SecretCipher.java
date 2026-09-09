package com.wuji.kidora.ai.common.crypto;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 通用密钥材料加解密（AES-GCM），供 LLM Key / 语音供应商凭证等复用。
 * <p>
 * 密文前缀：{@code enc:v1:} + Base64(iv||ciphertext)；兼容 {@code enc:} 明文遗留。
 *
 * @author liudy
 */
public final class SecretCipher {

    public static final String PREFIX_V1 = "enc:v1:";
    public static final String PREFIX_LEGACY = "enc:";

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String secretMaterial;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param secretMaterial 与 {@code kidora.security.api-key-secret} / KIDORA_API_KEY_SECRET 相同
     */
    public SecretCipher(String secretMaterial) {
        this.secretMaterial = secretMaterial;
    }

    /**
     * 加密明文。
     *
     * @param plain 明文
     * @return 带前缀密文
     */
    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "密钥材料不能为空");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherText.length);
            buf.put(iv);
            buf.put(cipherText);
            return PREFIX_V1 + Base64.getEncoder().encodeToString(buf.array());
        } catch (KidoraException e) {
            throw e;
        } catch (Exception e) {
            throw new KidoraException(ErrorCode.INTERNAL_ERROR, "密钥加密失败", e);
        }
    }

    /**
     * 解密存储值。
     *
     * @param stored 密文或明文遗留
     * @return 明文；空输入返回空串
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        if (stored.startsWith(PREFIX_V1)) {
            return decryptV1(stored.substring(PREFIX_V1.length()));
        }
        if (stored.startsWith(PREFIX_LEGACY)) {
            return stored.substring(PREFIX_LEGACY.length());
        }
        return stored;
    }

    /**
     * 脱敏：****** + 明文末 4 位。
     *
     * @param stored 存储值
     * @return 脱敏串
     */
    public String mask(String stored) {
        String plain = decrypt(stored);
        if (plain == null || plain.isBlank() || plain.length() < 4) {
            return "******";
        }
        return "******" + plain.substring(plain.length() - 4);
    }

    private String decryptV1(String base64Payload) {
        try {
            byte[] all = Base64.getDecoder().decode(base64Payload);
            if (all.length <= GCM_IV_LENGTH) {
                throw new KidoraException(ErrorCode.INTERNAL_ERROR, "密钥密文损坏");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, GCM_IV_LENGTH);
            byte[] cipherText = new byte[all.length - GCM_IV_LENGTH];
            System.arraycopy(all, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (KidoraException e) {
            throw e;
        } catch (Exception e) {
            throw new KidoraException(ErrorCode.INTERNAL_ERROR, "密钥解密失败", e);
        }
    }

    private SecretKey deriveKey() {
        try {
            if (secretMaterial == null || secretMaterial.isBlank()) {
                throw new KidoraException(ErrorCode.INTERNAL_ERROR, "未配置 kidora.security.api-key-secret");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secretMaterial.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (KidoraException e) {
            throw e;
        } catch (Exception e) {
            throw new KidoraException(ErrorCode.INTERNAL_ERROR, "派生加密密钥失败", e);
        }
    }
}
