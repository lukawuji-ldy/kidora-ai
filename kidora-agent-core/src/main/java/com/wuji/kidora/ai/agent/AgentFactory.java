package com.wuji.kidora.ai.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitExceededException;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitExceededException;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.wuji.kidora.ai.agent.checkpoint.CheckpointSaverFactory;
import com.wuji.kidora.ai.agent.config.KidoraAgentProperties;
import com.wuji.kidora.ai.agent.model.LlmClientFactory;
import com.wuji.kidora.ai.agent.prompt.KidoraSystemPromptInterceptor;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 llm configId 构建并缓存有界 ReactAgent（PostgresSaver / MemorySaver）。
 * 本期通用 Chat 不挂 MCP/RAG 工具。
 *
 * @author liudy
 */
@Component
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final LlmClientFactory llmClientFactory;
    private final KidoraAgentProperties agentProperties;
    private final CheckpointSaverFactory checkpointSaverFactory;
    private final ConcurrentHashMap<String, ReactAgent> cache = new ConcurrentHashMap<>();

    public AgentFactory(LlmClientFactory llmClientFactory,
                        KidoraAgentProperties agentProperties,
                        CheckpointSaverFactory checkpointSaverFactory) {
        this.llmClientFactory = llmClientFactory;
        this.agentProperties = agentProperties;
        this.checkpointSaverFactory = checkpointSaverFactory;
    }

    /**
     * 获取或创建指定配置的 ReactAgent。
     *
     * @param configId llm_config.config_id
     * @return ReactAgent
     */
    public ReactAgent getOrCreate(String configId) {
        if (!StringUtils.hasText(configId)) {
            throw new KidoraException(ErrorCode.MODEL_UNAVAILABLE, "configId 不能为空");
        }
        return cache.computeIfAbsent(configId, this::build);
    }

    /**
     * 使缓存失效。
     *
     * @param configId 配置键
     */
    public void invalidate(String configId) {
        cache.remove(configId);
        log.info("ReactAgent cache invalidated, configId={}", configId);
    }

    /**
     * 单次 Agent 最大模型调用次数（配置透出）。
     *
     * @return max-model-calls
     */
    public int maxModelCalls() {
        return agentProperties.getMaxModelCalls();
    }

    /**
     * 将框架超限异常映射为业务错误码。
     *
     * @param ex 原始异常
     * @return 若为超限则包装后的异常，否则原样返回
     */
    public static RuntimeException mapLimitException(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof ModelCallLimitExceededException
                    || cur instanceof ToolCallLimitExceededException) {
                return new KidoraException(ErrorCode.AGENT_MAX_ITERATIONS,
                        ErrorCode.AGENT_MAX_ITERATIONS.getMessage(), cur);
            }
            cur = cur.getCause();
        }
        if (ex instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new KidoraException(ErrorCode.INTERNAL_ERROR,
                ex.getMessage() == null ? ErrorCode.INTERNAL_ERROR.getMessage() : ex.getMessage(), ex);
    }

    private ReactAgent build(String configId) {
        ChatModel chatModel = llmClientFactory.getChatModel(configId);
        int maxModelCalls = Math.max(1, agentProperties.getMaxModelCalls());
        int maxToolRounds = Math.max(1, agentProperties.getMaxToolRounds());
        BaseCheckpointSaver saver = checkpointSaverFactory.getSaver();

        ModelCallLimitHook modelLimit = ModelCallLimitHook.builder()
                .runLimit(maxModelCalls)
                .exitBehavior(ModelCallLimitHook.ExitBehavior.ERROR)
                .build();
        ToolCallLimitHook toolLimit = ToolCallLimitHook.builder()
                .runLimit(maxToolRounds)
                .exitBehavior(ToolCallLimitHook.ExitBehavior.ERROR)
                .build();

        String agentName = (StringUtils.hasText(agentProperties.getId())
                ? agentProperties.getId() : "kidora") + "-" + configId;

        ReactAgent agent = ReactAgent.builder()
                .name(agentName)
                .model(chatModel)
                .saver(saver)
                .hooks(modelLimit, toolLimit)
                .interceptors(new KidoraSystemPromptInterceptor())
                .build();
        log.info("ReactAgent initialized, name={}, configId={}, tools=0, maxModelCalls={}, maxToolRounds={}",
                agentName, configId, maxModelCalls, maxToolRounds);
        return agent;
    }
}
