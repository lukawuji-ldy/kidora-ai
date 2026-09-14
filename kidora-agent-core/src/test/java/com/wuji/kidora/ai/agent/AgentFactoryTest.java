package com.wuji.kidora.ai.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitExceededException;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.wuji.kidora.ai.agent.checkpoint.CheckpointSaverFactory;
import com.wuji.kidora.ai.agent.config.KidoraAgentProperties;
import com.wuji.kidora.ai.agent.model.LlmClientFactory;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentFactory 单元测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentFactoryTest {

    @Mock
    private LlmClientFactory llmClientFactory;

    @Mock
    private CheckpointSaverFactory checkpointSaverFactory;

    private AgentFactory agentFactory;

    @BeforeEach
    void setUp() {
        KidoraAgentProperties props = new KidoraAgentProperties();
        props.setId("kidora");
        props.setMaxModelCalls(8);
        props.setMaxToolRounds(8);
        props.getCheckpoint().setType("memory");
        when(checkpointSaverFactory.getSaver()).thenReturn(new MemorySaver());
        agentFactory = new AgentFactory(llmClientFactory, props, checkpointSaverFactory);
    }

    @Test
    void getOrCreate_cachesSameConfigId() {
        ChatModel chatModel = mock(ChatModel.class);
        when(llmClientFactory.getChatModel("llm_primary")).thenReturn(chatModel);

        ReactAgent first = agentFactory.getOrCreate("llm_primary");
        ReactAgent second = agentFactory.getOrCreate("llm_primary");
        assertSame(first, second);
        verify(llmClientFactory, times(1)).getChatModel("llm_primary");
    }

    @Test
    void maxModelCalls_fromProperties() {
        assertEquals(8, agentFactory.maxModelCalls());
    }

    @Test
    void mapLimitException_wrapsModelCallLimit() {
        RuntimeException mapped = AgentFactory.mapLimitException(
                new ModelCallLimitExceededException(0, 8, null, 8));
        assertInstanceOf(KidoraException.class, mapped);
        assertEquals(ErrorCode.AGENT_MAX_ITERATIONS, ((KidoraException) mapped).getErrorCode());
    }
}
