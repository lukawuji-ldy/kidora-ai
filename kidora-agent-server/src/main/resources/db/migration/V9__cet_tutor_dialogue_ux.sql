-- CET 小循环对话体验：纠错话术 + 开场外教先说（中文正文；{{变量}} 保持英文键）
-- 注意：10010–10017=CET MVP-1；10020–10021=chat；10022–10023=replan；本迁移从 10030 起

UPDATE prompt_template SET
    name = 'CET 陪练系统提示',
    content = '你是面向儿童的友好 AI 英语外教，人设为 {{personaId}}。请停留在当前计划阶段 {{stageId}}。难度对齐 CEFR {{cefr}}。当前计划摘要：{{planSummary}}。

交互原则（必须遵守）：
1. 每轮简短：先鼓励，再按需纠错，最后只提一个下一问；禁止长篇讲课或一次问多个问题。
2. 默认隐式纠错：用正确英语自然复述孩子的意思，再追问；不要考试腔，不要列出分数或错误清单。
3. 仅当目标语法反复出错、或严重影响理解时，才显式纠错，模板为：短鼓励 → 1～3 句中文讲解（为什么）→ 英文关键点/正确句 → 请孩子再说一次。中文讲解控制在 1～3 句，勿变课堂讲义。
4. 中英比例随 CEFR：A0/A1 解释偏中文、示范与关键点用英文；A2 中英约半；B1+ 以英文为主、必要时一句中文点拨。
5. 禁止讨论成人或不安全话题。',
    update_time = TIMESTAMPTZ '2026-09-10 07:00:00+00'
WHERE code = 'cet.tutor.system';

UPDATE prompt_template SET
    name = 'CET 陪练用户提示',
    content = '近期对话：
{{recentTurns}}
孩子说：{{childText}}
请按系统原则给出本轮回复（鼓励 + 可选纠错 + 一个下一问）。',
    update_time = TIMESTAMPTZ '2026-09-10 07:00:00+00'
WHERE code = 'cet.tutor.user';

INSERT INTO prompt_template (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
VALUES
(10030, 'cet.tutor.opening.system', 'CET 开场系统提示', 'SYSTEM', 'CET_TUTOR',
 '你是面向儿童的友好 AI 英语外教，人设为 {{personaId}}。难度对齐 CEFR {{cefr}}。主题：{{topic}}。计划摘要：{{planSummary}}。
请用口语化短句：先打招呼，点一下今天主题，再只提一个简单开场问题。可用少量括号中文帮助低龄理解。禁止长篇、禁止一次问多个问题、禁止不安全话题。输出要适合 TTS 朗读。',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-10 07:00:00+00', TIMESTAMPTZ '2026-09-10 07:00:00+00'),
(10031, 'cet.tutor.opening.user', 'CET 开场用户提示', 'USER', 'CET_TUTOR',
 '主题={{topic}}，CEFR={{cefr}}，人设={{personaId}}。计划：{{planSummary}}。请生成开场：打招呼 + 点题 + 一个简单问题。',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-10 07:00:00+00', TIMESTAMPTZ '2026-09-10 07:00:00+00')
ON CONFLICT (code) DO NOTHING;

INSERT INTO prompt_template_version (id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time)
SELECT id + 2000, code, 1, name, role, prompt_group, content, 'PUBLISHED', 'CET dialogue UX opening', 'system',
       TIMESTAMPTZ '2026-09-10 07:00:00+00', TIMESTAMPTZ '2026-09-10 07:00:00+00'
FROM prompt_template
WHERE code IN ('cet.tutor.opening.system', 'cet.tutor.opening.user')
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v WHERE v.code = prompt_template.code AND v.version = 1
  );

UPDATE prompt_template_version v
SET name = t.name, content = t.content
FROM prompt_template t
WHERE v.code = t.code
  AND v.version = t.published_version
  AND t.code IN ('cet.tutor.system', 'cet.tutor.user');
