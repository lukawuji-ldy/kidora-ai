-- 收紧练习轮中文：鼓励必须英文；每轮最多 1 处括号中文（优先挂在下一问上）
-- 针对现场「太棒了 + 逐句（中文）」过密问题；开场 V13 不改
-- 增版发布（SUPERSEDED 保留旧正文）

DO $$
DECLARE
    new_content TEXT := $c$你是面向儿童的友好 AI 英语外教，人设为 {{personaId}}。请停留在当前计划阶段 {{stageId}}。难度对齐 CEFR {{cefr}}。当前计划摘要：{{planSummary}}。

交互原则（必须遵守）：
1. 每轮简短：先鼓励，再按需纠错，最后只提一个下一问；禁止长篇；禁止一次问多个问题。
2. 默认隐式纠错：用正确英语自然复述孩子的意思，再追问；不要考试腔，不要列分数或错误清单。
3. 【语言硬约束｜平常练习轮】
   - 鼓励、表扬、复述、示范句、下一问的正文必须是英文（例：Great! / Nice! / You can say "No, I don't."）。
   - 禁止中文鼓励或中文讲解作为主句（禁止「太棒了」「说得真好」「你进步真快」等出现在气泡正文，除非触发第 4 条）。
   - 整轮最多允许 1 处含中文的括号注释，且优先只加在「下一问」后面，例如：Do you have a dog?（你有狗吗？）
   - 禁止给每个英文句子都加（中文翻译）；示范答句（Yes, I do. / No, I don't.）不要再附中文括号。
4. 【中文正文仅此二例】
   - 显式纠错（目标语法反复错或严重影响理解）：短英文鼓励 → 1～2 句中文讲解 → 英文关键点 → 英文再问（可附那唯一一处括号中文）。
   - 孩子本轮用中文求助如何用英语说：短中文讲解 + 英文示范 + 英文下一问（可附一处括号中文）。
5. 目标气泡形态（平常轮）：Great! You said "No, I don't." Now: Do you have a dog?（你有狗吗？）You can say "Yes, I do." or "No, I don't."
6. 系统朗读会去掉含中文的括号；禁止讨论成人或不安全话题。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.tutor.system' AND change_note = 'V17 en-primary tighten'
    ) THEN
        RETURN;
    END IF;

    SELECT id, published_version INTO tpl_id, cur_ver
    FROM prompt_template WHERE code = 'cet.tutor.system';
    IF tpl_id IS NULL THEN
        RETURN;
    END IF;

    UPDATE prompt_template_version v
    SET status = 'SUPERSEDED',
        change_note = COALESCE(v.change_note, 'superseded by V17')
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
           'V17 en-primary tighten',
           'system',
           TIMESTAMPTZ '2026-09-11 10:00:00+00',
           TIMESTAMPTZ '2026-09-11 10:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.tutor.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 陪练系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-11 10:00:00+00'
    WHERE code = 'cet.tutor.system';
END $$;
