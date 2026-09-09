-- MVP-1 seed: demo parent/learner, CET prompts, llm_config placeholder
-- password_hash 由 DevSeedRunner 启动时刷新为 parent123

INSERT INTO app_user (id, user_id, username, password_hash, nickname, role, status, deleted, create_time, update_time)
VALUES (10001, 'u_demo_parent', 'parent1', 'PLACEHOLDER', 'Demo Parent', 'parent', 'ACTIVE', FALSE,
        TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO learner_profile (id, learner_id, user_id, display_name, age_band, cefr_level, preferred_persona,
                             extra_json, status, deleted, create_time, update_time)
VALUES (10002, 'lrn_demo_amy', 'u_demo_parent', 'Amy', '6-8', 'A1', 'emma',
        '{}'::jsonb, 'ACTIVE', FALSE,
        TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00')
ON CONFLICT (learner_id) DO NOTHING;

INSERT INTO llm_config (id, config_id, name, provider, model_kind, base_url, api_key_cipher, model,
                        temperature, max_tokens, extra_json, status, create_time, update_time)
VALUES (10003, 'llm_primary', 'Primary Chat', 'openai_compatible', 'CHAT',
        'https://api.openai.com', 'CHANGE_ME', 'gpt-4o-mini',
        0.70, 2048, '{}'::jsonb, 'ACTIVE',
        TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00')
ON CONFLICT (config_id) DO NOTHING;

-- CET prompts
INSERT INTO prompt_template (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
VALUES
(10010, 'cet.planner.system', 'CET Planner System', 'SYSTEM', 'CET_PLANNER',
 'You are a child English lesson planner. Output ONLY valid JSON with keys: topic, cefr, personaId, objectives (array of strings), stages (array of {id,name,goal,targetTurns}), childSummary (short friendly Chinese summary for the child). Stages should include warmup, vocab, model, dialog, wrapup. Keep language age-appropriate for CEFR {{cefr}}.',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(10011, 'cet.planner.user', 'CET Planner User', 'USER', 'CET_PLANNER',
 'Learner: {{displayName}}, ageBand={{ageBand}}, CEFR={{cefr}}, persona={{personaId}}. Topic: {{topic}}. Create a short training plan JSON.',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(10012, 'cet.tutor.system', 'CET Tutor System', 'SYSTEM', 'CET_TUTOR',
 'You are a friendly AI English tutor for children named {{personaId}}. Stay on the current stage {{stageId}} of the plan. Be encouraging, short sentences, CEFR {{cefr}}. Current plan summary: {{planSummary}}. Reply in simple English with optional brief Chinese hint in parentheses when helpful. Do not discuss adult or unsafe topics.',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(10013, 'cet.tutor.user', 'CET Tutor User', 'USER', 'CET_TUTOR',
 'Recent turns:\n{{recentTurns}}\nChild says: {{childText}}\nRespond with feedback and the next prompt question.',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(10014, 'cet.eval.system', 'CET Eval System', 'SYSTEM', 'CET_EVAL',
 'You evaluate a child English practice session. Output ONLY JSON: {grammar:number 0-100, vocabulary:number 0-100, fluency:number 0-100, encouragement:string, childSummary:string}. Be kind and specific.',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(10015, 'cet.eval.user', 'CET Eval User', 'USER', 'CET_EVAL',
 'Topic={{topic}}, CEFR={{cefr}}. Transcript:\n{{transcript}}\nReturn session assessment JSON.',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(10016, 'cet.safety.system', 'CET Safety System', 'SYSTEM', 'CET_SAFETY',
 'You are a child-safety classifier for an English tutoring product. Output ONLY JSON: {action:ALLOW|REWRITE|SOFT_BLOCK|HARD_BLOCK, reason:string, rewrite:string|null}. HARD_BLOCK for adult/violence/self-harm/illegal. SOFT_BLOCK for politics/religion solicitations. REWRITE mild issues. ALLOW safe content.',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(10017, 'cet.safety.user', 'CET Safety User', 'USER', 'CET_SAFETY',
 'Direction={{direction}}. Text: {{text}}',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00')
ON CONFLICT (code) DO NOTHING;

INSERT INTO prompt_template_version (id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time)
SELECT id, code, 1, name, role, prompt_group, content, 'PUBLISHED', 'MVP-1 seed', 'system', create_time, create_time
FROM prompt_template
WHERE code LIKE 'cet.%'
ON CONFLICT (code, version) DO NOTHING;
