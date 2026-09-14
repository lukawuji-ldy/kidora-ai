package com.wuji.kidora.ai.agent.server.auth;

/**
 * 当前登录家长资料（不含密码）。
 *
 * @param userId   用户键
 * @param username 登录名（只读）
 * @param nickname 展示昵称
 * @param role     角色
 * @author liudy
 */
public record ProfileResponse(
        String userId,
        String username,
        String nickname,
        String role
) {
}
