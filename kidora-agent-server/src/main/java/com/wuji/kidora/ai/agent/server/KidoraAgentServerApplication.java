package com.wuji.kidora.ai.agent.server;

import com.wuji.kidora.ai.agent.config.AgentCoreConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * KidoraAgentServerApplication.
 *
 * @author liudy
 */
@SpringBootApplication
@Import(AgentCoreConfiguration.class)
public class KidoraAgentServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KidoraAgentServerApplication.class, args);
    }
}
