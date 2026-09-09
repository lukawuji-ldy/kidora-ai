-- 语音供应商主备路由（单行权威）
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
