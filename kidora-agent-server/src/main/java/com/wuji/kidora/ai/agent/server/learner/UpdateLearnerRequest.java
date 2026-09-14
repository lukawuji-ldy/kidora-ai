package com.wuji.kidora.ai.agent.server.learner;

/**
 * 更新儿童档案请求。
 *
 * @param displayName  儿童昵称（可选）
 * @param englishLevel 英语水平档位（可选）
 * @author liudy
 */
public record UpdateLearnerRequest(String displayName, String englishLevel) {
}
