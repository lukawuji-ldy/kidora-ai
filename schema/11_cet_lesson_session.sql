-- CET 一次陪练会话
CREATE TABLE IF NOT EXISTS cet_lesson_session
(
    id               BIGINT       PRIMARY KEY,
    lesson_session_id VARCHAR(64) NOT NULL,
    user_id          VARCHAR(64)  NOT NULL,
    learner_id       VARCHAR(64)  NOT NULL,
    topic            VARCHAR(200),
    persona_id       VARCHAR(64),
    cefr_level       VARCHAR(16),
    status           VARCHAR(32)  NOT NULL DEFAULT 'CREATED',
    active_plan_id   VARCHAR(64),
    extra_json       JSONB,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    start_time       TIMESTAMPTZ,
    end_time         TIMESTAMPTZ,
    create_time      TIMESTAMPTZ  NOT NULL,
    update_time      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_cet_lesson_session_id UNIQUE (lesson_session_id)
);

CREATE INDEX IF NOT EXISTS idx_cet_lesson_learner
    ON cet_lesson_session (learner_id, create_time) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_cet_lesson_user_status
    ON cet_lesson_session (user_id, status) WHERE deleted = FALSE;

COMMENT ON TABLE cet_lesson_session IS 'CET 一次陪练会话';
COMMENT ON COLUMN cet_lesson_session.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_lesson_session.lesson_session_id IS '对外业务键';
COMMENT ON COLUMN cet_lesson_session.user_id IS '开课家长/老师 app_user.user_id';
COMMENT ON COLUMN cet_lesson_session.learner_id IS '学习者 learner_profile.learner_id';
COMMENT ON COLUMN cet_lesson_session.topic IS '陪练主题';
COMMENT ON COLUMN cet_lesson_session.persona_id IS 'AI 外教人设 id';
COMMENT ON COLUMN cet_lesson_session.cefr_level IS '本课 CEFR 级别';
COMMENT ON COLUMN cet_lesson_session.status IS 'CREATED|PLANNING|PRACTICING|EVALUATING|REPLANNING|COMPLETED|ABORTED|SAFETY_BLOCKED';
COMMENT ON COLUMN cet_lesson_session.active_plan_id IS '当前生效 cet_training_plan.plan_id';
COMMENT ON COLUMN cet_lesson_session.extra_json IS '扩展元数据 JSON';
COMMENT ON COLUMN cet_lesson_session.deleted IS '软删除标记';
COMMENT ON COLUMN cet_lesson_session.start_time IS '开课时间';
COMMENT ON COLUMN cet_lesson_session.end_time IS '结课/终止时间';
COMMENT ON COLUMN cet_lesson_session.create_time IS '创建时间';
COMMENT ON COLUMN cet_lesson_session.update_time IS '更新时间';
