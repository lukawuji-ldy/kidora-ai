-- CET 教具道具资源库（本地文件元数据；同 lemma 复用）
CREATE TABLE IF NOT EXISTS cet_prop_asset
(
    id                   BIGINT        PRIMARY KEY,
    lemma                VARCHAR(64)   NOT NULL,
    aliases_json         JSONB         NOT NULL DEFAULT '[]'::jsonb,
    theme                VARCHAR(32)   NOT NULL DEFAULT 'default',
    storage_path         VARCHAR(512)  NOT NULL,
    content_type         VARCHAR(128)  NOT NULL DEFAULT 'image/webp',
    byte_size            BIGINT        NOT NULL DEFAULT 0,
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    source_code          VARCHAR(32)   NOT NULL DEFAULT 'local',
    license_code         VARCHAR(32)   NOT NULL DEFAULT 'PD',
    license_url          VARCHAR(512),
    author               VARCHAR(255),
    source_url           VARCHAR(1024),
    attribution_required BOOLEAN       NOT NULL DEFAULT FALSE,
    checksum_sha256      CHAR(64),
    width                INTEGER       NOT NULL DEFAULT 0,
    height               INTEGER       NOT NULL DEFAULT 0,
    create_time          TIMESTAMPTZ   NOT NULL,
    update_time          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_cet_prop_asset_lemma UNIQUE (lemma),
    CONSTRAINT ck_cet_prop_asset_source CHECK (
        source_code IN ('openmoji', 'openclipart', 'wikimedia', 'pixabay',
                        'kenney', 'local', 'generated')
    ),
    CONSTRAINT ck_cet_prop_asset_license CHECK (
        license_code IN ('CC0-1.0', 'PD', 'CC-BY-4.0', 'CC-BY-SA-4.0', 'PIXABAY')
    )
);

CREATE INDEX IF NOT EXISTS idx_cet_prop_asset_status_lemma
    ON cet_prop_asset (status, lemma);

COMMENT ON TABLE cet_prop_asset IS 'CET 教具道具本地资源库（运营预置；lemma 唯一复用）';
COMMENT ON COLUMN cet_prop_asset.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_prop_asset.lemma IS '规范词干（小写英文），唯一检索键';
COMMENT ON COLUMN cet_prop_asset.aliases_json IS '别名 JSON 数组（如 puppy/狗），运行时词表的唯一权威';
COMMENT ON COLUMN cet_prop_asset.theme IS '主题：pets|colors|food|default 等';
COMMENT ON COLUMN cet_prop_asset.storage_path IS '相对 kidora.cet.props.local-dir 的路径';
COMMENT ON COLUMN cet_prop_asset.content_type IS 'MIME，如 image/webp';
COMMENT ON COLUMN cet_prop_asset.byte_size IS '文件字节数';
COMMENT ON COLUMN cet_prop_asset.status IS 'ACTIVE|DISABLED';
COMMENT ON COLUMN cet_prop_asset.source_code IS '素材来源：openmoji|openclipart|wikimedia|pixabay|kenney|local|generated';
COMMENT ON COLUMN cet_prop_asset.license_code IS '许可协议白名单：CC0-1.0|PD|CC-BY-4.0|CC-BY-SA-4.0|PIXABAY';
COMMENT ON COLUMN cet_prop_asset.license_url IS '许可协议原文链接';
COMMENT ON COLUMN cet_prop_asset.author IS '原作者署名（CC-BY 系列必填）';
COMMENT ON COLUMN cet_prop_asset.source_url IS '素材原始页面链接，便于复核版权';
COMMENT ON COLUMN cet_prop_asset.attribution_required IS '是否必须在前台署名页展示';
COMMENT ON COLUMN cet_prop_asset.checksum_sha256 IS '文件 SHA-256，用于体检与 HTTP ETag';
COMMENT ON COLUMN cet_prop_asset.width IS '图片像素宽；0 表示未采集或矢量图';
COMMENT ON COLUMN cet_prop_asset.height IS '图片像素高；0 表示未采集或矢量图';
COMMENT ON COLUMN cet_prop_asset.create_time IS '创建时间';
COMMENT ON COLUMN cet_prop_asset.update_time IS '更新时间';
