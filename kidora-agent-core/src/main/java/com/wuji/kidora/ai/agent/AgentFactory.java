package com.wuji.kidora.ai.agent;

/**
 * AgentFactory 空壳：MVP-2+ 挂载 ReactAgent / MCP 工具环时扩展。
 * 当前通用 Chat 走 {@link com.wuji.kidora.ai.agent.chat.ChatFacade}。
 *
 * @author liudy
 */
@org.springframework.stereotype.Component
public class AgentFactory {

    private final com.wuji.kidora.ai.agent.config.KidoraAgentProperties agentProperties;

    public AgentFactory(com.wuji.kidora.ai.agent.config.KidoraAgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    public int maxModelCalls() {
        return agentProperties.getMaxModelCalls();
    }
}
