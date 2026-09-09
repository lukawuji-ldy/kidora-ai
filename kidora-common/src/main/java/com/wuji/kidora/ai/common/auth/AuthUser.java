package com.wuji.kidora.ai.common.auth;

/**
 * 当前登录用户（来自 JWT，禁止信任请求体 userId）。
 *
 * @param userId   业务用户键
 * @param username 登录名
 * @param nickname 昵称
 * @param role     角色（parent|teacher 等）
 *
 * @author liudy
 */
public record AuthUser(
        String userId,
        String username,
        String nickname,
        String role
) {
}
