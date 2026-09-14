-- CET 一轮陪练分段耗时（毫秒）与 skip/client 上报
ALTER TABLE cet_tutor_turn
    ADD COLUMN IF NOT EXISTS timing_json JSONB;

COMMENT ON COLUMN cet_tutor_turn.timing_json IS
    '一轮陪练分段耗时（毫秒）与 skip/client 上报；不含音频与对话原文';
