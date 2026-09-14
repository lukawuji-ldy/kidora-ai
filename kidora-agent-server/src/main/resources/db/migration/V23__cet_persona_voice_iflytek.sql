-- CET 人设 ↔ 讯飞 TTS 音色映射（vcn）

COMMENT ON COLUMN cet_persona_voice.voice_id IS '厂商音色 id（腾讯 VoiceType / 讯飞 vcn）';

INSERT INTO cet_persona_voice
(id, persona_id, vendor_code, voice_id, voice_name, voice_traits, catalog_url, locale, status, create_time, update_time)
VALUES
(40011, 'emma', 'iflytek', 'x4_xiaoyan', '讯飞小燕',
 '普通话女声；清晰温和，贴合温柔鼓励型外教',
 'https://console.xfyun.cn/services/tts', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-14 00:00:00+00', TIMESTAMPTZ '2026-09-14 00:00:00+00'),
(40012, 'mike', 'iflytek', 'aisjiuxu', '讯飞许久',
 '普通话男声；沉稳，贴合探险任务型（基础发音人唯一男声）',
 'https://console.xfyun.cn/services/tts', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-14 00:00:00+00', TIMESTAMPTZ '2026-09-14 00:00:00+00'),
(40013, 'lily', 'iflytek', 'x4_yezi', '讯飞小露',
 '普通话女声；柔和，贴合故事型',
 'https://console.xfyun.cn/services/tts', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-14 00:00:00+00', TIMESTAMPTZ '2026-09-14 00:00:00+00'),
(40014, 'tom', 'iflytek', 'aisjiuxu', '讯飞许久',
 '普通话男声；与 mike 暂共用 vcn（开通更多男声后可管理台改）',
 'https://console.xfyun.cn/services/tts', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-14 00:00:00+00', TIMESTAMPTZ '2026-09-14 00:00:00+00'),
(40015, 'coco', 'iflytek', 'aisbabyxu', '讯飞许小宝',
 '普通话童声；活泼亲切，贴合萌宠/低龄',
 'https://console.xfyun.cn/services/tts', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-14 00:00:00+00', TIMESTAMPTZ '2026-09-14 00:00:00+00'),
(40016, 'alex', 'iflytek', 'aisjinger', '讯飞小婧',
 '普通话女声；年轻感；无少年男声时作游戏型替代',
 'https://console.xfyun.cn/services/tts', 'en-US', 'ACTIVE',
 TIMESTAMPTZ '2026-09-14 00:00:00+00', TIMESTAMPTZ '2026-09-14 00:00:00+00')
ON CONFLICT (persona_id, vendor_code) DO NOTHING;
