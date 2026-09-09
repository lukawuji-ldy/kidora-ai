-- 管理台写操作审计
-- detail JSON 契约: {"changes":[{"field","from","to"}],"meta"?:{}}
CREATE TABLE IF NOT EXISTS admin_audit_log
(
    id            BIGINT       PRIMARY KEY,
    admin_id      VARCHAR(64)  NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   VARCHAR(128),
    detail        JSONB,
    create_time   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_admin_time ON admin_audit_log (admin_id, create_time);
CREATE INDEX IF NOT EXISTS idx_admin_audit_resource ON admin_audit_log (resource_type, resource_id);

COMMENT ON TABLE admin_audit_log IS '管理台写操作审计；detail 记录字段级 from→to 变更';
COMMENT ON COLUMN admin_audit_log.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN admin_audit_log.admin_id IS '操作者 admin_user.admin_id';
COMMENT ON COLUMN admin_audit_log.action IS '动作，如 CREATE/UPDATE/DISABLE';
COMMENT ON COLUMN admin_audit_log.resource_type IS '资源类型，如 llm_config/prompt_template';
COMMENT ON COLUMN admin_audit_log.resource_id IS '资源业务键，可空';
COMMENT ON COLUMN admin_audit_log.detail IS 'JSONB 契约: {"changes":[{"field","from","to"}],"meta":{可选}}. apiKey/password 仅记 to=[CHANGED]；禁止明文密钥/密码。';
COMMENT ON COLUMN admin_audit_log.create_time IS '创建时间';
