-- MVP-3：计划修订 / 语义记忆 / 词典绑定 / Re-plan 与评测 decision 提示词

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

-- 词典工具绑定 CET
INSERT INTO mcp_tool_binding (id, server_id, tool_name, product, enabled, bound, create_time, update_time)
VALUES
(20014, 'mcp_local_kidora', 'dictionary_lookup', 'CET', TRUE, TRUE,
 TIMESTAMPTZ '2026-09-10 00:00:00+00', TIMESTAMPTZ '2026-09-10 00:00:00+00')
ON CONFLICT (product, tool_name) DO NOTHING;

UPDATE prompt_template SET
    content = '学习者：{{displayName}}，年龄段={{ageBand}}，CEFR={{cefr}}，人设={{personaId}}。主题：{{topic}}。画像扩展：{{profileExtra}}。近期学习记忆：{{semanticHits}}。请生成一份简短训练计划 JSON。',
    update_time = TIMESTAMPTZ '2026-09-10 00:00:00+00'
WHERE code = 'cet.planner.user';

UPDATE prompt_template SET
    content = '你负责评估儿童英语练习会话。只输出 JSON：{grammar:number 0-100, vocabulary:number 0-100, fluency:number 0-100, problems:string[], decision:"continue"|"replan"|"complete", focus:string[], pauseNewVocab:boolean, insertStage:object|null, encouragement:string, childSummary:string}。未达标或需微练习时用 replan；阶段可继续用 continue；课时可结束用 complete。encouragement 与 childSummary 用中文。',
    update_time = TIMESTAMPTZ '2026-09-10 00:00:00+00'
WHERE code = 'cet.eval.system';

UPDATE prompt_template SET
    content = '主题={{topic}}，CEFR={{cefr}}，模式={{evalMode}}。当前计划：{{planJson}}。转写文本：\n{{transcript}}\n请返回含 decision 的评测 JSON。',
    update_time = TIMESTAMPTZ '2026-09-10 00:00:00+00'
WHERE code = 'cet.eval.user';

INSERT INTO prompt_template (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
VALUES
(10020, 'cet.replan.system', 'CET 再规划系统提示', 'SYSTEM', 'CET_REPLAN',
 '你是儿童英语课时再规划助手。根据评测信号修订训练计划。只输出合法 JSON，字段同开课计划：topic、cefr、personaId、objectives、stages（含 id/name/goal/targetTurns）、childSummary。可插入微练习阶段；pauseNewVocab 为 true 时勿加新词。措辞适合儿童。',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-10 00:00:00+00', TIMESTAMPTZ '2026-09-10 00:00:00+00'),
(10021, 'cet.replan.user', 'CET 再规划用户提示', 'USER', 'CET_REPLAN',
 '原计划：{{planJson}}\n评测：{{evalJson}}\nfocus={{focus}}，pauseNewVocab={{pauseNewVocab}}。请输出修订后的计划 JSON。',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-10 00:00:00+00', TIMESTAMPTZ '2026-09-10 00:00:00+00')
ON CONFLICT (code) DO NOTHING;

INSERT INTO prompt_template_version (id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time)
SELECT id + 1000, code, 1, name, role, prompt_group, content, 'PUBLISHED', 'MVP-3 seed', 'system',
       TIMESTAMPTZ '2026-09-10 00:00:00+00', TIMESTAMPTZ '2026-09-10 00:00:00+00'
FROM prompt_template
WHERE code IN ('cet.replan.system', 'cet.replan.user')
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v WHERE v.code = prompt_template.code AND v.version = 1
  );

UPDATE prompt_template_version v
SET name = t.name, content = t.content
FROM prompt_template t
WHERE v.code = t.code
  AND v.version = t.published_version
  AND t.code IN ('cet.eval.system', 'cet.eval.user', 'cet.planner.user');
