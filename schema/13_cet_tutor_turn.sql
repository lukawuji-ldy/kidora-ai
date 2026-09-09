-- CET 陪练轮次（Tutor 小循环）
CREATE TABLE IF NOT EXISTS cet_tutor_turn
(
    id                BIGINT       PRIMARY KEY,
    turn_id           VARCHAR(64)  NOT NULL,
    lesson_session_id VARCHAR(64)  NOT NULL,
    learner_id        VARCHAR(64)  NOT NULL,
    turn_index        INT          NOT NULL,
    stage_id          VARCHAR(64),
    tutor_text        TEXT,
    child_text        TEXT,
    child_asr_json    JSONB,
    signals_json      JSONB,
    create_time       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_cet_tutor_turn_id UNIQUE (turn_id),
    CONSTRAINT uk_cet_tutor_turn_lesson_idx UNIQUE (lesson_session_id, turn_index)
);

CREATE INDEX IF NOT EXISTS idx_cet_tutor_turn_lesson
    ON cet_tutor_turn (lesson_session_id, turn_index);

COMMENT ON TABLE cet_tutor_turn IS 'CET 每一轮外教/儿童输入输出';
COMMENT ON COLUMN cet_tutor_turn.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_tutor_turn.turn_id IS '轮次业务键';
COMMENT ON COLUMN cet_tutor_turn.lesson_session_id IS '所属课时会话键';
COMMENT ON COLUMN cet_tutor_turn.learner_id IS '学习者键';
COMMENT ON COLUMN cet_tutor_turn.turn_index IS '会话内从 1 递增的轮次序号';
COMMENT ON COLUMN cet_tutor_turn.stage_id IS '计划 stages[].id';
COMMENT ON COLUMN cet_tutor_turn.tutor_text IS '外教回复文本';
COMMENT ON COLUMN cet_tutor_turn.child_text IS '儿童输入文本（MVP-1）';
COMMENT ON COLUMN cet_tutor_turn.child_asr_json IS 'ASR 结果（MVP-2）；文本陪练可空';
COMMENT ON COLUMN cet_tutor_turn.signals_json IS '中间信号（卡顿、求助等）';
COMMENT ON COLUMN cet_tutor_turn.create_time IS '创建时间';
