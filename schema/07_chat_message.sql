-- 通用聊天消息
CREATE TABLE IF NOT EXISTS chat_message
(
    id              BIGINT       PRIMARY KEY,
    message_id      VARCHAR(64)  NOT NULL,
    session_id      VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    token_count     INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
    create_time     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_chat_message_id UNIQUE (message_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_session_time
    ON chat_message (session_id, create_time);

COMMENT ON TABLE chat_message IS '通用会话消息（短期记忆）';
COMMENT ON COLUMN chat_message.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN chat_message.message_id IS '消息业务键';
COMMENT ON COLUMN chat_message.session_id IS '所属 chat_session.session_id';
COMMENT ON COLUMN chat_message.user_id IS '发送方用户 app_user.user_id';
COMMENT ON COLUMN chat_message.role IS 'user|assistant|system|tool';
COMMENT ON COLUMN chat_message.content IS '消息正文';
COMMENT ON COLUMN chat_message.token_count IS '本条估算 token 数';
COMMENT ON COLUMN chat_message.status IS 'STREAMING|COMPLETED|CANCELLED';
COMMENT ON COLUMN chat_message.create_time IS '创建时间';
