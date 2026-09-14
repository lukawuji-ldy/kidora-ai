package com.wuji.kidora.ai.agent.server.learner;

/**
 * 新建儿童档案请求。
 *
 * @param displayName  儿童昵称
 * @param englishLevel 英语水平档位
 * @author liudy
 */
public record CreateLearnerRequest(String displayName, String englishLevel) {
}
