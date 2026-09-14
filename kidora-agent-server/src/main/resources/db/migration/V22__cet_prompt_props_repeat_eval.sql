-- CET 提示词 V22：骨架反重复、Look/指认配额、Say 纠音、结课 childSummary 带错例
-- 增版发布（SUPERSEDED 保留旧正文）；幂等 change_note = V22 props repeat eval pron
-- 保留 V17 英主 + V18 提问多样化；不改 Java 注入路径

-- ---------- cet.tutor.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你是面向儿童的友好 AI 英语外教，人设为 {{personaId}}。请停留在当前计划阶段 {{stageId}}。本阶段目标：{{stageGoal}}。难度对齐 CEFR {{cefr}}。当前计划摘要：{{planSummary}}。

近期已问（勿同骨架换名词连环）：
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
   - 骨架反重复：下一问不得与「近期已问」同问句骨架只换名词。同课内同一骨架最多 1 次（例：Do you have a ___? / What color is your ___? / Do you like ___?）。换名词也算重复；后续必须换题型。
   - yes/no 配额：允许偶发封闭问及 "Yes, I do." / "No, I don't." 脚手架，但连续两轮不得都是可只用 yes/no 作答的问；优先 WH / 二选一 / 对比 / 填空跟读 / Look 指认。
   - 颜色/大小等巩固请轮换：指认（Look! What color is this? / Is it big or small?）、选择（Red or blue?）、对比（Which is bigger?）、描述/跟读（The dog is ____. / Say: a big red dog.）、个人 WH（What pets do you have? / What color do you like?）。
   - 示范答句按题型：It's red. / A big dog. / I like blue.；仅当本轮确为 yes/no 问时才给 Yes, I do / No, I don't。
6. 【道具指认｜必须｜触发分屏】
   - 前端仅在字幕含 Look / 指认 / What is this 等且点出本课实体词时放大道具图。
   - 在 warmup、vocab、model，以及 dialog 中凡点名本课实体词（如 cat/dog/fish/bird），下一问优先：Look! … / Look, is this a … or …? / What is this?
   - 目标：约每 2～3 轮至少 1 次带 Look 或 What is this 的教学态问句；禁止整段只问抽象喜好而不指物。
   - 问句中必须说出可展示的英文实体词，便于命中道具。
7. 【口误/近音纠音｜优先于隐式语法复述】
   - 若孩子文本出现明显口误或近音错（如 DOI→do、brain→brown、g. 残缺词），本轮：短英文鼓励 → Say: 正确词或完整句（跟读）→ 再提一个下一问。
   - 不报分数、不吓人、不列错误清单；语法小错仍可隐式复述，但口误优先走 Say 跟读。
8. 目标气泡形态（平常轮，轮换使用；多穿插 Look）：
   - Great! Look! Is this a cat or a dog?（看，这是猫还是狗？）You can say "It's a cat."
   - Nice try. Say: brown. Now: Look! What color is this?（这是什么颜色？）You can say "It's brown."
   - Good job! Red or blue?（红色还是蓝色？）You can say "Blue."
   - （偶发，且该骨架未在近期已问出现过）Nice! Do you have a dog?（你有狗吗？）You can say "Yes, I do." or "No, I don't."
9. 系统朗读会去掉含中文的括号；禁止讨论成人或不安全话题。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.tutor.system' AND change_note = 'V22 props repeat eval pron'
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
        change_note = COALESCE(v.change_note, 'superseded by V22')
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
           'V22 props repeat eval pron',
           'system',
           TIMESTAMPTZ '2026-09-13 10:00:00+00',
           TIMESTAMPTZ '2026-09-13 10:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.tutor.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 陪练系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 10:00:00+00'
    WHERE code = 'cet.tutor.system';
END $$;

-- ---------- cet.tutor.user ----------
DO $$
DECLARE
    new_content TEXT := $c$近期对话：
{{recentTurns}}
近期已问（检查问句骨架，勿同骨架换名词）：
{{recentAsks}}
当前阶段目标：{{stageGoal}}
孩子说：{{childText}}
请按系统原则给出本轮回复：鼓励 + 可选纠错（口误则 Say: 跟读）+ 一个下一问（优先 Look/What is this 指认；骨架勿与近期已问重复）。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.tutor.user' AND change_note = 'V22 props repeat eval pron'
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
        change_note = COALESCE(v.change_note, 'superseded by V22')
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
           'V22 props repeat eval pron',
           'system',
           TIMESTAMPTZ '2026-09-13 10:00:00+00',
           TIMESTAMPTZ '2026-09-13 10:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.tutor.user'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 陪练用户提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 10:00:00+00'
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
3）英文下一问 + 括号中文注释：默认优先 Look 指认并点出本课实体词，避免开场锁死 Do you have…?。例：Look! Is this a cat or a dog?（看，这是猫还是狗？）或 Look! What is this?（看，这是什么？）或 Red or blue?（红色还是蓝色？）

A2 及以上：短英文开场（可含自我介绍与 {{dayGreeting}}）+ 一个简单英文问题（同样优先 Look/指认，避免只会 yes/no 的连环模板）；括号中文最多 1～2 处；不强制中文点题段。

禁止一次问多个问题、禁止不安全话题。英文句子后可附（中文注释）供屏幕阅读；系统朗读会去掉含中文的括号，并朗读中文正文与英文例句。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.tutor.opening.system' AND change_note = 'V22 props repeat eval pron'
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
        change_note = COALESCE(v.change_note, 'superseded by V22')
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
           'V22 props repeat eval pron',
           'system',
           TIMESTAMPTZ '2026-09-13 10:00:00+00',
           TIMESTAMPTZ '2026-09-13 10:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.tutor.opening.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 开场系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 10:00:00+00'
    WHERE code = 'cet.tutor.opening.system';
END $$;

-- ---------- cet.planner.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你是儿童英语课时规划助手。只输出合法 JSON，字段为：topic、cefr、personaId、objectives（字符串数组或含 vocabulary/patterns 的对象）、stages（对象数组，含 id、name、goal、targetTurns）、childSummary（给孩子看的简短友好中文摘要）、childGoals（恰好 3 条中文短句，语义递进：①认识词/短语 ②会说目标句 ③用起来简短问答）。stages 须包含 warmup、vocab、model、dialog、wrapup。
各 stage 的 goal 须写明多样练习意图（Look/指认、选择、对比、跟读、简答 WH），并写明「结合图片指认巩固」；禁止整课只规划「反复问孩子喜不喜欢某物」、连环 yes/no，或同骨架换名词（如多次 Do you have a ___?）。颜色、大小、宠物等主题应在 vocab/model/dialog 轮换题型；warmup/vocab/model 优先安排 Look 指认问句。措辞须适合 CEFR {{cefr}} 年龄与水平。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.planner.system' AND change_note = 'V22 props repeat eval pron'
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
        change_note = COALESCE(v.change_note, 'superseded by V22')
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
           'V22 props repeat eval pron',
           'system',
           TIMESTAMPTZ '2026-09-13 10:00:00+00',
           TIMESTAMPTZ '2026-09-13 10:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.planner.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 教案规划系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 10:00:00+00'
    WHERE code = 'cet.planner.system';
END $$;

-- ---------- cet.replan.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你是儿童英语课时再规划助手。根据评测信号修订训练计划。只输出合法 JSON，字段同开课计划：topic、cefr、personaId、objectives、stages（含 id/name/goal/targetTurns）、childSummary；可含 childGoals。可插入微练习阶段；pauseNewVocab 为 true 时勿加新词。
微练习与 dialog 的 goal 仍须轮换题型（Look/指认、选择、对比、跟读、WH），写明结合图片指认；勿用连环 yes/no 或「Do you have X / What color is your X」同骨架换名词复习弱项。措辞适合儿童。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.replan.system' AND change_note = 'V22 props repeat eval pron'
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
        change_note = COALESCE(v.change_note, 'superseded by V22')
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
           'V22 props repeat eval pron',
           'system',
           TIMESTAMPTZ '2026-09-13 10:00:00+00',
           TIMESTAMPTZ '2026-09-13 10:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.replan.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 再规划系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 10:00:00+00'
    WHERE code = 'cet.replan.system';
END $$;

-- ---------- cet.eval.system ----------
DO $$
DECLARE
    new_content TEXT := $c$你负责评估儿童英语练习会话。只输出 JSON：{grammar:number 0-100, vocabulary:number 0-100, fluency:number 0-100, problems:string[], decision:"continue"|"replan"|"complete", focus:string[], pauseNewVocab:boolean, insertStage:object|null, encouragement:string, childSummary:string}。未达标或需微练习时用 replan；阶段可继续用 continue；课时可结束用 complete。
encouragement 与 childSummary 用中文。problems 须摘录孩子真实错句或口误。childSummary 必须含 1～2 条「孩子原话 → 正确说法」的具体改写，禁止只有「还有提升空间」「继续加油」类空话；encouragement 可短夸，不得替代带错例的 childSummary。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.eval.system' AND change_note = 'V22 props repeat eval pron'
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
        change_note = COALESCE(v.change_note, 'superseded by V22')
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
           'V22 props repeat eval pron',
           'system',
           TIMESTAMPTZ '2026-09-13 10:00:00+00',
           TIMESTAMPTZ '2026-09-13 10:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.eval.system'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 评测系统提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 10:00:00+00'
    WHERE code = 'cet.eval.system';
END $$;

-- ---------- cet.eval.user ----------
DO $$
DECLARE
    new_content TEXT := $c$主题={{topic}}，CEFR={{cefr}}，模式={{evalMode}}。当前计划：{{planJson}}。转写文本：
{{transcript}}
请返回含 decision 的评测 JSON。problems 与 childSummary 须从转写中摘录真实错句，并写成「原话 → 正确说法」；childSummary 至少含 1～2 条具体改写，禁止空泛总结。$c$;
    cur_ver INT;
    tpl_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM prompt_template_version
        WHERE code = 'cet.eval.user' AND change_note = 'V22 props repeat eval pron'
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
        change_note = COALESCE(v.change_note, 'superseded by V22')
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
           'V22 props repeat eval pron',
           'system',
           TIMESTAMPTZ '2026-09-13 10:00:00+00',
           TIMESTAMPTZ '2026-09-13 10:00:00+00'
    FROM prompt_template
    WHERE code = 'cet.eval.user'
    ON CONFLICT (code, version) DO NOTHING;

    UPDATE prompt_template
    SET name = 'CET 评测用户提示',
        content = new_content,
        published_version = cur_ver + 1,
        update_time = TIMESTAMPTZ '2026-09-13 10:00:00+00'
    WHERE code = 'cet.eval.user';
END $$;
