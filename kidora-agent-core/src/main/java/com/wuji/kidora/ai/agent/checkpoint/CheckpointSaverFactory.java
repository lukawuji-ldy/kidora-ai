package com.wuji.kidora.ai.agent.checkpoint;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import com.wuji.kidora.ai.agent.config.KidoraAgentProperties;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 按配置构建 Checkpoint Saver（PostgresSaver / MemorySaver）。
 *
 * @author liudy
 */
@Component
public class CheckpointSaverFactory {

    private static final Logger log = LoggerFactory.getLogger(CheckpointSaverFactory.class);

    /**
     * PG 未加引号建表时关系名会折叠为小写；探测须覆盖小写表名与官方索引名。
     * SAA 1.1.2.2 的 {@code CREATE INDEX} 无 {@code IF NOT EXISTS}，表已存在时再 init 会直接失败。
     */
    public static final String SCHEMA_PROBE_SQL = """
            SELECT to_regclass('public.graphcheckpoint') IS NOT NULL
                OR to_regclass('public."GraphCheckpoint"') IS NOT NULL
                OR to_regclass('public.idx_lg4jcheckpoint_thread_id') IS NOT NULL
            """;

    private final KidoraAgentProperties agentProperties;
    private volatile BaseCheckpointSaver cached;

    public CheckpointSaverFactory(KidoraAgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * 获取进程内单例 Saver。
     *
     * @return Saver
     */
    public BaseCheckpointSaver getSaver() {
        BaseCheckpointSaver local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached == null) {
                cached = create();
            }
            return cached;
        }
    }

    private BaseCheckpointSaver create() {
        KidoraAgentProperties.Checkpoint cp = agentProperties.getCheckpoint();
        String type = cp.getType() == null ? "postgres" : cp.getType().trim().toLowerCase();
        if ("memory".equals(type)) {
            log.info("Checkpoint saver=MemorySaver");
            return new MemorySaver();
        }
        if (!"postgres".equals(type)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "不支持的 checkpoint.type: " + type);
        }
        boolean createTables = cp.isCreateTables() && !schemaAlreadyApplied(cp);
        try {
            PostgresSaver saver = PostgresSaver.builder()
                    .host(cp.getHost())
                    .port(cp.getPort())
                    .user(cp.getUsername())
                    .password(cp.getPassword())
                    .database(cp.getDatabase())
                    .createTables(createTables)
                    .dropTablesFirst(false)
                    .build();
            log.info("Checkpoint saver=PostgresSaver host={} db={} createTables={}",
                    cp.getHost(), cp.getDatabase(), createTables);
            return saver;
        } catch (Exception e) {
            throw new KidoraException(ErrorCode.INTERNAL_ERROR, "初始化 PostgresSaver 失败: " + e.getMessage(), e);
        }
    }

    private boolean schemaAlreadyApplied(KidoraAgentProperties.Checkpoint cp) {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:postgresql://" + cp.getHost() + ":" + cp.getPort() + "/" + cp.getDatabase(),
                cp.getUsername(), cp.getPassword());
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(SCHEMA_PROBE_SQL)) {
            rs.next();
            boolean exists = rs.getBoolean(1);
            if (exists) {
                log.info("Checkpoint schema already present, skip createTables");
            }
            return exists;
        } catch (Exception ex) {
            log.warn("Probe GraphCheckpoint failed, fall back to configured createTables={}", cp.isCreateTables(), ex);
            return false;
        }
    }
}
