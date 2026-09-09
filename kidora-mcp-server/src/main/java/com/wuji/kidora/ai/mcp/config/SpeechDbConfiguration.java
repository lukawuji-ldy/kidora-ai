package com.wuji.kidora.ai.mcp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * 仅 kidora.speech.mode=db 时启用 JDBC（读 speech_*）。
 *
 * @author liudy
 */
@Configuration
@ConditionalOnProperty(prefix = "kidora.speech", name = "mode", havingValue = "db")
public class SpeechDbConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties speechDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource dataSource(DataSourceProperties speechDataSourceProperties) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(speechDataSourceProperties.determineDriverClassName());
        ds.setUrl(speechDataSourceProperties.determineUrl());
        ds.setUsername(speechDataSourceProperties.determineUsername());
        ds.setPassword(speechDataSourceProperties.determinePassword());
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
