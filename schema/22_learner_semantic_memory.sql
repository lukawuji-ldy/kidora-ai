-- 学习者语义记忆（MVP-3；本切片无强制 pgvector embedding）
CREATE TABLE IF NOT EXISTS learner_semantic_memory
(
    id                 BIGINT       PRIMARY KEY,
    memory_id          VARCHAR(64)  NOT NULL,
    learner_id         VARCHAR(64)  NOT NULL,
    content            TEXT         NOT NULL,
    source_session_id  VARCHAR(64),
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    extra_json         JSONB,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time        TIMESTAMPTZ  NOT NULL,
    update_time        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_learner_semantic_memory_id UNIQUE (memory_id)
);

CREATE INDEX IF NOT EXISTS idx_learner_semantic_learner
    ON learner_semantic_memory (learner_id, create_time DESC) WHERE deleted = FALSE AND status = 'ACTIVE';

COMMENT ON TABLE learner_semantic_memory IS '学习者语义记忆短事实（MVP-3 无强制 embedding）';
COMMENT ON COLUMN learner_semantic_memory.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN learner_semantic_memory.memory_id IS '记忆业务键';
COMMENT ON COLUMN learner_semantic_memory.learner_id IS '学习者 learner_profile.learner_id';
COMMENT ON COLUMN learner_semantic_memory.content IS '可检索短事实文本';
COMMENT ON COLUMN learner_semantic_memory.source_session_id IS '来源课时 sessionId';
COMMENT ON COLUMN learner_semantic_memory.status IS 'ACTIVE|ARCHIVED';
COMMENT ON COLUMN learner_semantic_memory.extra_json IS '扩展元数据（来源维度等）';
COMMENT ON COLUMN learner_semantic_memory.deleted IS '软删除';
COMMENT ON COLUMN learner_semantic_memory.create_time IS '创建时间';
COMMENT ON COLUMN learner_semantic_memory.update_time IS '更新时间';
