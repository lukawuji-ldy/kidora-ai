-- CET 人设 ↔ 腾讯 TTS 音色映射（seed 仅 tencent；结构可扩展其他厂商）

CREATE TABLE IF NOT EXISTS cet_persona_voice
(
    id            BIGINT        PRIMARY KEY,
    persona_id    VARCHAR(64)   NOT NULL,
    vendor_code   VARCHAR(32)   NOT NULL,
    voice_id      VARCHAR(64)   NOT NULL,
    voice_name    VARCHAR(128)  NOT NULL,
    voice_traits  TEXT          NOT NULL,
    catalog_url   VARCHAR(512)  NOT NULL,
    locale        VARCHAR(16)   NOT NULL DEFAULT 'en-US',
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    create_time   TIMESTAMPTZ   NOT NULL,
    update_time   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_cet_persona_voice_persona_vendor UNIQUE (persona_id, vendor_code)
);

CREATE INDEX IF NOT EXISTS idx_cet_persona_voice_vendor_status
    ON cet_persona_voice (vendor_code, status);

COMMENT ON TABLE cet_persona_voice IS 'CET 人设与语音厂商 TTS 音色映射（按 vendor 可扩展）';
COMMENT ON COLUMN cet_persona_voice.id IS '主键（雪花/固定 seed id）';
COMMENT ON COLUMN cet_persona_voice.persona_id IS '人设 id：emma/mike/lily/tom/coco/alex';
COMMENT ON COLUMN cet_persona_voice.vendor_code IS '语音厂商：tencent｜iflytek｜azure 等';
COMMENT ON COLUMN cet_persona_voice.voice_id IS '厂商音色 id（腾讯 VoiceType）';
COMMENT ON COLUMN cet_persona_voice.voice_name IS '音色展示名';
COMMENT ON COLUMN cet_persona_voice.voice_traits IS '音色特点（运营可读中文）';
COMMENT ON COLUMN cet_persona_voice.catalog_url IS '官方音色对照表 URL';
COMMENT ON COLUMN cet_persona_voice.locale IS '建议朗读 locale';
COMMENT ON COLUMN cet_persona_voice.status IS 'ACTIVE｜DISABLED';
COMMENT ON COLUMN cet_persona_voice.create_time IS '创建时间';
COMMENT ON COLUMN cet_persona_voice.update_time IS '更新时间';

INSERT INTO cet_persona_voice
(id, persona_id, vendor_code, voice_id, voice_name, voice_traits, catalog_url, locale, status, create_time, update_time)
VALUES
(40001, 'emma', 'tencent', '501009', 'WeWinny',
 '外语女声；英文清晰温和，贴合温柔鼓励型外教',
 'https://cloud.tencent.com/document/product/1073/92668', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-10 00:00:00+00', TIMESTAMPTZ '2026-09-10 00:00:00+00'),
(40002, 'mike', 'tencent', '501008', 'WeJames',
 '外语男声；英文沉稳有力，贴合探险任务型',
 'https://cloud.tencent.com/document/product/1073/92668', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-10 00:00:00+00', TIMESTAMPTZ '2026-09-10 00:00:00+00'),
(40003, 'lily', 'tencent', '603004', '温柔小柠',
 '中英聊天女声；柔和、偏叙事，贴合故事型',
 'https://cloud.tencent.com/document/product/1073/92668', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-10 00:00:00+00', TIMESTAMPTZ '2026-09-10 00:00:00+00'),
(40004, 'tom', 'tencent', '101050', 'WeJack',
 '精品英文男声；干脆有力，贴合挑战型',
 'https://cloud.tencent.com/document/product/1073/92668', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-10 00:00:00+00', TIMESTAMPTZ '2026-09-10 00:00:00+00'),
(40005, 'coco', 'tencent', '502007', '智小虎',
 '中英聊天童声；活泼亲切，贴合萌宠/低龄',
 'https://cloud.tencent.com/document/product/1073/92668', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-10 00:00:00+00', TIMESTAMPTZ '2026-09-10 00:00:00+00'),
(40006, 'alex', 'tencent', '603000', '懂事少年',
 '中英特色男声；少年感，贴合游戏关卡型',
 'https://cloud.tencent.com/document/product/1073/92668', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-10 00:00:00+00', TIMESTAMPTZ '2026-09-10 00:00:00+00')
ON CONFLICT (persona_id, vendor_code) DO NOTHING;
