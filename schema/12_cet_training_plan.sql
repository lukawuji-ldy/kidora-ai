-- CET 当前生效训练计划
CREATE TABLE IF NOT EXISTS cet_training_plan
(
    id                BIGINT       PRIMARY KEY,
    plan_id           VARCHAR(64)  NOT NULL,
    lesson_session_id VARCHAR(64)  NOT NULL,
    learner_id        VARCHAR(64)  NOT NULL,
    version           INT          NOT NULL DEFAULT 1,
    plan_json         JSONB        NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_time       TIMESTAMPTZ  NOT NULL,
    update_time       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_cet_training_plan_id UNIQUE (plan_id)
);

CREATE INDEX IF NOT EXISTS idx_cet_plan_lesson
    ON cet_training_plan (lesson_session_id, version);

COMMENT ON TABLE cet_training_plan IS 'CET 当前生效训练计划（大循环 Planner 产出）';
COMMENT ON COLUMN cet_training_plan.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_training_plan.plan_id IS '计划业务键';
COMMENT ON COLUMN cet_training_plan.lesson_session_id IS '所属 cet_lesson_session.lesson_session_id';
COMMENT ON COLUMN cet_training_plan.learner_id IS '学习者键';
COMMENT ON COLUMN cet_training_plan.version IS '同会话内版本号；Re-plan 递增（修订历史表 MVP-3）';
COMMENT ON COLUMN cet_training_plan.plan_json IS '计划 JSON：topic/cefr/persona/objectives/stages';
COMMENT ON COLUMN cet_training_plan.status IS 'ACTIVE|SUPERSEDED';
COMMENT ON COLUMN cet_training_plan.create_time IS '创建时间';
COMMENT ON COLUMN cet_training_plan.update_time IS '更新时间';
