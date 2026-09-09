-- CET 评测结果（MVP-1：会话级摘要；细粒度轮次评测 MVP-3）
CREATE TABLE IF NOT EXISTS cet_turn_assessment
(
    id                BIGINT       PRIMARY KEY,
    assessment_id     VARCHAR(64)  NOT NULL,
    lesson_session_id VARCHAR(64)  NOT NULL,
    turn_id           VARCHAR(64),
    scope             VARCHAR(32)  NOT NULL DEFAULT 'SESSION',
    assessment_json   JSONB        NOT NULL,
    create_time       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_cet_turn_assessment_id UNIQUE (assessment_id)
);

CREATE INDEX IF NOT EXISTS idx_cet_assessment_lesson
    ON cet_turn_assessment (lesson_session_id, create_time);

COMMENT ON TABLE cet_turn_assessment IS 'CET 评测结构化结果';
COMMENT ON COLUMN cet_turn_assessment.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_turn_assessment.assessment_id IS '评测业务键';
COMMENT ON COLUMN cet_turn_assessment.lesson_session_id IS '所属课时会话键';
COMMENT ON COLUMN cet_turn_assessment.turn_id IS '轮次评测时关联 cet_tutor_turn.turn_id；会话摘要可空';
COMMENT ON COLUMN cet_turn_assessment.scope IS 'SESSION|TURN|STAGE';
COMMENT ON COLUMN cet_turn_assessment.assessment_json IS 'grammar/vocabulary/pronunciation/fluency 等评分 JSON';
COMMENT ON COLUMN cet_turn_assessment.create_time IS '创建时间';
