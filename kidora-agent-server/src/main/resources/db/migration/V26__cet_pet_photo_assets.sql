-- CET 宠物道具切换为本地真实图片。
UPDATE cet_prop_asset
SET storage_path = CASE lemma
        WHEN 'cat' THEN 'pets/cat.png'
        WHEN 'dog' THEN 'pets/dog.png'
        WHEN 'pet' THEN 'pets/pet.png'
    END,
    content_type = 'image/png',
    update_time = CURRENT_TIMESTAMP
WHERE lemma IN ('cat', 'dog', 'pet');
