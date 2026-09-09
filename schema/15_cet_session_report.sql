-- CET 结课报告快照（家长版打磨 MVP-4）
CREATE TABLE IF NOT EXISTS cet_session_report
(
    id                BIGINT       PRIMARY KEY,
    report_id         VARCHAR(64)  NOT NULL,
    lesson_session_id VARCHAR(64)  NOT NULL,
    learner_id        VARCHAR(64)  NOT NULL,
    child_summary     TEXT,
    parent_summary    TEXT,
    report_json       JSONB        NOT NULL,
    create_time       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_cet_session_report_id UNIQUE (report_id),
    CONSTRAINT uk_cet_session_report_lesson UNIQUE (lesson_session_id)
);

CREATE INDEX IF NOT EXISTS idx_cet_report_learner
    ON cet_session_report (learner_id, create_time);

COMMENT ON TABLE cet_session_report IS 'CET 结课报告快照';
COMMENT ON COLUMN cet_session_report.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_session_report.report_id IS '报告业务键';
COMMENT ON COLUMN cet_session_report.lesson_session_id IS '所属课时会话键（一对一）';
COMMENT ON COLUMN cet_session_report.learner_id IS '学习者键';
COMMENT ON COLUMN cet_session_report.child_summary IS '儿童可读短评';
COMMENT ON COLUMN cet_session_report.parent_summary IS '家长可读摘要（MVP-4 充实）';
COMMENT ON COLUMN cet_session_report.report_json IS '完整报告结构化 JSON';
COMMENT ON COLUMN cet_session_report.create_time IS '创建时间';
