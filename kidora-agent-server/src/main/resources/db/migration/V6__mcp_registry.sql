-- MVP-2A：MCP 注册表 + 本地 kidora-mcp-server seed（Client 下期再读）

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

CREATE TABLE IF NOT EXISTS mcp_tool_binding
(
    id          BIGINT       PRIMARY KEY,
    server_id   VARCHAR(64)  NOT NULL,
    tool_name   VARCHAR(128) NOT NULL,
    product     VARCHAR(32)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    bound       BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time TIMESTAMPTZ  NOT NULL,
    update_time TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_mcp_tool_binding_product_tool UNIQUE (product, tool_name),
    CONSTRAINT fk_mcp_tool_binding_server FOREIGN KEY (server_id) REFERENCES mcp_server_ref (server_id)
);

COMMENT ON TABLE mcp_tool_binding IS 'MCP 工具与产品线绑定；enabled 且 bound 才挂载';
COMMENT ON COLUMN mcp_tool_binding.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN mcp_tool_binding.server_id IS '关联 mcp_server_ref.server_id';
COMMENT ON COLUMN mcp_tool_binding.tool_name IS 'snake_case 工具名，如 asr_transcribe';
COMMENT ON COLUMN mcp_tool_binding.product IS 'CET | CHAT';
COMMENT ON COLUMN mcp_tool_binding.enabled IS '是否启用';
COMMENT ON COLUMN mcp_tool_binding.bound IS '是否绑定到该产品线';
COMMENT ON COLUMN mcp_tool_binding.create_time IS '创建时间';
COMMENT ON COLUMN mcp_tool_binding.update_time IS '更新时间';

INSERT INTO mcp_server_ref (id, server_id, name, base_url, auth_type, status, deleted, create_time, update_time)
VALUES (20001, 'mcp_local_kidora', 'Kidora Local MCP', 'http://127.0.0.1:8081', 'NONE', 'ACTIVE', FALSE,
        TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00')
ON CONFLICT (server_id) DO NOTHING;

INSERT INTO mcp_tool_binding (id, server_id, tool_name, product, enabled, bound, create_time, update_time)
VALUES
(20010, 'mcp_local_kidora', 'echo_ping', 'CET', TRUE, TRUE,
 TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(20011, 'mcp_local_kidora', 'asr_transcribe', 'CET', TRUE, TRUE,
 TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(20012, 'mcp_local_kidora', 'tts_synthesize', 'CET', TRUE, TRUE,
 TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(20013, 'mcp_local_kidora', 'pronunciation_score', 'CET', TRUE, TRUE,
 TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00')
ON CONFLICT (product, tool_name) DO NOTHING;
