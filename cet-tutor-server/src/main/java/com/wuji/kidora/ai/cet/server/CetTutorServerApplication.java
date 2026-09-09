package com.wuji.kidora.ai.cet.server;

import com.wuji.kidora.ai.agent.config.AgentCoreConfiguration;
import com.wuji.kidora.ai.cet.core.CetTutorCoreModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * CetTutorServerApplication.
 *
 * @author liudy
 */
@SpringBootApplication
@Import({AgentCoreConfiguration.class, CetTutorCoreModule.class})
public class CetTutorServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CetTutorServerApplication.class, args);
    }
}
