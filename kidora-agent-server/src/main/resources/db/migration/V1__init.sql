-- Flyway V1：与 schema/ 分文件内容一致（UTF-8）
-- 对齐 docs/database-design.md
-- 推荐：bootstrap 仅建库/表空间后，由本脚本建表

-- >>> 00_extensions.sql
-- SHA-256 等（后续内容哈希/种子用）；本切片不强制 pgvector
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- >>> 01_app_user.sql
-- 前台登录用户（家长/老师等；与 admin_user 隔离）
CREATE TABLE IF NOT EXISTS app_user
(
    id            BIGINT       PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    nickname      VARCHAR(100) NOT NULL,
    role          VARCHAR(64)  NOT NULL DEFAULT 'parent',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time   TIMESTAMPTZ  NOT NULL,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_app_user_id UNIQUE (user_id),
    CONSTRAINT uk_app_username UNIQUE (username)
);

CREATE INDEX IF NOT EXISTS idx_app_user_status ON app_user (status) WHERE deleted = FALSE;

COMMENT ON TABLE app_user IS '前台登录用户（家长/老师），与 admin_user 隔离';
COMMENT ON COLUMN app_user.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN app_user.user_id IS '业务用户键，写入 User JWT';
COMMENT ON COLUMN app_user.username IS '登录用户名';
COMMENT ON COLUMN app_user.password_hash IS '密码哈希（BCrypt 等）';
COMMENT ON COLUMN app_user.nickname IS '展示昵称';
COMMENT ON COLUMN app_user.role IS '角色：parent|teacher 等';
COMMENT ON COLUMN app_user.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN app_user.deleted IS '软删除标记';
COMMENT ON COLUMN app_user.create_time IS '创建时间';
COMMENT ON COLUMN app_user.update_time IS '更新时间';

-- >>> 02_learner_profile.sql
-- 儿童学习者档案（MVP-1 基础字段；完整画像 MVP-3）
CREATE TABLE IF NOT EXISTS learner_profile
(
    id              BIGINT       PRIMARY KEY,
    learner_id      VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    age_band        VARCHAR(32),
    cefr_level      VARCHAR(16),
    preferred_persona VARCHAR(64),
    extra_json      JSONB,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMPTZ  NOT NULL,
    update_time     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_learner_profile_id UNIQUE (learner_id)
);

CREATE INDEX IF NOT EXISTS idx_learner_profile_user
    ON learner_profile (user_id) WHERE deleted = FALSE;

COMMENT ON TABLE learner_profile IS '儿童学习者档案（归属家长/老师 user_id）';
COMMENT ON COLUMN learner_profile.learner_id IS '学习者业务键';
COMMENT ON COLUMN learner_profile.user_id IS '归属家长/老师 app_user.user_id';
COMMENT ON COLUMN learner_profile.display_name IS '儿童展示名';
COMMENT ON COLUMN learner_profile.age_band IS '年龄段，如 6-8';
COMMENT ON COLUMN learner_profile.cefr_level IS 'CEFR 如 A1';
COMMENT ON COLUMN learner_profile.preferred_persona IS '偏好 AI 外教人设 id';
COMMENT ON COLUMN learner_profile.extra_json IS '扩展画像 JSON（MVP-3 充实）';
COMMENT ON COLUMN learner_profile.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN learner_profile.deleted IS '软删除标记';

-- >>> 03_llm_config.sql
-- LLM 连接配置（CHAT / EMBEDDING 同表分行）
CREATE TABLE IF NOT EXISTS llm_config
(
    id              BIGINT        PRIMARY KEY,
    config_id       VARCHAR(64)   NOT NULL,
    name            VARCHAR(128)  NOT NULL,
    provider        VARCHAR(64)   NOT NULL DEFAULT 'openai_compatible',
    model_kind      VARCHAR(20)   NOT NULL DEFAULT 'CHAT',
    base_url        VARCHAR(512)  NOT NULL,
    api_key_cipher  TEXT          NOT NULL,
    model           VARCHAR(128)  NOT NULL,
    temperature     NUMERIC(4, 2),
    max_tokens      INT,
    extra_json      JSONB,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    create_time     TIMESTAMPTZ   NOT NULL,
    update_time     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_llm_config_id UNIQUE (config_id)
);

CREATE INDEX IF NOT EXISTS idx_llm_config_status ON llm_config (status);
CREATE INDEX IF NOT EXISTS idx_llm_config_kind_status ON llm_config (model_kind, status);

COMMENT ON TABLE llm_config IS '大模型 OpenAI Compatible 连接配置（CHAT/EMBEDDING）';
COMMENT ON COLUMN llm_config.config_id IS '配置业务键，如 llm_primary / llm_embedding';
COMMENT ON COLUMN llm_config.model_kind IS 'CHAT | EMBEDDING';
COMMENT ON COLUMN llm_config.api_key_cipher IS 'API Key 密文（禁止明文日志）';
COMMENT ON COLUMN llm_config.status IS 'ACTIVE/DISABLED';

-- >>> 04_prompt_template.sql
-- 提示词模板（线上副本：每 code 一行）
CREATE TABLE IF NOT EXISTS prompt_template
(
    id                 BIGINT       PRIMARY KEY,
    code               VARCHAR(128) NOT NULL,
    name               VARCHAR(128) NOT NULL,
    role               VARCHAR(20)  NOT NULL,
    prompt_group       VARCHAR(32)  NOT NULL DEFAULT 'CHAT',
    content            TEXT         NOT NULL,
    published_version  INT          NOT NULL DEFAULT 1,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_time        TIMESTAMPTZ  NOT NULL,
    update_time        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_prompt_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_prompt_group_code ON prompt_template (prompt_group, code);

COMMENT ON TABLE prompt_template IS '提示词线上副本（每 code 一行）';
COMMENT ON COLUMN prompt_template.code IS '模板编码，如 cet.tutor.system';
COMMENT ON COLUMN prompt_template.role IS 'SYSTEM|USER';
COMMENT ON COLUMN prompt_template.prompt_group IS '分组：CHAT|CET_PLANNER|CET_TUTOR|CET_EVAL|CET_SAFETY 等';
COMMENT ON COLUMN prompt_template.content IS '当前已发布正文，可含变量占位';
COMMENT ON COLUMN prompt_template.published_version IS '当前已发布版本号';
COMMENT ON COLUMN prompt_template.status IS 'ACTIVE/DISABLED';

-- >>> 05_prompt_template_version.sql
-- 提示词版本历史（草稿 / 已发布 / 已顶替）
CREATE TABLE IF NOT EXISTS prompt_template_version
(
    id           BIGINT       PRIMARY KEY,
    code         VARCHAR(128) NOT NULL,
    version      INT          NOT NULL,
    name         VARCHAR(128) NOT NULL,
    role         VARCHAR(20)  NOT NULL,
    prompt_group VARCHAR(32)  NOT NULL DEFAULT 'CHAT',
    content      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    change_note  VARCHAR(512),
    created_by   VARCHAR(64)  NOT NULL,
    create_time  TIMESTAMPTZ  NOT NULL,
    publish_time TIMESTAMPTZ,
    CONSTRAINT uk_prompt_ver_code_ver UNIQUE (code, version)
);

CREATE INDEX IF NOT EXISTS idx_prompt_ver_code_status ON prompt_template_version (code, status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_prompt_ver_one_draft
    ON prompt_template_version (code) WHERE status = 'DRAFT';

COMMENT ON TABLE prompt_template_version IS '提示词版本历史（DRAFT/PUBLISHED/SUPERSEDED）';
COMMENT ON COLUMN prompt_template_version.status IS 'DRAFT|PUBLISHED|SUPERSEDED';
COMMENT ON COLUMN prompt_template_version.created_by IS '操作者 admin_id；种子为 system';

-- >>> 06_chat_session.sql
-- 通用聊天会话元数据
CREATE TABLE IF NOT EXISTS chat_session
(
    id                       BIGINT       PRIMARY KEY,
    session_id               VARCHAR(64)  NOT NULL,
    user_id                  VARCHAR(64)  NOT NULL,
    title                    VARCHAR(200),
    summary                  TEXT,
    summary_until_time       TIMESTAMPTZ,
    summary_until_message_id VARCHAR(64),
    message_count            INT          NOT NULL DEFAULT 0,
    last_active_time         TIMESTAMPTZ  NOT NULL,
    deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_chat_session_id UNIQUE (session_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_session_user_active
    ON chat_session (user_id, last_active_time) WHERE deleted = FALSE;

COMMENT ON TABLE chat_session IS '通用聊天会话元数据';
COMMENT ON COLUMN chat_session.session_id IS '对外业务键';
COMMENT ON COLUMN chat_session.user_id IS '所属用户 app_user.user_id';
COMMENT ON COLUMN chat_session.summary IS '结构化摘要 JSON（滚动合并）';
COMMENT ON COLUMN chat_session.message_count IS '消息条数';
COMMENT ON COLUMN chat_session.last_active_time IS '最后活跃时间';
COMMENT ON COLUMN chat_session.deleted IS '软删除标记';

-- >>> 07_chat_message.sql
-- 通用聊天消息
CREATE TABLE IF NOT EXISTS chat_message
(
    id              BIGINT       PRIMARY KEY,
    message_id      VARCHAR(64)  NOT NULL,
    session_id      VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    token_count     INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
    create_time     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_chat_message_id UNIQUE (message_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_session_time
    ON chat_message (session_id, create_time);

COMMENT ON TABLE chat_message IS '通用会话消息（短期记忆）';
COMMENT ON COLUMN chat_message.message_id IS '消息业务键';
COMMENT ON COLUMN chat_message.session_id IS '所属 chat_session.session_id';
COMMENT ON COLUMN chat_message.role IS 'user|assistant|system|tool';
COMMENT ON COLUMN chat_message.status IS 'STREAMING|COMPLETED|CANCELLED';

-- >>> 08_llm_call_log.sql
-- 入模审计（完整 prompt 仅进本表，禁止应用日志打印）
CREATE TABLE IF NOT EXISTS llm_call_log
(
    id                BIGINT       PRIMARY KEY,
    call_id           VARCHAR(64)  NOT NULL,
    trace_id          VARCHAR(64),
    session_id        VARCHAR(64),
    message_id        VARCHAR(64),
    user_id           VARCHAR(64),
    learner_id        VARCHAR(64),
    biz_source        VARCHAR(32)  NOT NULL DEFAULT 'CHAT',
    biz_ref_id        VARCHAR(64),
    model_id          VARCHAR(128) NOT NULL,
    provider          VARCHAR(64),
    attempt           INT          NOT NULL DEFAULT 1,
    is_fallback       BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)  NOT NULL,
    error_code        VARCHAR(64),
    latency_ms        INT,
    prompt_tokens     INT,
    completion_tokens INT,
    request_json      JSONB        NOT NULL,
    response_json     JSONB,
    create_time       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_llm_call_id UNIQUE (call_id)
);

CREATE INDEX IF NOT EXISTS idx_llm_call_session ON llm_call_log (session_id, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_user ON llm_call_log (user_id, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_biz_source ON llm_call_log (biz_source, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_biz_ref ON llm_call_log (biz_ref_id, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_trace ON llm_call_log (trace_id);

COMMENT ON TABLE llm_call_log IS '每次 LLM 调用的完整入模参数审计';
COMMENT ON COLUMN llm_call_log.biz_source IS '业务来源：CHAT|CET';
COMMENT ON COLUMN llm_call_log.biz_ref_id IS '业务引用键：CET=lesson_session_id 等';
COMMENT ON COLUMN llm_call_log.request_json IS '完整请求参数 JSON';
COMMENT ON COLUMN llm_call_log.status IS 'SUCCESS/FAILED 等';

-- >>> 09_admin_user.sql
-- 后台管理员（与 app_user 隔离；管理台写）
CREATE TABLE IF NOT EXISTS admin_user
(
    id            BIGINT       PRIMARY KEY,
    admin_id      VARCHAR(64)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_builtin    BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time   TIMESTAMPTZ  NOT NULL,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_admin_user_id UNIQUE (admin_id),
    CONSTRAINT uk_admin_username UNIQUE (username)
);

COMMENT ON TABLE admin_user IS '后台运营账号，与 app_user 隔离';
COMMENT ON COLUMN admin_user.admin_id IS '业务键，写入 Admin JWT';
COMMENT ON COLUMN admin_user.role IS 'SUPER_ADMIN | OPERATOR';
COMMENT ON COLUMN admin_user.status IS 'ACTIVE | DISABLED';
COMMENT ON COLUMN admin_user.is_builtin IS '内置管理员：不可删/改角色/禁用，仅可改密';

-- >>> 10_admin_audit_log.sql
-- 管理台写操作审计
-- detail JSON 契约: {"changes":[{"field","from","to"}],"meta"?:{}}
CREATE TABLE IF NOT EXISTS admin_audit_log
(
    id            BIGINT       PRIMARY KEY,
    admin_id      VARCHAR(64)  NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   VARCHAR(128),
    detail        JSONB,
    create_time   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_admin_time ON admin_audit_log (admin_id, create_time);
CREATE INDEX IF NOT EXISTS idx_admin_audit_resource ON admin_audit_log (resource_type, resource_id);

COMMENT ON TABLE admin_audit_log IS '管理台写操作审计；detail 记录字段级 from→to 变更';
COMMENT ON COLUMN admin_audit_log.detail IS
    'JSONB 契约: {"changes":[{"field":"model","from":"旧值","to":"新值"}],"meta":{可选}}. '
    'apiKey/password 仅记 to=[CHANGED]；禁止明文密钥/密码。';

-- >>> 11_cet_lesson_session.sql
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
COMMENT ON COLUMN cet_lesson_session.lesson_session_id IS '对外业务键';
COMMENT ON COLUMN cet_lesson_session.status IS
    'CREATED|PLANNING|PRACTICING|EVALUATING|REPLANNING|COMPLETED|ABORTED|SAFETY_BLOCKED';
COMMENT ON COLUMN cet_lesson_session.active_plan_id IS '当前生效 cet_training_plan.plan_id';
COMMENT ON COLUMN cet_lesson_session.persona_id IS 'AI 外教人设 id';

-- >>> 12_cet_training_plan.sql
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
COMMENT ON COLUMN cet_training_plan.plan_json IS '计划 JSON：topic/cefr/persona/objectives/stages';
COMMENT ON COLUMN cet_training_plan.status IS 'ACTIVE|SUPERSEDED';
COMMENT ON COLUMN cet_training_plan.version IS '同会话内版本号；Re-plan 递增（修订历史表 MVP-3）';

-- >>> 13_cet_tutor_turn.sql
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
COMMENT ON COLUMN cet_tutor_turn.turn_index IS '会话内从 1 递增的轮次序号';
COMMENT ON COLUMN cet_tutor_turn.stage_id IS '计划 stages[].id';
COMMENT ON COLUMN cet_tutor_turn.child_asr_json IS 'ASR 结果（MVP-2）；文本陪练可空';
COMMENT ON COLUMN cet_tutor_turn.signals_json IS '中间信号（卡顿、求助等）';

-- >>> 14_cet_turn_assessment.sql
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
COMMENT ON COLUMN cet_turn_assessment.scope IS 'SESSION|TURN|STAGE';
COMMENT ON COLUMN cet_turn_assessment.turn_id IS '轮次评测时关联 cet_tutor_turn.turn_id；会话摘要可空';
COMMENT ON COLUMN cet_turn_assessment.assessment_json IS 'grammar/vocabulary/pronunciation/fluency 等评分 JSON';

-- >>> 15_cet_session_report.sql
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
COMMENT ON COLUMN cet_session_report.child_summary IS '儿童可读短评';
COMMENT ON COLUMN cet_session_report.parent_summary IS '家长可读摘要（MVP-4 充实）';
COMMENT ON COLUMN cet_session_report.report_json IS '完整报告结构化 JSON';

-- >>> 16_cet_safety_event.sql
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
COMMENT ON COLUMN cet_safety_event.direction IS 'INPUT|OUTPUT';
COMMENT ON COLUMN cet_safety_event.event_type IS '命中类型（敏感词/越界话题等）';
COMMENT ON COLUMN cet_safety_event.action IS 'BLOCK|REWRITE|ESCALATE|LOG';
COMMENT ON COLUMN cet_safety_event.detail_json IS '命中详情（避免完整敏感原文入应用日志）';

