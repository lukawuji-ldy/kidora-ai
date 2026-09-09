-- MVP-2C：语音供应商目录 + 主备路由（默认讯飞 / 备选腾讯 / 第三档 Azure 暂不用）

CREATE TABLE IF NOT EXISTS speech_vendor_config
(
    id                  BIGINT        PRIMARY KEY,
    vendor_code         VARCHAR(32)   NOT NULL,
    name                VARCHAR(128)  NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    credentials_cipher  TEXT,
    extra_json          JSONB,
    create_time         TIMESTAMPTZ   NOT NULL,
    update_time         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_speech_vendor_code UNIQUE (vendor_code)
);

COMMENT ON TABLE speech_vendor_config IS '语音供应商配置（讯飞/腾讯/Azure/stub）；密钥密文入库';
COMMENT ON COLUMN speech_vendor_config.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN speech_vendor_config.vendor_code IS 'iflytek | tencent | azure | stub';
COMMENT ON COLUMN speech_vendor_config.name IS '展示名';
COMMENT ON COLUMN speech_vendor_config.status IS 'ACTIVE | DISABLED';
COMMENT ON COLUMN speech_vendor_config.credentials_cipher IS '凭证 JSON 密文（enc:v1:）；空表示未配置';
COMMENT ON COLUMN speech_vendor_config.extra_json IS '非密扩展：region/voice/engine 等';
COMMENT ON COLUMN speech_vendor_config.create_time IS '创建时间';
COMMENT ON COLUMN speech_vendor_config.update_time IS '更新时间';

CREATE TABLE IF NOT EXISTS speech_route
(
    id               BIGINT       PRIMARY KEY,
    primary_vendor   VARCHAR(32)  NOT NULL,
    backup_vendor    VARCHAR(32)  NOT NULL,
    tertiary_vendor  VARCHAR(32)  NOT NULL DEFAULT 'azure',
    update_time      TIMESTAMPTZ  NOT NULL
);

COMMENT ON TABLE speech_route IS '语音主备路由：运行时仅用 primary；backup 不自动 failover；tertiary 预留 Azure';
COMMENT ON COLUMN speech_route.id IS '固定为 1';
COMMENT ON COLUMN speech_route.primary_vendor IS '默认供应商：iflytek | tencent';
COMMENT ON COLUMN speech_route.backup_vendor IS '备选供应商（管理台配置，运行时不自动切换）';
COMMENT ON COLUMN speech_route.tertiary_vendor IS '第三档，默认 azure，本阶段暂不启用';
COMMENT ON COLUMN speech_route.update_time IS '更新时间';

INSERT INTO speech_vendor_config (id, vendor_code, name, status, credentials_cipher, extra_json, create_time, update_time)
VALUES
(30001, 'iflytek', '讯飞（听写/合成/ISE）', 'ACTIVE', NULL,
 '{"engine":"en_vip","category":"read_sentence"}'::jsonb,
 TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(30002, 'tencent', '腾讯云（ASR/TTS/智聆SOE）', 'ACTIVE', NULL,
 '{"serverEngineType":"16k_en","evalMode":1}'::jsonb,
 TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(30003, 'azure', 'Azure Speech（第三档暂不用）', 'ACTIVE', NULL,
 '{"defaultVoice":"en-US-AvaNeural"}'::jsonb,
 TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00')
ON CONFLICT (vendor_code) DO NOTHING;

INSERT INTO speech_route (id, primary_vendor, backup_vendor, tertiary_vendor, update_time)
VALUES (1, 'iflytek', 'tencent', 'azure', TIMESTAMPTZ '2026-09-09 00:00:00+00')
ON CONFLICT (id) DO NOTHING;
