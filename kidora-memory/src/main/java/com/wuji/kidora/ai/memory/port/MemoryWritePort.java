package com.wuji.kidora.ai.memory.port;

import java.util.List;

/**
 * 长期记忆写入端口（Memory Action）。
 *
 * @author liudy
 */
public interface MemoryWritePort {

    /**
     * 动作类型。
     */
    enum Action {
        INSERT,
        UPDATE,
        MERGE,
        DELETE,
        IGNORE
    }

    /**
     * 写入一条语义记忆短事实。
     *
     * @param learnerId       学习者
     * @param content         内容
     * @param sourceSessionId 来源会话
     * @param extraJson       扩展
     * @return memoryId；IGNORE 时可为 null
     */
    String writeSemantic(String learnerId, String content, String sourceSessionId, String extraJson);

    /**
     * 最近语义命中。
     *
     * @param learnerId 学习者
     * @param limit     条数
     * @return 内容列表
     */
    List<String> listRecentSemantic(String learnerId, int limit);
}
