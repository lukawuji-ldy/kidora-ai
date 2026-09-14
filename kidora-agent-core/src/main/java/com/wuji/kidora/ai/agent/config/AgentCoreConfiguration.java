package com.wuji.kidora.ai.agent.config;

import com.wuji.kidora.ai.agent.model.ApiKeyCipherService;
import com.wuji.kidora.ai.agent.model.LlmCallAuditor;
import com.wuji.kidora.ai.agent.model.LlmClientFactory;
import com.wuji.kidora.ai.agent.model.LlmConfigRepository;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.memory.MemoryModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Agent Core 自动装配入口。
 *
 * @author liudy
 */
@Configuration
@EnableConfigurationProperties({KidoraModelProperties.class, KidoraSecurityProperties.class, KidoraAgentProperties.class})
@Import({
        MemoryModule.class,
        LlmConfigRepository.class,
        ApiKeyCipherService.class,
        LlmClientFactory.class,
        LlmCallAuditor.class,
        ModelRouter.class,
        PromptTemplateService.class,
        com.wuji.kidora.ai.agent.checkpoint.CheckpointSaverFactory.class,
        com.wuji.kidora.ai.agent.chat.ChatSessionRepository.class,
        com.wuji.kidora.ai.agent.chat.ChatMessageRepository.class,
        com.wuji.kidora.ai.agent.chat.ChatFacade.class,
        com.wuji.kidora.ai.agent.AgentFactory.class
})
public class AgentCoreConfiguration {
}
