-- MCP Server 注册（Agent Client 连接权威；管理台后续可写）
CREATE TABLE IF NOT EXISTS mcp_server_ref
(
    id          BIGINT       PRIMARY KEY,
    server_id   VARCHAR(64)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    base_url    VARCHAR(512) NOT NULL,
    auth_type   VARCHAR(32)  NOT NULL DEFAULT 'NONE',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time TIMESTAMPTZ  NOT NULL,
    update_time TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_mcp_server_ref_server_id UNIQUE (server_id)
);

COMMENT ON TABLE mcp_server_ref IS 'MCP Server 注册：ACTIVE 记录供 Agent Client 加载';
COMMENT ON COLUMN mcp_server_ref.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN mcp_server_ref.server_id IS '业务键，稳定标识';
COMMENT ON COLUMN mcp_server_ref.name IS '展示名';
COMMENT ON COLUMN mcp_server_ref.base_url IS 'SSE/MCP 基址，如 http://127.0.0.1:8081';
COMMENT ON COLUMN mcp_server_ref.auth_type IS 'NONE | BEARER；密钥不入库明文，由环境变量注入';
COMMENT ON COLUMN mcp_server_ref.status IS 'ACTIVE | INACTIVE';
COMMENT ON COLUMN mcp_server_ref.deleted IS '软删除';
COMMENT ON COLUMN mcp_server_ref.create_time IS '创建时间';
COMMENT ON COLUMN mcp_server_ref.update_time IS '更新时间';
