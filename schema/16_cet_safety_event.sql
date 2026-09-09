-- CET Safety 命中事件
CREATE TABLE IF NOT EXISTS cet_safety_event
(
    id                BIGINT       PRIMARY KEY,
    event_id          VARCHAR(64)  NOT NULL,
    lesson_session_id VARCHAR(64),
    turn_id           VARCHAR(64),
    learner_id        VARCHAR(64),
    user_id           VARCHAR(64),
    direction         VARCHAR(16)  NOT NULL,
    event_type        VARCHAR(64)  NOT NULL,
    action            VARCHAR(32)  NOT NULL,
    detail_json       JSONB,
    create_time       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_cet_safety_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_cet_safety_lesson
    ON cet_safety_event (lesson_session_id, create_time);
CREATE INDEX IF NOT EXISTS idx_cet_safety_learner
    ON cet_safety_event (learner_id, create_time);

COMMENT ON TABLE cet_safety_event IS '儿童 Safety 闸门命中事件';
COMMENT ON COLUMN cet_safety_event.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_safety_event.event_id IS '事件业务键';
COMMENT ON COLUMN cet_safety_event.lesson_session_id IS '关联课时会话键，可空';
COMMENT ON COLUMN cet_safety_event.turn_id IS '关联轮次键，可空';
COMMENT ON COLUMN cet_safety_event.learner_id IS '学习者键，可空';
COMMENT ON COLUMN cet_safety_event.user_id IS '触发用户键，可空';
COMMENT ON COLUMN cet_safety_event.direction IS 'INPUT|OUTPUT';
COMMENT ON COLUMN cet_safety_event.event_type IS '命中类型（敏感词/越界话题等）';
COMMENT ON COLUMN cet_safety_event.action IS 'BLOCK|REWRITE|ESCALATE|LOG';
COMMENT ON COLUMN cet_safety_event.detail_json IS '命中详情（避免完整敏感原文入应用日志）';
COMMENT ON COLUMN cet_safety_event.create_time IS '创建时间';
