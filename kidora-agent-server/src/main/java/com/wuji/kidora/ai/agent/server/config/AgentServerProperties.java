package com.wuji.kidora.ai.agent.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent server 线程池等运行时参数。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.server")
public class AgentServerProperties {

    private int blockingPoolSize = 32;

    public int getBlockingPoolSize() {
        return blockingPoolSize;
    }

    public void setBlockingPoolSize(int blockingPoolSize) {
        this.blockingPoolSize = blockingPoolSize;
    }
}
