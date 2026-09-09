-- 提示词模板（线上副本：每 code 一行）
CREATE TABLE IF NOT EXISTS prompt_template
(
    id                 BIGINT       PRIMARY KEY,
    code               VARCHAR(128) NOT NULL,
    name               VARCHAR(128) NOT NULL,
    role               VARCHAR(20)  NOT NULL,
    prompt_group       VARCHAR(32)  NOT NULL DEFAULT 'CHAT',
    content            TEXT         NOT NULL,
    published_version  INT          NOT NULL DEFAULT 1,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_time        TIMESTAMPTZ  NOT NULL,
    update_time        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_prompt_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_prompt_group_code ON prompt_template (prompt_group, code);

COMMENT ON TABLE prompt_template IS '提示词线上副本（每 code 一行）';
COMMENT ON COLUMN prompt_template.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN prompt_template.code IS '模板编码，如 cet.tutor.system';
COMMENT ON COLUMN prompt_template.name IS '模板展示名称';
COMMENT ON COLUMN prompt_template.role IS 'SYSTEM|USER';
COMMENT ON COLUMN prompt_template.prompt_group IS '分组：CHAT|CET_PLANNER|CET_TUTOR|CET_EVAL|CET_SAFETY 等';
COMMENT ON COLUMN prompt_template.content IS '当前已发布正文，可含变量占位';
COMMENT ON COLUMN prompt_template.published_version IS '当前已发布版本号';
COMMENT ON COLUMN prompt_template.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN prompt_template.create_time IS '创建时间';
COMMENT ON COLUMN prompt_template.update_time IS '更新时间';
