package com.wuji.kidora.ai.mcp.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.common.crypto.SecretCipher;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 解密并解析供应商凭证 JSON。
 *
 * @author liudy
 */
public final class VendorCredentials {

    private final JsonNode root;

    private VendorCredentials(JsonNode root) {
        this.root = root;
    }

    /**
     * @param cipherText 密文
     * @param cipher     解密器
     * @param mapper     JSON
     * @return 凭证；空密文 empty
     */
    public static Optional<VendorCredentials> parse(String cipherText, SecretCipher cipher, ObjectMapper mapper) {
        if (!StringUtils.hasText(cipherText) || cipher == null) {
            return Optional.empty();
        }
        try {
            String plain = cipher.decrypt(cipherText);
            if (!StringUtils.hasText(plain)) {
                return Optional.empty();
            }
            return Optional.of(new VendorCredentials(mapper.readTree(plain)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String get(String field) {
        JsonNode n = root.path(field);
        return n.isMissingNode() || n.isNull() ? "" : n.asText("");
    }

    public boolean hasText(String field) {
        return StringUtils.hasText(get(field));
    }
}
