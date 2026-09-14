-- CET 道具资产补齐来源 / 许可 / 校验元数据，并把别名词表下沉到库（代码不再硬编码）。

ALTER TABLE cet_prop_asset
    ADD COLUMN IF NOT EXISTS source_code          VARCHAR(32)  NOT NULL DEFAULT 'local',
    ADD COLUMN IF NOT EXISTS license_code         VARCHAR(32)  NOT NULL DEFAULT 'PD',
    ADD COLUMN IF NOT EXISTS license_url          VARCHAR(512),
    ADD COLUMN IF NOT EXISTS author               VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source_url           VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS attribution_required BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS checksum_sha256      CHAR(64),
    ADD COLUMN IF NOT EXISTS width                INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS height               INTEGER      NOT NULL DEFAULT 0;

-- 许可白名单：只允许无版权风险的协议进库，导入脚本同步校验。
ALTER TABLE cet_prop_asset
    DROP CONSTRAINT IF EXISTS ck_cet_prop_asset_source;
ALTER TABLE cet_prop_asset
    ADD CONSTRAINT ck_cet_prop_asset_source CHECK (
        source_code IN ('openmoji', 'openclipart', 'wikimedia', 'pixabay',
                        'kenney', 'local', 'generated')
    );

ALTER TABLE cet_prop_asset
    DROP CONSTRAINT IF EXISTS ck_cet_prop_asset_license;
ALTER TABLE cet_prop_asset
    ADD CONSTRAINT ck_cet_prop_asset_license CHECK (
        license_code IN ('CC0-1.0', 'PD', 'CC-BY-4.0', 'CC-BY-SA-4.0', 'PIXABAY')
    );

COMMENT ON COLUMN cet_prop_asset.source_code IS '素材来源：openmoji|openclipart|wikimedia|pixabay|kenney|local|generated';
COMMENT ON COLUMN cet_prop_asset.license_code IS '许可协议白名单：CC0-1.0|PD|CC-BY-4.0|CC-BY-SA-4.0|PIXABAY';
COMMENT ON COLUMN cet_prop_asset.license_url IS '许可协议原文链接';
COMMENT ON COLUMN cet_prop_asset.author IS '原作者署名（CC-BY 系列必填）';
COMMENT ON COLUMN cet_prop_asset.source_url IS '素材原始页面链接，便于复核版权';
COMMENT ON COLUMN cet_prop_asset.attribution_required IS '是否必须在前台署名页展示';
COMMENT ON COLUMN cet_prop_asset.checksum_sha256 IS '文件 SHA-256，用于体检与 HTTP ETag';
COMMENT ON COLUMN cet_prop_asset.width IS '图片像素宽；0 表示未采集或矢量图';
COMMENT ON COLUMN cet_prop_asset.height IS '图片像素高；0 表示未采集或矢量图';

-- 存量 11 行为本仓自绘素材，归入公有领域，无需署名。
UPDATE cet_prop_asset
SET source_code = 'local',
    license_code = 'PD',
    attribution_required = FALSE,
    update_time = CURRENT_TIMESTAMP
WHERE source_code IS NULL OR source_code = 'local';

-- 别名词表下沉：原先写死在 PropAssetResolver 的映射改由 aliases_json 提供。
UPDATE cet_prop_asset SET aliases_json = '["puppy","狗","小狗"]'::jsonb WHERE lemma = 'dog';
UPDATE cet_prop_asset SET aliases_json = '["kitten","猫","小猫"]'::jsonb WHERE lemma = 'cat';
UPDATE cet_prop_asset SET aliases_json = '["pets","animal","animals","宠物","动物"]'::jsonb WHERE lemma = 'pet';
UPDATE cet_prop_asset SET aliases_json = '["鱼","小鱼"]'::jsonb WHERE lemma = 'fish';
UPDATE cet_prop_asset SET aliases_json = '["鸟","小鸟"]'::jsonb WHERE lemma = 'bird';
UPDATE cet_prop_asset SET aliases_json = '["红","红色"]'::jsonb WHERE lemma = 'red';
UPDATE cet_prop_asset SET aliases_json = '["蓝","蓝色"]'::jsonb WHERE lemma = 'blue';
UPDATE cet_prop_asset SET aliases_json = '["绿","绿色"]'::jsonb WHERE lemma = 'green';
UPDATE cet_prop_asset SET aliases_json = '["黄","黄色"]'::jsonb WHERE lemma = 'yellow';
UPDATE cet_prop_asset SET aliases_json = '["fruit","苹果","水果"]'::jsonb WHERE lemma = 'apple';
UPDATE cet_prop_asset SET aliases_json = '["食物","吃的"]'::jsonb WHERE lemma = 'food';
