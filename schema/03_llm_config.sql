-- LLM 连接配置（CHAT / EMBEDDING 同表分行）
CREATE TABLE IF NOT EXISTS llm_config
(
    id              BIGINT        PRIMARY KEY,
    config_id       VARCHAR(64)   NOT NULL,
    name            VARCHAR(128)  NOT NULL,
    provider        VARCHAR(64)   NOT NULL DEFAULT 'openai_compatible',
    model_kind      VARCHAR(20)   NOT NULL DEFAULT 'CHAT',
    base_url        VARCHAR(512)  NOT NULL,
    api_key_cipher  TEXT          NOT NULL,
    model           VARCHAR(128)  NOT NULL,
    temperature     NUMERIC(4, 2),
    max_tokens      INT,
    extra_json      JSONB,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    create_time     TIMESTAMPTZ   NOT NULL,
    update_time     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_llm_config_id UNIQUE (config_id)
);

CREATE INDEX IF NOT EXISTS idx_llm_config_status ON llm_config (status);
CREATE INDEX IF NOT EXISTS idx_llm_config_kind_status ON llm_config (model_kind, status);

COMMENT ON TABLE llm_config IS '大模型 OpenAI Compatible 连接配置（CHAT/EMBEDDING）';
COMMENT ON COLUMN llm_config.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN llm_config.config_id IS '配置业务键，如 llm_primary / llm_embedding';
COMMENT ON COLUMN llm_config.name IS '配置展示名称';
COMMENT ON COLUMN llm_config.provider IS '供应商标识，如 openai_compatible';
COMMENT ON COLUMN llm_config.model_kind IS 'CHAT | EMBEDDING';
COMMENT ON COLUMN llm_config.base_url IS 'OpenAI Compatible API Base URL';
COMMENT ON COLUMN llm_config.api_key_cipher IS 'API Key 密文（禁止明文日志）';
COMMENT ON COLUMN llm_config.model IS '模型名（入库配置，禁止业务代码硬编码）';
COMMENT ON COLUMN llm_config.temperature IS '采样温度';
COMMENT ON COLUMN llm_config.max_tokens IS '单次最大生成 token';
COMMENT ON COLUMN llm_config.extra_json IS '扩展参数 JSON（如 chat_completions_path）';
COMMENT ON COLUMN llm_config.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN llm_config.create_time IS '创建时间';
COMMENT ON COLUMN llm_config.update_time IS '更新时间';
