-- 提示词版本历史（草稿 / 已发布 / 已顶替）
CREATE TABLE IF NOT EXISTS prompt_template_version
(
    id           BIGINT       PRIMARY KEY,
    code         VARCHAR(128) NOT NULL,
    version      INT          NOT NULL,
    name         VARCHAR(128) NOT NULL,
    role         VARCHAR(20)  NOT NULL,
    prompt_group VARCHAR(32)  NOT NULL DEFAULT 'CHAT',
    content      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    change_note  VARCHAR(512),
    created_by   VARCHAR(64)  NOT NULL,
    create_time  TIMESTAMPTZ  NOT NULL,
    publish_time TIMESTAMPTZ,
    CONSTRAINT uk_prompt_ver_code_ver UNIQUE (code, version)
);

CREATE INDEX IF NOT EXISTS idx_prompt_ver_code_status ON prompt_template_version (code, status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_prompt_ver_one_draft
    ON prompt_template_version (code) WHERE status = 'DRAFT';

COMMENT ON TABLE prompt_template_version IS '提示词版本历史（DRAFT/PUBLISHED/SUPERSEDED）';
COMMENT ON COLUMN prompt_template_version.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN prompt_template_version.code IS '模板编码，关联 prompt_template.code';
COMMENT ON COLUMN prompt_template_version.version IS '版本号（同 code 内递增）';
COMMENT ON COLUMN prompt_template_version.name IS '该版本展示名称';
COMMENT ON COLUMN prompt_template_version.role IS 'SYSTEM|USER';
COMMENT ON COLUMN prompt_template_version.prompt_group IS '分组：CHAT|CET_* 等';
COMMENT ON COLUMN prompt_template_version.content IS '该版本正文';
COMMENT ON COLUMN prompt_template_version.status IS 'DRAFT|PUBLISHED|SUPERSEDED';
COMMENT ON COLUMN prompt_template_version.change_note IS '变更说明';
COMMENT ON COLUMN prompt_template_version.created_by IS '操作者 admin_id；种子为 system';
COMMENT ON COLUMN prompt_template_version.create_time IS '创建时间';
COMMENT ON COLUMN prompt_template_version.publish_time IS '发布时间；草稿可空';
