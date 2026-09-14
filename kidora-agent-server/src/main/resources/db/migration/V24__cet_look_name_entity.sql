-- CET 开场/陪练：Look 问句必须点名可展示实体词（避免裸 What is this? 无道具可配）
-- Flyway 版本号 V24（V23 已被 persona_voice_iflytek 占用）
-- 增版；幂等 change_note = V23 look name entity（内容已按该 note 发布）

-- ---------- cet.tutor.opening.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你是面向儿童的友好 AI 英语外教。人设 id={{personaId}}，展示名={{personaName}}。难度对齐 CEFR {{cefr}}。主题：{{topic}}。计划摘要：{{planSummary}}。warmup 阶段目标：{{stageGoal}}。当前时段问候语（必须原样使用）：{{dayGreeting}}。

语气：极度热情、友爱、口语化短句，像见到孩子很开心；可少量口语鼓励，但禁止堆砌感叹号、禁止煽情过头、禁止长篇。

若 CEFR 为 A0 或 A1，开场必须按以下三段顺序，且只提一个问题：
1）英文问候 + 自我介绍：必须使用展示名 {{personaName}} 与时段问候 {{dayGreeting}}（勿臆造人名或时段）。例：Hi! I'm Emma. Good morning!
2）中文点题：点出今天主题，英文关键词可放在括号里。例：你好！今天我们来聊聊颜色（colors）。
3）英文下一问 + 括号中文注释：必须用 Look 指认，且问句里至少点出一个本课实体英文词（cat/dog/fish/bird/apple 等），禁止只说 Look! What is this? 而不点名物体。例：Look! Is this a cat or a dog?（看，这是猫还是狗？）或 Look! A dog — what color is it?（看！这只狗——它是什么颜色？）

A2 及以上：短英文开场（可含自我介绍与 {{dayGreeting}}）+ 一个简单英文问题（同样须 Look/指认并点名实体词）；括号中文最多 1～2 处；不强制中文点题段。

禁止一次问多个问题、禁止不安全话题。英文句子后可附（中文注释）供屏幕阅读；系统朗读会去掉含中文的括号，并朗读中文正文与英文例句。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.tutor.opening.system' AND change_note = 'V23 look name entity'
    ) THEN
        RETURN;
    END IF;

    SELECT id, published_version INTO tpl_id, cur_ver
    FROM prompt_template WHERE code = 'cet.tutor.opening.system';
    IF tpl_id IS NULL THEN
        RETURN;
    END IF;

    UPDATE prompt_template_version v
    SET status = 'SUPERSEDED',
        change_note = COALESCE(v.change_note, 'superseded by V23')
    WHERE v.code = 'cet.tutor.opening.system'
      AND v.version = cur_ver
      AND v.status = 'PUBLISHED';

    INSERT INTO prompt_template_version (
        id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time
    )
    SELECT tpl_id * 1000 + (cur_ver + 1),
           'cet.tutor.opening.system',
           cur_ver + 1,
           'CET 开场系统提示',
           role,
           prompt_group,
           new_content,
           'PUBLISHED',
           'V23 look name entity',
           'system',
           TIMESTAMPTZ '2026-09-14 01:00:00+00',
           TIMESTAMPTZ '2026-09-14 01:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.tutor.opening.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 开场系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-14 01:00:00+00'
    WHERE code = 'cet.tutor.opening.system';
END $$;

-- ---------- cet.tutor.system：强化 Look 须点名实体 ----------
DO $$
DECLARE
    new_content TEXT;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.tutor.system' AND change_note = 'V23 look name entity'
    ) THEN
        RETURN;
    END IF;

    SELECT id, published_version, content INTO tpl_id, cur_ver, new_content
    FROM prompt_template WHERE code = 'cet.tutor.system';
    IF tpl_id IS NULL THEN
        RETURN;
    END IF;

    -- 在 V22 正文上追加一句硬约束（若已含则跳过替换逻辑用全文覆写片段）
    new_content := replace(
        new_content,
        '禁止整段只问抽象喜好而不指物。',
        '禁止整段只问抽象喜好而不指物。禁止只说 Look! What is this? 而不点名至少一个本课实体英文词（须说 cat/dog 等可展示词，或 Is this a cat or a dog?）。'
    );

    UPDATE prompt_template_version v
    SET status = 'SUPERSEDED',
        change_note = COALESCE(v.change_note, 'superseded by V23')
    WHERE v.code = 'cet.tutor.system'
      AND v.version = cur_ver
      AND v.status = 'PUBLISHED';

    INSERT INTO prompt_template_version (
        id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time
    )
    SELECT tpl_id * 1000 + (cur_ver + 1),
           'cet.tutor.system',
           cur_ver + 1,
           'CET 陪练系统提示',
           role,
           prompt_group,
           new_content,
           'PUBLISHED',
           'V23 look name entity',
           'system',
           TIMESTAMPTZ '2026-09-14 01:00:00+00',
           TIMESTAMPTZ '2026-09-14 01:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.tutor.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 陪练系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-14 01:00:00+00'
    WHERE code = 'cet.tutor.system';
END $$;
