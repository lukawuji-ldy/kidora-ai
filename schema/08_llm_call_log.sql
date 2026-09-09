-- 入模审计（完整 prompt 仅进本表，禁止应用日志打印）
CREATE TABLE IF NOT EXISTS llm_call_log
(
    id                BIGINT       PRIMARY KEY,
    call_id           VARCHAR(64)  NOT NULL,
    trace_id          VARCHAR(64),
    session_id        VARCHAR(64),
    message_id        VARCHAR(64),
    user_id           VARCHAR(64),
    learner_id        VARCHAR(64),
    biz_source        VARCHAR(32)  NOT NULL DEFAULT 'CHAT',
    biz_ref_id        VARCHAR(64),
    model_id          VARCHAR(128) NOT NULL,
    provider          VARCHAR(64),
    attempt           INT          NOT NULL DEFAULT 1,
    is_fallback       BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)  NOT NULL,
    error_code        VARCHAR(64),
    latency_ms        INT,
    prompt_tokens     INT,
    completion_tokens INT,
    request_json      JSONB        NOT NULL,
    response_json     JSONB,
    create_time       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_llm_call_id UNIQUE (call_id)
);

CREATE INDEX IF NOT EXISTS idx_llm_call_session ON llm_call_log (session_id, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_user ON llm_call_log (user_id, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_biz_source ON llm_call_log (biz_source, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_biz_ref ON llm_call_log (biz_ref_id, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_trace ON llm_call_log (trace_id);

COMMENT ON TABLE llm_call_log IS '每次 LLM 调用的完整入模参数审计';
COMMENT ON COLUMN llm_call_log.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN llm_call_log.call_id IS '调用业务键';
COMMENT ON COLUMN llm_call_log.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN llm_call_log.session_id IS '关联会话键（chat_session 或 cet lesson）';
COMMENT ON COLUMN llm_call_log.message_id IS '关联消息业务键，可空';
COMMENT ON COLUMN llm_call_log.user_id IS '调用用户 app_user.user_id';
COMMENT ON COLUMN llm_call_log.learner_id IS '学习者键（CET 场景）；通用 Chat 可空';
COMMENT ON COLUMN llm_call_log.biz_source IS '业务来源：CHAT|CET';
COMMENT ON COLUMN llm_call_log.biz_ref_id IS '业务引用键：CET=lesson_session_id 等';
COMMENT ON COLUMN llm_call_log.model_id IS '实际调用模型名';
COMMENT ON COLUMN llm_call_log.provider IS '供应商标识';
COMMENT ON COLUMN llm_call_log.attempt IS '同配置重试序号，从 1 计';
COMMENT ON COLUMN llm_call_log.is_fallback IS '是否走备用模型';
COMMENT ON COLUMN llm_call_log.status IS 'SUCCESS/FAILED 等';
COMMENT ON COLUMN llm_call_log.error_code IS '失败错误码，成功可空';
COMMENT ON COLUMN llm_call_log.latency_ms IS '端到端耗时毫秒';
COMMENT ON COLUMN llm_call_log.prompt_tokens IS '入模 prompt token';
COMMENT ON COLUMN llm_call_log.completion_tokens IS '生成 completion token';
COMMENT ON COLUMN llm_call_log.request_json IS '完整请求参数 JSON';
COMMENT ON COLUMN llm_call_log.response_json IS '响应摘要/全文 JSON，失败可含错误信息';
COMMENT ON COLUMN llm_call_log.create_time IS '创建时间';
