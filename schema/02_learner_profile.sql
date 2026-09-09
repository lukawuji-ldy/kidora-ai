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
COMMENT ON COLUMN learner_profile.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN learner_profile.learner_id IS '学习者业务键';
COMMENT ON COLUMN learner_profile.user_id IS '归属家长/老师 app_user.user_id';
COMMENT ON COLUMN learner_profile.display_name IS '儿童展示名';
COMMENT ON COLUMN learner_profile.age_band IS '年龄段，如 6-8';
COMMENT ON COLUMN learner_profile.cefr_level IS 'CEFR 如 A1';
COMMENT ON COLUMN learner_profile.preferred_persona IS '偏好 AI 外教人设 id';
COMMENT ON COLUMN learner_profile.extra_json IS '扩展画像 JSON（MVP-3 充实）';
COMMENT ON COLUMN learner_profile.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN learner_profile.deleted IS '软删除标记';
COMMENT ON COLUMN learner_profile.create_time IS '创建时间';
COMMENT ON COLUMN learner_profile.update_time IS '更新时间';
