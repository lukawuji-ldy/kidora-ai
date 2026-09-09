package com.wuji.kidora.ai.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Kidora MCP 工具服务入口（echo_ping + 语音 stub/db 路由）。
 * <p>
 * 默认排除 DataSource；仅 {@code kidora.speech.mode=db} 时由 {@code SpeechDbConfiguration} 建库连接。
 *
 * @author liudy
 */
@SpringBootApplication(
        scanBasePackages = "com.wuji.kidora.ai.mcp",
        exclude = DataSourceAutoConfiguration.class)
public class KidoraMcpServerApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(KidoraMcpServerApplication.class, args);
    }
}
