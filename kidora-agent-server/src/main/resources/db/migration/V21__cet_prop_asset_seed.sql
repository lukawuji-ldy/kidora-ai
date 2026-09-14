-- CET 教具道具 seed 元数据（文件需复制到 kidora.cet.props.local-dir，见 scripts/seed-cet-props.md）

INSERT INTO cet_prop_asset
(id, lemma, aliases_json, theme, storage_path, content_type, byte_size, status, create_time, update_time)
VALUES
(50001, 'dog', '["puppy","狗","小狗"]'::jsonb, 'pets', 'pets/dog.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00'),
(50002, 'cat', '["kitten","猫","小猫"]'::jsonb, 'pets', 'pets/cat.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00'),
(50003, 'fish', '["鱼"]'::jsonb, 'pets', 'pets/fish.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00'),
(50004, 'bird', '["鸟"]'::jsonb, 'pets', 'pets/bird.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00'),
(50005, 'pet', '["宠物"]'::jsonb, 'pets', 'pets/pet.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00'),
(50006, 'apple', '["fruit","苹果","水果"]'::jsonb, 'food', 'food/apple.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00'),
(50007, 'food', '["食物"]'::jsonb, 'food', 'food/food.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00'),
(50008, 'red', '["红","红色"]'::jsonb, 'colors', 'colors/red.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00'),
(50009, 'blue', '["蓝","蓝色"]'::jsonb, 'colors', 'colors/blue.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00'),
(50010, 'green', '["绿"]'::jsonb, 'colors', 'colors/green.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00'),
(50011, 'yellow', '["黄"]'::jsonb, 'colors', 'colors/yellow.svg', 'image/svg+xml', 0, 'ACTIVE',
 TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00')
ON CONFLICT (lemma) DO NOTHING;
