package com.wuji.kidora.ai.common.auth;

/**
 * 当前登录管理员（来自 Admin JWT，禁止信任请求体 adminId）。
 *
 * @param adminId   业务管理员键
 * @param username  登录名
 * @param role      SUPER_ADMIN / OPERATOR
 * @param tokenType 固定 ADMIN
 * @author liudy
 */
public record AdminAuthUser(
        String adminId,
        String username,
        String role,
        String tokenType
) {
    public static final String TOKEN_TYPE_ADMIN = "ADMIN";

    public static AdminAuthUser of(String adminId, String username, String role) {
        return new AdminAuthUser(adminId, username, role, TOKEN_TYPE_ADMIN);
    }
}
