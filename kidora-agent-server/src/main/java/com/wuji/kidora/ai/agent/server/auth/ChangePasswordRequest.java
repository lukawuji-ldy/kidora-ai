package com.wuji.kidora.ai.agent.server.auth;

/**
 * 修改登录密码。
 *
 * @param currentPassword 当前密码
 * @param newPassword     新密码
 * @author liudy
 */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
