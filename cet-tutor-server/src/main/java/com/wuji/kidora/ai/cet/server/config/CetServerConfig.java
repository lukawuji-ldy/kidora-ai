package com.wuji.kidora.ai.cet.server.config;

import com.wuji.kidora.ai.common.auth.JwtTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * CetServerConfig.
 *
 * @author liudy
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, CetServerProperties.class, CetPropsProperties.class,
        com.wuji.kidora.ai.cet.server.mcp.KidoraMcpProperties.class})
public class CetServerConfig {

    @Bean
    public JwtTokenService jwtTokenService(JwtProperties properties) {
        return new JwtTokenService(properties.getSecret(), properties.getIssuer(), properties.getExpireHours());
    }

    @Bean(name = "cetBlockingScheduler")
    public Scheduler cetBlockingScheduler(CetServerProperties properties) {
        return Schedulers.newBoundedElastic(
                properties.getBlockingPoolSize(),
                Integer.MAX_VALUE,
                "cet-blocking");
    }
}
