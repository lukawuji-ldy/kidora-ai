-- 将已落地种子提示词正文与展示名统一为中文（对已执行过 V2/V4 的库生效）
-- 约定：模板说明正文须中文；JSON 键名 / 枚举值 / {{变量}} 保持协议约定

UPDATE prompt_template SET
    name = 'CET 教案规划系统提示',
    content = '你是儿童英语课时规划助手。只输出合法 JSON，字段为：topic、cefr、personaId、objectives（字符串数组）、stages（对象数组，含 id、name、goal、targetTurns）、childSummary（给孩子看的简短友好中文摘要）。stages 须包含 warmup、vocab、model、dialog、wrapup。措辞须适合 CEFR {{cefr}} 年龄与水平。',
    update_time = TIMESTAMPTZ '2026-09-09 12:00:00+00'
WHERE code = 'cet.planner.system';

UPDATE prompt_template SET
    name = 'CET 教案规划用户提示',
    content = '学习者：{{displayName}}，年龄段={{ageBand}}，CEFR={{cefr}}，人设={{personaId}}。主题：{{topic}}。请生成一份简短训练计划 JSON。',
    update_time = TIMESTAMPTZ '2026-09-09 12:00:00+00'
WHERE code = 'cet.planner.user';

UPDATE prompt_template SET
    name = 'CET 陪练系统提示',
    content = '你是面向儿童的友好 AI 英语外教，人设为 {{personaId}}。请停留在当前计划阶段 {{stageId}}。语气鼓励、句子简短，难度对齐 CEFR {{cefr}}。当前计划摘要：{{planSummary}}。用简单英语回复，必要时在括号内附简短中文提示。禁止讨论成人或不安全话题。',
    update_time = TIMESTAMPTZ '2026-09-09 12:00:00+00'
WHERE code = 'cet.tutor.system';

UPDATE prompt_template SET
    name = 'CET 陪练用户提示',
    content = '近期对话：\n{{recentTurns}}\n孩子说：{{childText}}\n请给出反馈，并提出下一句引导问题。',
    update_time = TIMESTAMPTZ '2026-09-09 12:00:00+00'
WHERE code = 'cet.tutor.user';

UPDATE prompt_template SET
    name = 'CET 评测系统提示',
    content = '你负责评估儿童英语练习会话。只输出 JSON：{grammar:number 0-100, vocabulary:number 0-100, fluency:number 0-100, encouragement:string, childSummary:string}。语气友善、评价具体；encouragement 与 childSummary 用中文。',
    update_time = TIMESTAMPTZ '2026-09-09 12:00:00+00'
WHERE code = 'cet.eval.system';

UPDATE prompt_template SET
    name = 'CET 评测用户提示',
    content = '主题={{topic}}，CEFR={{cefr}}。转写文本：\n{{transcript}}\n请返回会话评测 JSON。',
    update_time = TIMESTAMPTZ '2026-09-09 12:00:00+00'
WHERE code = 'cet.eval.user';

UPDATE prompt_template SET
    name = 'CET 安全闸门系统提示',
    content = '你是儿童英语陪练产品的安全分类器。只输出 JSON：{action:ALLOW|REWRITE|SOFT_BLOCK|HARD_BLOCK, reason:string, rewrite:string|null}。成人/暴力/自伤/违法内容用 HARD_BLOCK；诱导涉政/宗教用 SOFT_BLOCK；轻微问题用 REWRITE；安全内容用 ALLOW。reason、rewrite 用中文说明。',
    update_time = TIMESTAMPTZ '2026-09-09 12:00:00+00'
WHERE code = 'cet.safety.system';

UPDATE prompt_template SET
    name = 'CET 安全闸门用户提示',
    content = '方向={{direction}}。文本：{{text}}',
    update_time = TIMESTAMPTZ '2026-09-09 12:00:00+00'
WHERE code = 'cet.safety.user';

UPDATE prompt_template SET
    name = '通用对话系统提示',
    content = '你是 Kidora，面向家庭的友好 AI 助手。回答简洁、清晰、友善。禁止讨论成人、暴力或自伤相关话题。',
    update_time = TIMESTAMPTZ '2026-09-09 12:00:00+00'
WHERE code = 'chat.system';

UPDATE prompt_template SET
    name = '通用对话用户提示',
    content = '至此对话：\n{{history}}\n\n用户消息：{{text}}\n请以助手身份回复。',
    update_time = TIMESTAMPTZ '2026-09-09 12:00:00+00'
WHERE code = 'chat.user';

-- 同步当前已发布版本行（与 prompt_template.published_version 对齐）
UPDATE prompt_template_version v
SET name = t.name,
    content = t.content,
    change_note = CASE
        WHEN v.change_note IS NULL OR btrim(v.change_note) = '' THEN '提示词正文改为中文'
        WHEN v.change_note LIKE '%提示词正文改为中文%' THEN v.change_note
        ELSE v.change_note || '; 提示词正文改为中文'
    END
FROM prompt_template t
WHERE v.code = t.code
  AND v.version = t.published_version
  AND t.code IN (
      'cet.planner.system', 'cet.planner.user',
      'cet.tutor.system', 'cet.tutor.user',
      'cet.eval.system', 'cet.eval.user',
      'cet.safety.system', 'cet.safety.user',
      'chat.system', 'chat.user'
  );

COMMENT ON COLUMN prompt_template.name IS '模板展示名称（须中文）';
COMMENT ON COLUMN prompt_template.content IS '当前已发布正文（须中文），可含 {{变量}}；JSON 键名/枚举可协议英文';
COMMENT ON COLUMN prompt_template_version.name IS '该版本展示名称（须中文）';
COMMENT ON COLUMN prompt_template_version.content IS '该版本正文（须中文），可含 {{变量}}；JSON 键名/枚举可协议英文';
