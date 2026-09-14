package com.wuji.kidora.ai.agent.server.auth;

/**
 * 注册请求：家长账号 + 首个儿童。
 *
 * @param username       登录用户名
 * @param password       密码
 * @param childNickname  儿童昵称（外教称呼）
 * @param englishLevel   英语水平档：BEGINNER|ELEMENTARY|INTERMEDIATE|ADVANCED
 * @author liudy
 */
public record RegisterRequest(
        String username,
        String password,
        String childNickname,
        String englishLevel
) {
}
