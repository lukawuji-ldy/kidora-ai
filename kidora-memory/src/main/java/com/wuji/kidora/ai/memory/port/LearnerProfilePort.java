package com.wuji.kidora.ai.memory.port;

import com.wuji.kidora.ai.memory.model.LearnerProfile;

import java.util.Optional;

/**
 * 学习者画像读写端口。
 *
 * @author liudy
 */
public interface LearnerProfilePort {

    /**
     * 按 learnerId 读取。
     *
     * @param learnerId 学习者
     * @return 画像
     */
    Optional<LearnerProfile> findByLearnerId(String learnerId);

    /**
     * 合并写入 extra_json。
     *
     * @param learnerId 学习者
     * @param extraJson 新 JSON（整体替换或调用方已 merge）
     */
    void updateExtraJson(String learnerId, String extraJson);
}
