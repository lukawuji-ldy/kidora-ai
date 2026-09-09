package com.wuji.kidora.ai.agent.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 刷新本地演示家长账号密码为 parent123（与 V2 seed 配套）。
 *
 * @author liudy
 */
@Component
public class DevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DevSeedRunner(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String hash = passwordEncoder.encode("parent123");
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbcTemplate.update("""
                UPDATE app_user SET password_hash = ?, update_time = ?
                WHERE username = 'parent1' AND deleted = FALSE
                """, hash, now);
        if (updated > 0) {
            log.info("refreshed demo parent1 password hash (local password parent123)");
        }
    }
}
