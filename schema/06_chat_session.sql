-- 通用聊天会话元数据
CREATE TABLE IF NOT EXISTS chat_session
(
    id                       BIGINT       PRIMARY KEY,
    session_id               VARCHAR(64)  NOT NULL,
    user_id                  VARCHAR(64)  NOT NULL,
    title                    VARCHAR(200),
    summary                  TEXT,
    summary_until_time       TIMESTAMPTZ,
    summary_until_message_id VARCHAR(64),
    message_count            INT          NOT NULL DEFAULT 0,
    last_active_time         TIMESTAMPTZ  NOT NULL,
    deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_chat_session_id UNIQUE (session_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_session_user_active
    ON chat_session (user_id, last_active_time) WHERE deleted = FALSE;

COMMENT ON TABLE chat_session IS '通用聊天会话元数据';
COMMENT ON COLUMN chat_session.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN chat_session.session_id IS '对外业务键';
COMMENT ON COLUMN chat_session.user_id IS '所属用户 app_user.user_id';
COMMENT ON COLUMN chat_session.title IS '会话标题';
COMMENT ON COLUMN chat_session.summary IS '结构化摘要 JSON（滚动合并）';
COMMENT ON COLUMN chat_session.summary_until_time IS '摘要覆盖截止时间';
COMMENT ON COLUMN chat_session.summary_until_message_id IS '摘要覆盖截止消息业务键';
COMMENT ON COLUMN chat_session.message_count IS '消息条数';
COMMENT ON COLUMN chat_session.last_active_time IS '最后活跃时间';
COMMENT ON COLUMN chat_session.deleted IS '软删除标记';
COMMENT ON COLUMN chat_session.create_time IS '创建时间';
