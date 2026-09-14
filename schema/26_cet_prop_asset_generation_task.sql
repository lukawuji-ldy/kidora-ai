-- CET 道具缺失生成任务与版本记录。
CREATE TABLE IF NOT EXISTS cet_prop_asset_generation_task
(
    id                BIGINT        PRIMARY KEY,
    task_id           VARCHAR(64)   NOT NULL UNIQUE,
    lemma             VARCHAR(64)   NOT NULL,
    aliases_json      JSONB         NOT NULL DEFAULT '[]'::jsonb,
    theme             VARCHAR(32)   NOT NULL DEFAULT 'default',
    source_plan_id    VARCHAR(64),
    source_session_id VARCHAR(64),
    version           INTEGER       NOT NULL DEFAULT 1,
    status            VARCHAR(32)   NOT NULL DEFAULT 'QUEUED',
    storage_path      VARCHAR(512),
    content_type      VARCHAR(128),
    byte_size         BIGINT        NOT NULL DEFAULT 0,
    prompt_version    VARCHAR(64),
    provider_code     VARCHAR(64),
    attempt           INTEGER       NOT NULL DEFAULT 0,
    failure_reason    TEXT,
    review_reason     TEXT,
    reviewed_by      VARCHAR(64),
    reviewed_time    TIMESTAMPTZ,
    create_time       TIMESTAMPTZ   NOT NULL,
    update_time       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_cet_prop_generation_version UNIQUE (lemma, theme, version),
    CONSTRAINT ck_cet_prop_generation_status CHECK (
        status IN ('QUEUED', 'GENERATING', 'PENDING_REVIEW', 'APPROVED',
                   'REJECTED', 'FAILED', 'CANCELLED')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cet_prop_generation_pending
    ON cet_prop_asset_generation_task (lemma, theme)
    WHERE status IN ('QUEUED', 'GENERATING', 'PENDING_REVIEW');

CREATE INDEX IF NOT EXISTS idx_cet_prop_generation_status_time
    ON cet_prop_asset_generation_task (status, update_time);

COMMENT ON TABLE cet_prop_asset_generation_task IS 'CET 道具缺失生成任务与审核版本';
COMMENT ON COLUMN cet_prop_asset_generation_task.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_prop_asset_generation_task.task_id IS '生成任务业务 ID';
COMMENT ON COLUMN cet_prop_asset_generation_task.lemma IS '规范词干（小写英文）';
COMMENT ON COLUMN cet_prop_asset_generation_task.aliases_json IS '道具别名 JSON 数组';
COMMENT ON COLUMN cet_prop_asset_generation_task.theme IS '主题：pets|colors|food|default';
COMMENT ON COLUMN cet_prop_asset_generation_task.source_plan_id IS '触发任务的训练计划 ID';
COMMENT ON COLUMN cet_prop_asset_generation_task.source_session_id IS '触发任务的课时会话 ID';
COMMENT ON COLUMN cet_prop_asset_generation_task.version IS '同 lemma/主题的生成版本';
COMMENT ON COLUMN cet_prop_asset_generation_task.status IS 'QUEUED|GENERATING|PENDING_REVIEW|APPROVED|REJECTED|FAILED|CANCELLED';
COMMENT ON COLUMN cet_prop_asset_generation_task.storage_path IS '相对道具存储根目录的生成文件路径';
COMMENT ON COLUMN cet_prop_asset_generation_task.content_type IS '生成文件 MIME 类型';
COMMENT ON COLUMN cet_prop_asset_generation_task.byte_size IS '生成文件字节数';
COMMENT ON COLUMN cet_prop_asset_generation_task.prompt_version IS '生成提示词版本';
COMMENT ON COLUMN cet_prop_asset_generation_task.provider_code IS '生成服务商标识';
COMMENT ON COLUMN cet_prop_asset_generation_task.attempt IS '生成尝试次数';
COMMENT ON COLUMN cet_prop_asset_generation_task.failure_reason IS '生成失败原因';
COMMENT ON COLUMN cet_prop_asset_generation_task.review_reason IS '审核驳回或备注原因';
COMMENT ON COLUMN cet_prop_asset_generation_task.reviewed_by IS '审核管理员 ID';
COMMENT ON COLUMN cet_prop_asset_generation_task.reviewed_time IS '审核时间';
COMMENT ON COLUMN cet_prop_asset_generation_task.create_time IS '创建时间';
COMMENT ON COLUMN cet_prop_asset_generation_task.update_time IS '更新时间';
