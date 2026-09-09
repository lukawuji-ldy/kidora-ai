-- MCP 工具绑定（按产品线子集；工具名全局唯一冲突由应用 fail-fast）
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
