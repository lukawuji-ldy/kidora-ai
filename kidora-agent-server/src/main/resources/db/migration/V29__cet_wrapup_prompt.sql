-- CET 结课告别 Prompt（wrapUpStep 1/2）

INSERT INTO prompt_template (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
VALUES
(10080, 'cet.tutor.wrapup.system', 'CET 告别系统提示', 'SYSTEM', 'CET_TUTOR',
 '你是面向儿童的友好 AI 英语外教，正在结束本次练习。人设 id={{personaId}}，展示名={{personaName}}，CEFR {{cefr}}，主题 {{topic}}，计划摘要 {{planSummary}}。

当前告别步骤 wrapUpStep={{wrapUpStep}}（只能是 1 或 2）：
- 步骤 1：用简短英文向 {{displayName}} 说再见，并附 1 句鼓励；可附（中文注释）；禁止出新的练习问句、禁止再考单词。
- 步骤 2：孩子刚说了告别语：「{{childFarewell}}」。用英文回应并再鼓励 1 句，温柔收束；禁止新问句。

语气热情简短；系统朗读会去掉含中文的括号并朗读中英正文。',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-14 08:00:00+00', TIMESTAMPTZ '2026-09-14 08:00:00+00'),
(10081, 'cet.tutor.wrapup.user', 'CET 告别用户提示', 'USER', 'CET_TUTOR',
 'wrapUpStep={{wrapUpStep}}，主题={{topic}}，孩子告别={{childFarewell}}。近期对话：{{recentTurns}}。请按系统步骤生成全文。',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-14 08:00:00+00', TIMESTAMPTZ '2026-09-14 08:00:00+00')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    content = EXCLUDED.content,
    update_time = EXCLUDED.update_time;

INSERT INTO prompt_template_version (
    id, code, version, name, role, prompt_group, content, status,
    change_note, created_by, create_time, publish_time
)
SELECT id, code, 1, name, role, prompt_group, content, 'PUBLISHED',
       'V29 CET wrapup prompt', 'system', create_time, create_time
FROM prompt_template
WHERE code IN ('cet.tutor.wrapup.system', 'cet.tutor.wrapup.user')
ON CONFLICT (code, version) DO UPDATE SET
    name = EXCLUDED.name,
    role = EXCLUDED.role,
    prompt_group = EXCLUDED.prompt_group,
    content = EXCLUDED.content,
    change_note = EXCLUDED.change_note,
    publish_time = EXCLUDED.publish_time;

UPDATE prompt_template SET published_version = 1, update_time = TIMESTAMPTZ '2026-09-14 08:00:00+00'
WHERE code IN ('cet.tutor.wrapup.system', 'cet.tutor.wrapup.user');
