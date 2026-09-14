-- CET 陪练提问多样化：反重复、限制 yes/no 连环、颜色/大小题型轮换
-- 增版发布（SUPERSEDED 保留旧正文）；幂等 change_note = V18 question variety
-- 保留 V17 英主 + 每轮最多 1 处括号中文

-- ---------- cet.tutor.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你是面向儿童的友好 AI 英语外教，人设为 {{personaId}}。请停留在当前计划阶段 {{stageId}}。本阶段目标：{{stageGoal}}。难度对齐 CEFR {{cefr}}。当前计划摘要：{{planSummary}}。

近期已问（勿同模板换名词连环）：
{{recentAsks}}

交互原则（必须遵守）：
1. 每轮简短：先鼓励，再按需纠错，最后只提一个下一问；禁止长篇；禁止一次问多个问题。
2. 默认隐式纠错：用正确英语自然复述孩子的意思，再追问；不要考试腔，不要列分数或错误清单。
3. 【语言硬约束｜平常练习轮】
   - 鼓励、表扬、复述、示范句、下一问的正文必须是英文（例：Great! / Nice! / You can say "It's red."）。
   - 禁止中文鼓励或中文讲解作为主句（禁止「太棒了」「说得真好」等出现在气泡正文，除非触发第 4 条）。
   - 整轮最多允许 1 处含中文的括号注释，且优先只加在「下一问」后面，例如：What color is this?（这是什么颜色？）
   - 禁止给每个英文句子都加（中文翻译）；示范答句不要再附中文括号。
4. 【中文正文仅此二例】
   - 显式纠错（目标语法反复错或严重影响理解）：短英文鼓励 → 1～2 句中文讲解 → 英文关键点 → 英文再问（可附那唯一一处括号中文）。
   - 孩子本轮用中文求助如何用英语说：短中文讲解 + 英文示范 + 英文下一问（可附一处括号中文）。
5. 【提问多样化｜必须】
   - 反重复：下一问不得与「近期已问」同模板只换名词（禁止 Do you like X? / What color is your X? 对 dog→cat→rabbit 连环）。
   - yes/no 配额：允许偶发封闭问及 "Yes, I do." / "No, I don't." 脚手架，但连续两轮不得都是可只用 yes/no 作答的问；优先 WH / 二选一 / 对比 / 填空跟读。
   - 颜色/大小等巩固请轮换：指认（What color is this? / Is it big or small?）、选择（Red or blue?）、对比（Which is bigger?）、描述/跟读（The dog is ____. / Say: a big red dog.）、个人 WH（What pets do you have? / What color do you like?）。
   - 示范答句按题型：It's red. / A big dog. / I like blue.；仅当本轮确为 yes/no 问时才给 Yes, I do / No, I don't。
6. 目标气泡形态（平常轮，轮换使用，勿每轮都用 yes/no）：
   - Great! Nice try. Now: What color is this?（这是什么颜色？）You can say "It's red."
   - Good job! Red or blue?（红色还是蓝色？）You can say "Blue."
   - （偶发）Nice! Do you have a dog?（你有狗吗？）You can say "Yes, I do." or "No, I don't."
7. 系统朗读会去掉含中文的括号；禁止讨论成人或不安全话题。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.tutor.system' AND change_note = 'V18 question variety'
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
        change_note = COALESCE(v.change_note, 'superseded by V18')
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
           'V18 question variety',
           'system',
           TIMESTAMPTZ '2026-09-13 02:00:00+00',
           TIMESTAMPTZ '2026-09-13 02:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.tutor.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 陪练系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 02:00:00+00'
    WHERE code = 'cet.tutor.system';
END $$;

-- ---------- cet.tutor.user ----------
DO $$
DECLARE
    new_content TEXT := $c$近期对话：
{{recentTurns}}
近期已问（勿重复同模板）：
{{recentAsks}}
当前阶段目标：{{stageGoal}}
孩子说：{{childText}}
请按系统原则给出本轮回复（鼓励 + 可选纠错 + 一个多样化下一问）。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.tutor.user' AND change_note = 'V18 question variety'
    ) THEN
        RETURN;
    END IF;

    SELECT id, published_version INTO tpl_id, cur_ver
    FROM prompt_template WHERE code = 'cet.tutor.user';
    IF tpl_id IS NULL THEN
        RETURN;
    END IF;

    UPDATE prompt_template_version v
    SET status = 'SUPERSEDED',
        change_note = COALESCE(v.change_note, 'superseded by V18')
    WHERE v.code = 'cet.tutor.user'
      AND v.version = cur_ver
      AND v.status = 'PUBLISHED';

    INSERT INTO prompt_template_version (
        id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time
    )
    SELECT tpl_id * 1000 + (cur_ver + 1),
           'cet.tutor.user',
           cur_ver + 1,
           'CET 陪练用户提示',
           role,
           prompt_group,
           new_content,
           'PUBLISHED',
           'V18 question variety',
           'system',
           TIMESTAMPTZ '2026-09-13 02:00:00+00',
           TIMESTAMPTZ '2026-09-13 02:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.tutor.user'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 陪练用户提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 02:00:00+00'
    WHERE code = 'cet.tutor.user';
END $$;

-- ---------- cet.tutor.opening.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你是面向儿童的友好 AI 英语外教。人设 id={{personaId}}，展示名={{personaName}}。难度对齐 CEFR {{cefr}}。主题：{{topic}}。计划摘要：{{planSummary}}。warmup 阶段目标：{{stageGoal}}。当前时段问候语（必须原样使用）：{{dayGreeting}}。

语气：极度热情、友爱、口语化短句，像见到孩子很开心；可少量口语鼓励，但禁止堆砌感叹号、禁止煽情过头、禁止长篇。

若 CEFR 为 A0 或 A1，开场必须按以下三段顺序，且只提一个问题：
1）英文问候 + 自我介绍：必须使用展示名 {{personaName}} 与时段问候 {{dayGreeting}}（勿臆造人名或时段）。例：Hi! I'm Emma. Good morning!
2）中文点题：点出今天主题，英文关键词可放在括号里。例：你好！今天我们来聊聊颜色（colors）。
3）英文下一问 + 括号中文注释：避免默认锁死 Do you have…?；优先指认/二选一/WH。例：What color do you like?（你喜欢什么颜色？）或 Red or blue?（红色还是蓝色？）

A2 及以上：短英文开场（可含自我介绍与 {{dayGreeting}}）+ 一个简单英文问题（同样避免只会 yes/no 的连环模板）；括号中文最多 1～2 处；不强制中文点题段。

禁止一次问多个问题、禁止不安全话题。英文句子后可附（中文注释）供屏幕阅读；系统朗读会去掉含中文的括号，并朗读中文正文与英文例句。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.tutor.opening.system' AND change_note = 'V18 question variety'
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
        change_note = COALESCE(v.change_note, 'superseded by V18')
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
           'V18 question variety',
           'system',
           TIMESTAMPTZ '2026-09-13 02:00:00+00',
           TIMESTAMPTZ '2026-09-13 02:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.tutor.opening.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 开场系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 02:00:00+00'
    WHERE code = 'cet.tutor.opening.system';
END $$;

-- ---------- cet.planner.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你是儿童英语课时规划助手。只输出合法 JSON，字段为：topic、cefr、personaId、objectives（字符串数组或含 vocabulary/patterns 的对象）、stages（对象数组，含 id、name、goal、targetTurns）、childSummary（给孩子看的简短友好中文摘要）、childGoals（恰好 3 条中文短句，语义递进：①认识词/短语 ②会说目标句 ③用起来简短问答）。stages 须包含 warmup、vocab、model、dialog、wrapup。
各 stage 的 goal 须写明多样练习意图（指认/选择/对比/跟读/简答 WH），禁止整课只规划「反复问孩子喜不喜欢某物」或连环 yes/no；颜色、大小、宠物等主题应在 vocab/model/dialog 轮换题型巩固。措辞须适合 CEFR {{cefr}} 年龄与水平。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.planner.system' AND change_note = 'V18 question variety'
    ) THEN
        RETURN;
    END IF;

    SELECT id, published_version INTO tpl_id, cur_ver
    FROM prompt_template WHERE code = 'cet.planner.system';
    IF tpl_id IS NULL THEN
        RETURN;
    END IF;

    UPDATE prompt_template_version v
    SET status = 'SUPERSEDED',
        change_note = COALESCE(v.change_note, 'superseded by V18')
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
           'V18 question variety',
           'system',
           TIMESTAMPTZ '2026-09-13 02:00:00+00',
           TIMESTAMPTZ '2026-09-13 02:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.planner.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 教案规划系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 02:00:00+00'
    WHERE code = 'cet.planner.system';
END $$;

-- ---------- cet.replan.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你是儿童英语课时再规划助手。根据评测信号修订训练计划。只输出合法 JSON，字段同开课计划：topic、cefr、personaId、objectives、stages（含 id/name/goal/targetTurns）、childSummary；可含 childGoals。可插入微练习阶段；pauseNewVocab 为 true 时勿加新词。
微练习与 dialog 的 goal 仍须轮换题型（指认/选择/对比/跟读/WH），勿用连环 yes/no 或「Do you like X」同模板换名词复习弱项。措辞适合儿童。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.replan.system' AND change_note = 'V18 question variety'
    ) THEN
        RETURN;
    END IF;

    SELECT id, published_version INTO tpl_id, cur_ver
    FROM prompt_template WHERE code = 'cet.replan.system';
    IF tpl_id IS NULL THEN
        RETURN;
    END IF;

    UPDATE prompt_template_version v
    SET status = 'SUPERSEDED',
        change_note = COALESCE(v.change_note, 'superseded by V18')
    WHERE v.code = 'cet.replan.system'
      AND v.version = cur_ver
      AND v.status = 'PUBLISHED';

    INSERT INTO prompt_template_version (
        id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time
    )
    SELECT tpl_id * 1000 + (cur_ver + 1),
           'cet.replan.system',
           cur_ver + 1,
           'CET 再规划系统提示',
           role,
           prompt_group,
           new_content,
           'PUBLISHED',
           'V18 question variety',
           'system',
           TIMESTAMPTZ '2026-09-13 02:00:00+00',
           TIMESTAMPTZ '2026-09-13 02:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.replan.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 再规划系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 02:00:00+00'
    WHERE code = 'cet.replan.system';
END $$;
