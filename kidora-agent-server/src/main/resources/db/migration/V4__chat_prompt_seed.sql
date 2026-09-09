-- Chat prompt seed for generic assistant

INSERT INTO prompt_template (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
VALUES
(10020, 'chat.system', 'Chat System', 'SYSTEM', 'CHAT',
 'You are Kidora, a helpful family-friendly AI assistant. Be concise, clear, and kind. Do not discuss adult, violent, or self-harm topics.',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00'),
(10021, 'chat.user', 'Chat User', 'USER', 'CHAT',
 'Conversation so far:\n{{history}}\n\nUser message: {{text}}\nReply as Assistant.',
 1, 'ACTIVE', TIMESTAMPTZ '2026-09-09 00:00:00+00', TIMESTAMPTZ '2026-09-09 00:00:00+00')
ON CONFLICT (code) DO NOTHING;

INSERT INTO prompt_template_version (id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time)
SELECT id, code, 1, name, role, prompt_group, content, 'PUBLISHED', 'MVP Chat seed', 'system', create_time, create_time
FROM prompt_template
WHERE code LIKE 'chat.%'
ON CONFLICT (code, version) DO NOTHING;
