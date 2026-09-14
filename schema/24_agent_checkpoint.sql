-- Agent Graph Checkpoint（对齐 Spring AI Alibaba PostgresSaver 官方 DDL）
-- 禁止与 chat_message 混用；thread_name 存业务 threadId（如 userId:sessionId）

CREATE TABLE IF NOT EXISTS GraphThread (
    thread_id    UUID PRIMARY KEY,
    thread_name  VARCHAR(255),
    is_released  BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE IF NOT EXISTS GraphCheckpoint (
    checkpoint_id        UUID PRIMARY KEY,
    parent_checkpoint_id UUID,
    thread_id            UUID NOT NULL,
    node_id              VARCHAR(255),
    next_node_id         VARCHAR(255),
    state_data           JSONB NOT NULL,
    state_content_type   VARCHAR(100) NOT NULL,
    saved_at             TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_thread
        FOREIGN KEY (thread_id) REFERENCES GraphThread (thread_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id
    ON GraphCheckpoint (thread_id);

CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id_saved_at_desc
    ON GraphCheckpoint (thread_id, saved_at DESC);

DO $$
BEGIN
    BEGIN
        CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_lg4jthread_thread_name_unreleased
            ON GraphThread (thread_name) WHERE is_released = FALSE;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'skip idx_unique_lg4jthread_thread_name_unreleased: %', SQLERRM;
    END;
END $$;

COMMENT ON TABLE GraphThread IS 'Agent Checkpoint 线程（PostgresSaver）；thread_name 为业务 threadId';
COMMENT ON COLUMN GraphThread.thread_id IS '框架内部线程 UUID';
COMMENT ON COLUMN GraphThread.thread_name IS '业务 threadId，约定 userId:sessionId';
COMMENT ON COLUMN GraphThread.is_released IS '是否已释放（软释放标记）';

COMMENT ON TABLE GraphCheckpoint IS 'Agent Checkpoint 快照（PostgresSaver）；state_data 含 binaryPayload';
COMMENT ON COLUMN GraphCheckpoint.checkpoint_id IS '快照主键 UUID';
COMMENT ON COLUMN GraphCheckpoint.parent_checkpoint_id IS '父快照 ID，用于回溯执行链';
COMMENT ON COLUMN GraphCheckpoint.thread_id IS '所属 GraphThread';
COMMENT ON COLUMN GraphCheckpoint.node_id IS '完成节点 ID';
COMMENT ON COLUMN GraphCheckpoint.next_node_id IS '下一节点 ID';
COMMENT ON COLUMN GraphCheckpoint.state_data IS '序列化图状态 JSONB（含 Base64 binaryPayload）';
COMMENT ON COLUMN GraphCheckpoint.state_content_type IS '状态内容类型';
COMMENT ON COLUMN GraphCheckpoint.saved_at IS '保存时间';
