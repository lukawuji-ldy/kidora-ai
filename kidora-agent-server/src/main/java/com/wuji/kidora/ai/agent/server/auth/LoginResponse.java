package com.wuji.kidora.ai.agent.server.auth;

/**
 * 登录/注册响应。
 *
 * @param token      JWT
 * @param tokenType  固定 Bearer
 * @param userId     业务用户键
 * @param username   登录名
 * @param nickname   家长展示名
 * @param role       角色
 * @param learnerId  注册时首个学习者；登录可为 null
 * @author liudy
 */
public record LoginResponse(
        String token,
        String tokenType,
        String userId,
        String username,
        String nickname,
        String role,
        String learnerId
) {
    /**
     * 兼容无 learnerId 的登录构造。
     *
     * @author liudy
     */
    public LoginResponse(String token, String tokenType, String userId, String username,
                         String nickname, String role) {
        this(token, tokenType, userId, username, nickname, role, null);
    }
}
