package com.wuji.kidora.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 运行时配置（max-model-calls 供后续 ReactAgent/MCP 使用）。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.agent")
public class KidoraAgentProperties {

    /**
     * 单次 Agent 最大模型调用次数（MVP Chat 无工具环，仅占位）。
     */
    private int maxModelCalls = 8;

    /**
     * 通用 Chat 入模短窗消息条数。
     */
    private int chatWindowSize = 20;

    public int getMaxModelCalls() {
        return maxModelCalls;
    }

    public void setMaxModelCalls(int maxModelCalls) {
        this.maxModelCalls = maxModelCalls;
    }

    public int getChatWindowSize() {
        return chatWindowSize;
    }

    public void setChatWindowSize(int chatWindowSize) {
        this.chatWindowSize = chatWindowSize;
    }
}
