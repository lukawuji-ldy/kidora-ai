package com.wuji.kidora.ai.common.auth;

/**
 * JWT claim 常量（家长令牌不含 learnerId）。
 *
 * @author liudy
 */
public final class JwtClaims {

    public static final String USER_ID = "userId";
    public static final String USERNAME = "username";
    public static final String NICKNAME = "nickname";
    public static final String ROLE = "role";

    private JwtClaims() {
    }
}
