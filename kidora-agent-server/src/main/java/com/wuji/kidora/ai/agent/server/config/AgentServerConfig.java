package com.wuji.kidora.ai.agent.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Agent server 运行时 Bean。
 *
 * @author liudy
 */
@Configuration
@EnableConfigurationProperties(AgentServerProperties.class)
public class AgentServerConfig {

    @Bean(name = "chatBlockingScheduler")
    public Scheduler chatBlockingScheduler(AgentServerProperties properties) {
        return Schedulers.newBoundedElastic(
                properties.getBlockingPoolSize(),
                Integer.MAX_VALUE,
                "chat-blocking");
    }
}
