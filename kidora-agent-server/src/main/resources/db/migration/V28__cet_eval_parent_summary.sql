-- CET 评测：结课产出 parentSummary（家长可读小结）
-- 增版发布；幂等 change_note = V28 parent summary

-- ---------- cet.eval.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你负责评估儿童英语练习会话。只输出 JSON：{grammar:number 0-100, vocabulary:number 0-100, fluency:number 0-100, problems:string[], decision:"continue"|"replan"|"complete", focus:string[], pauseNewVocab:boolean, insertStage:object|null, encouragement:string, childSummary:string, parentSummary:string}。未达标或需微练习时用 replan；阶段可继续用 continue；课时可结束用 complete。
encouragement、childSummary、parentSummary 用中文。problems 须摘录孩子真实错句或口误。childSummary 必须含 1～2 条「孩子原话 → 正确说法」的具体改写，禁止只有「还有提升空间」「继续加油」类空话；encouragement 可短夸，不得替代带错例的 childSummary。
parentSummary 给家长阅读：2～4 句，说明本节练了什么、整体表现、1 个亮点、1～2 条回家可练建议；须引用转写中的具体表现或错例，禁止空泛套话。分数仅作参考说明，勿写成正式测评。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.eval.system' AND change_note = 'V28 parent summary'
    ) THEN
        RETURN;
    END IF;

    SELECT id, published_version INTO tpl_id, cur_ver
    FROM prompt_template WHERE code = 'cet.eval.system';
    IF tpl_id IS NULL THEN
        RETURN;
    END IF;

    UPDATE prompt_template_version v
    SET status = 'SUPERSEDED',
        change_note = COALESCE(v.change_note, 'superseded by V28')
    WHERE v.code = 'cet.eval.system'
      AND v.version = cur_ver
      AND v.status = 'PUBLISHED';

    INSERT INTO prompt_template_version (
        id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time
    )
    SELECT tpl_id * 1000 + (cur_ver + 1),
           'cet.eval.system',
           cur_ver + 1,
           'CET 评测系统提示',
           role,
           prompt_group,
           new_content,
           'PUBLISHED',
           'V28 parent summary',
           'system',
           TIMESTAMPTZ '2026-09-14 08:00:00+00',
           TIMESTAMPTZ '2026-09-14 08:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.eval.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 评测系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-14 08:00:00+00'
    WHERE code = 'cet.eval.system';
END $$;

-- ---------- cet.eval.user ----------
DO $$
DECLARE
    new_content TEXT := $c$主题={{topic}}，CEFR={{cefr}}，模式={{evalMode}}。当前计划：{{planJson}}。转写文本：
{{transcript}}
请返回含 decision 的评测 JSON。problems 与 childSummary 须从转写中摘录真实错句，并写成「原话 → 正确说法」；childSummary 至少含 1～2 条具体改写，禁止空泛总结。
parentSummary 用中文写给家长：覆盖本节主题、参考表现、具体错例或亮点、下次练习建议；禁止只有「表现不错/继续加油」。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.eval.user' AND change_note = 'V28 parent summary'
    ) THEN
        RETURN;
    END IF;

    SELECT id, published_version INTO tpl_id, cur_ver
    FROM prompt_template WHERE code = 'cet.eval.user';
    IF tpl_id IS NULL THEN
        RETURN;
    END IF;

    UPDATE prompt_template_version v
    SET status = 'SUPERSEDED',
        change_note = COALESCE(v.change_note, 'superseded by V28')
    WHERE v.code = 'cet.eval.user'
      AND v.version = cur_ver
      AND v.status = 'PUBLISHED';

    INSERT INTO prompt_template_version (
        id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time
    )
    SELECT tpl_id * 1000 + (cur_ver + 1),
           'cet.eval.user',
           cur_ver + 1,
           'CET 评测用户提示',
           role,
           prompt_group,
           new_content,
           'PUBLISHED',
           'V28 parent summary',
           'system',
           TIMESTAMPTZ '2026-09-14 08:00:00+00',
           TIMESTAMPTZ '2026-09-14 08:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.eval.user'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 评测用户提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-14 08:00:00+00'
    WHERE code = 'cet.eval.user';
END $$;
