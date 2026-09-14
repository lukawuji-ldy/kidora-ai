-- CET 练习轮英文为主脚手架 + Planner childGoals
-- 开场 cet.tutor.opening.* 保持 V13，本迁移不改
-- 发版方式：旧 published 行标 SUPERSEDED（正文保留），再 INSERT 新 version 并提升 published_version
-- 禁止再对 published 行原地覆盖 content（会丢掉历史）

-- ---------- cet.tutor.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你是面向儿童的友好 AI 英语外教，人设为 {{personaId}}。请停留在当前计划阶段 {{stageId}}。难度对齐 CEFR {{cefr}}。当前计划摘要：{{planSummary}}。

交互原则（必须遵守）：
1. 每轮简短：先鼓励，再按需纠错，最后只提一个下一问；禁止长篇讲课或一次问多个问题。
2. 默认隐式纠错：用正确英语自然复述孩子的意思，再追问；不要考试腔，不要列出分数或错误清单。
3. 练习轮默认以英文为主：鼓励、隐式纠错、引导、下一问都用英文；英文句子后可以附一个（中文注释）供屏幕阅读。禁止整段「英文主句 + 逐句括号翻译」堆叠。
4. 中文正文仅在以下情况使用：
   - 显式纠错（目标语法反复出错或严重影响理解）：短鼓励 → 1～3 句中文讲解（为什么）→ 英文关键点/正确句 → 请孩子再说一次（英文问句，可附括号中文）；
   - 孩子用中文求助如何用英语表达：可用短中文讲解 + 英文示范句 + 英文下一问（可附括号中文）。
5. A0/A1 与 A2+ 平常练习轮均不以中文追问为默认；下一问用英文，低龄可在问句后附（中文注释）。
6. 系统朗读会去掉含中文的括号，并朗读中文正文与英文例句。
7. 禁止讨论成人或不安全话题。$c$;
    old_fallback TEXT := $o$你是面向儿童的友好 AI 英语外教，人设为 {{personaId}}。请停留在当前计划阶段 {{stageId}}。难度对齐 CEFR {{cefr}}。当前计划摘要：{{planSummary}}。

交互原则（必须遵守）：
1. 每轮简短：先鼓励，再按需纠错，最后只提一个下一问；禁止长篇讲课或一次问多个问题。
2. 默认隐式纠错：用正确英语自然复述孩子的意思，再追问；不要考试腔，不要列出分数或错误清单。
3. 显式纠错（目标语法反复出错或严重影响理解）：短鼓励 → 1～3 句中文讲解（为什么）→ 英文关键点/正确句 → 请孩子再说一次。中文讲解控制在 1～3 句。
4. 中文脚手架（以下任一即启用，优先于「A2+ 英文为主」）：
   - CEFR 为 A0 或 A1；
   - 孩子用中文求助如何用英语表达；
   - 本轮显式纠错。
   启用时：鼓励、讲解、引导、追问用中文；示范句与关键点用英文（可给 1～2 个短句对照）。A0/A1 下一问默认中文；必要时可用英文问句并在后附（中文注释）。
5. A2 及以上且未触发脚手架：以英文为主，必要时一句中文点拨。
6. 英文句子后可以附（中文注释）供屏幕阅读；禁止整段「英文主句 + 逐句括号翻译」堆叠。系统朗读会去掉含中文的括号，并朗读中文正文与英文例句。
7. 禁止讨论成人或不安全话题。$o$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.tutor.system' AND change_note = 'V15 en-primary scaffold'
    ) THEN
        RETURN;
    END IF;

    SELECT id, published_version INTO tpl_id, cur_ver
    FROM prompt_template WHERE code = 'cet.tutor.system';
    IF tpl_id IS NULL THEN
        RETURN;
    END IF;

    -- 若 published 行已被原地覆盖成新文，先写回 V12 正文再 SUPERSEDED
    UPDATE prompt_template_version v
    SET content = CASE WHEN v.content = new_content THEN old_fallback ELSE v.content END,
        status = 'SUPERSEDED',
        change_note = CASE
            WHEN v.content = new_content THEN 'pre-V15 zh scaffold (restored)'
            ELSE COALESCE(v.change_note, 'superseded by V15')
        END
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
           'V15 en-primary scaffold',
           'system',
           TIMESTAMPTZ '2026-09-11 08:00:00+00',
           TIMESTAMPTZ '2026-09-11 08:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.tutor.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 陪练系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-11 08:00:00+00'
    WHERE code = 'cet.tutor.system';
END $$;

-- ---------- cet.planner.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你是儿童英语课时规划助手。只输出合法 JSON，字段为：topic、cefr、personaId、objectives（字符串数组或含 vocabulary/patterns 的对象）、stages（对象数组，含 id、name、goal、targetTurns）、childSummary（给孩子看的简短友好中文摘要）、childGoals（恰好 3 条中文短句，语义递进：①认识词/短语 ②会说目标句 ③用起来简短问答）。stages 须包含 warmup、vocab、model、dialog、wrapup。措辞须适合 CEFR {{cefr}} 年龄与水平。$c$;
    old_fallback TEXT := $o$你是儿童英语课时规划助手。只输出合法 JSON，字段为：topic、cefr、personaId、objectives（字符串数组）、stages（对象数组，含 id、name、goal、targetTurns）、childSummary（给孩子看的简短友好中文摘要）。stages 须包含 warmup、vocab、model、dialog、wrapup。措辞须适合 CEFR {{cefr}} 年龄与水平。$o$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.planner.system' AND change_note = 'V15 en-primary scaffold'
    ) THEN
        RETURN;
    END IF;

    SELECT id, published_version INTO tpl_id, cur_ver
    FROM prompt_template WHERE code = 'cet.planner.system';
    IF tpl_id IS NULL THEN
        RETURN;
    END IF;

    UPDATE prompt_template_version v
    SET content = CASE WHEN v.content = new_content THEN old_fallback ELSE v.content END,
        status = 'SUPERSEDED',
        change_note = CASE
            WHEN v.content = new_content THEN 'pre-V15 planner (restored)'
            ELSE COALESCE(v.change_note, 'superseded by V15')
        END
    WHERE v.code = 'cet.planner.system'
      AND v.version = cur_ver
      AND v.status = 'PUBLISHED';

    INSERT INTO prompt_template_version (
        id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time
    )
    SELECT tpl_id * 1000 + (cur_ver + 1),
           'cet.planner.system',
           cur_ver + 1,
           'CET 教案规划系统提示',
           role,
           prompt_group,
           new_content,
           'PUBLISHED',
           'V15 en-primary scaffold',
           'system',
           TIMESTAMPTZ '2026-09-11 08:00:00+00',
           TIMESTAMPTZ '2026-09-11 08:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.planner.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 教案规划系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-11 08:00:00+00'
    WHERE code = 'cet.planner.system';
END $$;
