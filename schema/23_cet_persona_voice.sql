-- CET 人设 ↔ 语音厂商音色映射
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
COMMENT ON COLUMN cet_persona_voice.voice_id IS '厂商音色 id（腾讯 VoiceType / 讯飞 vcn）';
COMMENT ON COLUMN cet_persona_voice.voice_name IS '音色展示名';
COMMENT ON COLUMN cet_persona_voice.voice_traits IS '音色特点（运营可读中文）';
COMMENT ON COLUMN cet_persona_voice.catalog_url IS '官方音色对照表 URL';
COMMENT ON COLUMN cet_persona_voice.locale IS '建议朗读 locale';
COMMENT ON COLUMN cet_persona_voice.status IS 'ACTIVE｜DISABLED';
COMMENT ON COLUMN cet_persona_voice.create_time IS '创建时间';
COMMENT ON COLUMN cet_persona_voice.update_time IS '更新时间';
