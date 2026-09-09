package com.wuji.kidora.ai.cet.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CetServerProperties.
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.cet")
public class CetServerProperties {

    private int blockingPoolSize = 16;

    public int getBlockingPoolSize() {
        return blockingPoolSize;
    }

    public void setBlockingPoolSize(int blockingPoolSize) {
        this.blockingPoolSize = blockingPoolSize;
    }
}
