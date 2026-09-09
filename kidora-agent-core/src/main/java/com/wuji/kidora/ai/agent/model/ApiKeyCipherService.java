package com.wuji.kidora.ai.agent.model;

import com.wuji.kidora.ai.agent.config.KidoraSecurityProperties;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
 * llm_config.api_key_cipher 加解密。
 *
 * @author liudy
 */
@Component
public class ApiKeyCipherService {

    public static final String PREFIX_V1 = "enc:v1:";
    public static final String PREFIX_LEGACY = "enc:";

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final KidoraSecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyCipherService(KidoraSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public String encrypt(String plain) {
        if (!StringUtils.hasText(plain)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "API Key 不能为空");
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
            throw new KidoraException(ErrorCode.INTERNAL_ERROR, "API Key 加密失败", e);
        }
    }

    public String decrypt(String stored) {
        if (!StringUtils.hasText(stored)) {
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

    private String decryptV1(String base64Payload) {
        try {
            byte[] all = Base64.getDecoder().decode(base64Payload);
            if (all.length <= GCM_IV_LENGTH) {
                throw new KidoraException(ErrorCode.INTERNAL_ERROR, "API Key 密文损坏");
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
            throw new KidoraException(ErrorCode.INTERNAL_ERROR, "API Key 解密失败", e);
        }
    }

    private SecretKey deriveKey() {
        try {
            String material = securityProperties.getApiKeySecret();
            if (!StringUtils.hasText(material)) {
                throw new KidoraException(ErrorCode.INTERNAL_ERROR, "未配置 kidora.security.api-key-secret");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (KidoraException e) {
            throw e;
        } catch (Exception e) {
            throw new KidoraException(ErrorCode.INTERNAL_ERROR, "派生 API Key 密钥失败", e);
        }
    }
}
