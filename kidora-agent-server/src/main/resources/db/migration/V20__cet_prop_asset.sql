-- CET 教具道具资源库（本地文件元数据；同 lemma 复用）

CREATE TABLE IF NOT EXISTS cet_prop_asset
(
    id            BIGINT        PRIMARY KEY,
    lemma         VARCHAR(64)   NOT NULL,
    aliases_json  JSONB         NOT NULL DEFAULT '[]'::jsonb,
    theme         VARCHAR(32)   NOT NULL DEFAULT 'default',
    storage_path  VARCHAR(512)  NOT NULL,
    content_type  VARCHAR(128)  NOT NULL DEFAULT 'image/webp',
    byte_size     BIGINT        NOT NULL DEFAULT 0,
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    create_time   TIMESTAMPTZ   NOT NULL,
    update_time   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_cet_prop_asset_lemma UNIQUE (lemma)
);

CREATE INDEX IF NOT EXISTS idx_cet_prop_asset_status_lemma
    ON cet_prop_asset (status, lemma);

COMMENT ON TABLE cet_prop_asset IS 'CET 教具道具本地资源库（运营预置；lemma 唯一复用）';
COMMENT ON COLUMN cet_prop_asset.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_prop_asset.lemma IS '规范词干（小写英文），唯一检索键';
COMMENT ON COLUMN cet_prop_asset.aliases_json IS '别名 JSON 数组（如 puppy/狗），检索时归一到 lemma';
COMMENT ON COLUMN cet_prop_asset.theme IS '主题：pets|colors|food|default';
COMMENT ON COLUMN cet_prop_asset.storage_path IS '相对 kidora.cet.props.local-dir 的路径';
COMMENT ON COLUMN cet_prop_asset.content_type IS 'MIME，如 image/webp';
COMMENT ON COLUMN cet_prop_asset.byte_size IS '文件字节数';
COMMENT ON COLUMN cet_prop_asset.status IS 'ACTIVE|DISABLED';
COMMENT ON COLUMN cet_prop_asset.create_time IS '创建时间';
COMMENT ON COLUMN cet_prop_asset.update_time IS '更新时间';
