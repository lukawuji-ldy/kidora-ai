-- CET 道具可用性闸门：没有已发布图片时禁止生成图片指认问题。
DO $$
DECLARE
    v_code TEXT;
    tpl_id BIGINT;
    cur_ver INT;
    current_content TEXT;
    guarded_content TEXT;
BEGIN
    FOREACH v_code IN ARRAY ARRAY[
        'cet.tutor.system',
        'cet.tutor.user',
        'cet.tutor.opening.system',
        'cet.tutor.opening.user'
    ] LOOP
        SELECT id, published_version, content
        INTO tpl_id, cur_ver, current_content
        FROM prompt_template
        WHERE code = v_code;

        IF tpl_id IS NULL OR current_content LIKE '%{{propInstruction}}%' THEN
            CONTINUE;
        END IF;

        guarded_content := current_content || E'\n\n【道具可用性动态约束】\n{{propInstruction}}\n可用道具词：{{availablePropLemmas}}\n';

        UPDATE prompt_template_version
        SET status = 'SUPERSEDED',
            change_note = COALESCE(change_note, 'superseded by V30')
        WHERE code = v_code
          AND version = cur_ver
          AND status = 'PUBLISHED';

        INSERT INTO prompt_template_version (
            id, code, version, name, role, prompt_group, content, status,
            change_note, created_by, create_time, publish_time
        )
        SELECT tpl_id * 1000 + COALESCE(cur_ver, 0) + 1,
               v_code,
               COALESCE(cur_ver, 0) + 1,
               name,
               role,
               prompt_group,
               guarded_content,
               'PUBLISHED',
               'V30 prop availability guard',
               'system',
               CURRENT_TIMESTAMP,
               CURRENT_TIMESTAMP
        FROM prompt_template
        WHERE code = v_code
        ON CONFLICT (code, version) DO NOTHING;

        UPDATE prompt_template
        SET content = guarded_content,
            published_version = COALESCE(cur_ver, 0) + 1,
            update_time = CURRENT_TIMESTAMP
        WHERE code = v_code;
    END LOOP;
END $$;
