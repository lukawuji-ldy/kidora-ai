-- 前台登录用户（家长/老师等；与 admin_user 隔离）
CREATE TABLE IF NOT EXISTS app_user
(
    id            BIGINT       PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    nickname      VARCHAR(100) NOT NULL,
    role          VARCHAR(64)  NOT NULL DEFAULT 'parent',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time   TIMESTAMPTZ  NOT NULL,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_app_user_id UNIQUE (user_id),
    CONSTRAINT uk_app_username UNIQUE (username)
);

CREATE INDEX IF NOT EXISTS idx_app_user_status ON app_user (status) WHERE deleted = FALSE;

COMMENT ON TABLE app_user IS '前台登录用户（家长/老师），与 admin_user 隔离';
COMMENT ON COLUMN app_user.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN app_user.user_id IS '业务用户键，写入 User JWT';
COMMENT ON COLUMN app_user.username IS '登录用户名';
COMMENT ON COLUMN app_user.password_hash IS '密码哈希（BCrypt 等）';
COMMENT ON COLUMN app_user.nickname IS '展示昵称';
COMMENT ON COLUMN app_user.role IS '角色：parent|teacher 等';
COMMENT ON COLUMN app_user.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN app_user.deleted IS '软删除标记';
COMMENT ON COLUMN app_user.create_time IS '创建时间';
COMMENT ON COLUMN app_user.update_time IS '更新时间';
