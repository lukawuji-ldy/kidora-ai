package com.wuji.kidora.ai.agent.server.auth;

import com.wuji.kidora.ai.common.auth.AuthUser;
import com.wuji.kidora.ai.common.auth.JwtTokenService;
import com.wuji.kidora.ai.agent.server.config.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JwtConfig.
 *
 * @author liudy
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtTokenService jwtTokenService(JwtProperties properties) {
        return new JwtTokenService(properties.getSecret(), properties.getIssuer(), properties.getExpireHours());
    }
}
