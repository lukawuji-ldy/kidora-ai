-- CET 训练计划修订历史（MVP-3）
CREATE TABLE IF NOT EXISTS cet_training_plan_revision
(
    id                BIGINT       PRIMARY KEY,
    revision_id       VARCHAR(64)  NOT NULL,
    lesson_session_id VARCHAR(64)  NOT NULL,
    plan_id           VARCHAR(64)  NOT NULL,
    version           INT          NOT NULL,
    plan_json         JSONB        NOT NULL,
    eval_json         JSONB,
    reason            VARCHAR(512),
    create_time       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_cet_training_plan_revision_id UNIQUE (revision_id)
);

CREATE INDEX IF NOT EXISTS idx_cet_plan_rev_lesson
    ON cet_training_plan_revision (lesson_session_id, version);

COMMENT ON TABLE cet_training_plan_revision IS 'CET 训练计划修订历史（Re-plan 产出）';
COMMENT ON COLUMN cet_training_plan_revision.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_training_plan_revision.revision_id IS '修订业务键';
COMMENT ON COLUMN cet_training_plan_revision.lesson_session_id IS '所属 cet_lesson_session.lesson_session_id';
COMMENT ON COLUMN cet_training_plan_revision.plan_id IS '新计划 cet_training_plan.plan_id';
COMMENT ON COLUMN cet_training_plan_revision.version IS '同会话内版本号';
COMMENT ON COLUMN cet_training_plan_revision.plan_json IS '修订后计划 JSON';
COMMENT ON COLUMN cet_training_plan_revision.eval_json IS '触发本次修订的评测 JSON';
COMMENT ON COLUMN cet_training_plan_revision.reason IS '修订原因摘要';
COMMENT ON COLUMN cet_training_plan_revision.create_time IS '创建时间';
