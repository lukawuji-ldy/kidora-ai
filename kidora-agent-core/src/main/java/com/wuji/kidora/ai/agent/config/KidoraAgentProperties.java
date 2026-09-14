package com.wuji.kidora.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 运行时配置（ReactAgent / Checkpoint / Chat 短窗）。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.agent")
public class KidoraAgentProperties {

    /**
     * Agent 标识，用于 ReactAgent name 前缀。
     */
    private String id = "kidora";

    /**
     * 单次 Agent 最大模型调用次数。
     */
    private int maxModelCalls = 8;

    /**
     * 单次 Agent 最大工具轮次（本期 Chat 无工具，仍注册 Hook）。
     */
    private int maxToolRounds = 8;

    /**
     * 通用 Chat 入模短窗消息条数。
     */
    private int chatWindowSize = 20;

    private Checkpoint checkpoint = new Checkpoint();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getMaxModelCalls() {
        return maxModelCalls;
    }

    public void setMaxModelCalls(int maxModelCalls) {
        this.maxModelCalls = maxModelCalls;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public int getChatWindowSize() {
        return chatWindowSize;
    }

    public void setChatWindowSize(int chatWindowSize) {
        this.chatWindowSize = chatWindowSize;
    }

    public Checkpoint getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Checkpoint checkpoint) {
        this.checkpoint = checkpoint == null ? new Checkpoint() : checkpoint;
    }

    /**
     * Checkpoint Saver 配置。
     *
     * @author liudy
     */
    public static class Checkpoint {

        /** postgres | memory */
        private String type = "postgres";

        /** 默认 false：表由 schema/Flyway 管理；SAA 1.1.2.2 建索引非幂等 */
        private boolean createTables = false;

        private String host = "127.0.0.1";

        private int port = 5432;

        private String database = "kidora_ai";

        private String username = "postgres";

        private String password = "";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isCreateTables() {
            return createTables;
        }

        public void setCreateTables(boolean createTables) {
            this.createTables = createTables;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
