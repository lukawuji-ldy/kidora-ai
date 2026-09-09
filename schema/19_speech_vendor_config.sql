-- 语音供应商配置目录
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
