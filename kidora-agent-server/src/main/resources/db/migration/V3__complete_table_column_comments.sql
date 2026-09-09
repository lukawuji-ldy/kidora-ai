-- 补齐全部业务表/字段 COMMENT（幂等：COMMENT ON 覆盖原注释）
-- Schema 权威仍在 schema/*.sql；本迁移用于已落地库。

-- app_user
COMMENT ON TABLE app_user IS '前台登录用户（家长/老师），与 admin_user 隔离';
COMMENT ON COLUMN app_user.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN app_user.user_id IS '业务用户键，写入 User JWT';
COMMENT ON COLUMN app_user.username IS '登录用户名';
COMMENT ON COLUMN app_user.password_hash IS '密码哈希（BCrypt 等）';
COMMENT ON COLUMN app_user.nickname IS '展示昵称';
COMMENT ON COLUMN app_user.role IS '角色：parent|teacher 等';
COMMENT ON COLUMN app_user.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN app_user.deleted IS '软删除标记';
COMMENT ON COLUMN app_user.create_time IS '创建时间';
COMMENT ON COLUMN app_user.update_time IS '更新时间';

-- learner_profile
COMMENT ON TABLE learner_profile IS '儿童学习者档案（归属家长/老师 user_id）';
COMMENT ON COLUMN learner_profile.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN learner_profile.learner_id IS '学习者业务键';
COMMENT ON COLUMN learner_profile.user_id IS '归属家长/老师 app_user.user_id';
COMMENT ON COLUMN learner_profile.display_name IS '儿童展示名';
COMMENT ON COLUMN learner_profile.age_band IS '年龄段，如 6-8';
COMMENT ON COLUMN learner_profile.cefr_level IS 'CEFR 如 A1';
COMMENT ON COLUMN learner_profile.preferred_persona IS '偏好 AI 外教人设 id';
COMMENT ON COLUMN learner_profile.extra_json IS '扩展画像 JSON（MVP-3 充实）';
COMMENT ON COLUMN learner_profile.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN learner_profile.deleted IS '软删除标记';
COMMENT ON COLUMN learner_profile.create_time IS '创建时间';
COMMENT ON COLUMN learner_profile.update_time IS '更新时间';

-- llm_config
COMMENT ON TABLE llm_config IS '大模型 OpenAI Compatible 连接配置（CHAT/EMBEDDING）';
COMMENT ON COLUMN llm_config.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN llm_config.config_id IS '配置业务键，如 llm_primary / llm_embedding';
COMMENT ON COLUMN llm_config.name IS '配置展示名称';
COMMENT ON COLUMN llm_config.provider IS '供应商标识，如 openai_compatible';
COMMENT ON COLUMN llm_config.model_kind IS 'CHAT | EMBEDDING';
COMMENT ON COLUMN llm_config.base_url IS 'OpenAI Compatible API Base URL';
COMMENT ON COLUMN llm_config.api_key_cipher IS 'API Key 密文（禁止明文日志）';
COMMENT ON COLUMN llm_config.model IS '模型名（入库配置，禁止业务代码硬编码）';
COMMENT ON COLUMN llm_config.temperature IS '采样温度';
COMMENT ON COLUMN llm_config.max_tokens IS '单次最大生成 token';
COMMENT ON COLUMN llm_config.extra_json IS '扩展参数 JSON（如 chat_completions_path）';
COMMENT ON COLUMN llm_config.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN llm_config.create_time IS '创建时间';
COMMENT ON COLUMN llm_config.update_time IS '更新时间';

-- prompt_template
COMMENT ON TABLE prompt_template IS '提示词线上副本（每 code 一行）';
COMMENT ON COLUMN prompt_template.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN prompt_template.code IS '模板编码，如 cet.tutor.system';
COMMENT ON COLUMN prompt_template.name IS '模板展示名称';
COMMENT ON COLUMN prompt_template.role IS 'SYSTEM|USER';
COMMENT ON COLUMN prompt_template.prompt_group IS '分组：CHAT|CET_PLANNER|CET_TUTOR|CET_EVAL|CET_SAFETY 等';
COMMENT ON COLUMN prompt_template.content IS '当前已发布正文，可含变量占位';
COMMENT ON COLUMN prompt_template.published_version IS '当前已发布版本号';
COMMENT ON COLUMN prompt_template.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN prompt_template.create_time IS '创建时间';
COMMENT ON COLUMN prompt_template.update_time IS '更新时间';

-- prompt_template_version
COMMENT ON TABLE prompt_template_version IS '提示词版本历史（DRAFT/PUBLISHED/SUPERSEDED）';
COMMENT ON COLUMN prompt_template_version.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN prompt_template_version.code IS '模板编码，关联 prompt_template.code';
COMMENT ON COLUMN prompt_template_version.version IS '版本号（同 code 内递增）';
COMMENT ON COLUMN prompt_template_version.name IS '该版本展示名称';
COMMENT ON COLUMN prompt_template_version.role IS 'SYSTEM|USER';
COMMENT ON COLUMN prompt_template_version.prompt_group IS '分组：CHAT|CET_* 等';
COMMENT ON COLUMN prompt_template_version.content IS '该版本正文';
COMMENT ON COLUMN prompt_template_version.status IS 'DRAFT|PUBLISHED|SUPERSEDED';
COMMENT ON COLUMN prompt_template_version.change_note IS '变更说明';
COMMENT ON COLUMN prompt_template_version.created_by IS '操作者 admin_id；种子为 system';
COMMENT ON COLUMN prompt_template_version.create_time IS '创建时间';
COMMENT ON COLUMN prompt_template_version.publish_time IS '发布时间；草稿可空';

-- chat_session
COMMENT ON TABLE chat_session IS '通用聊天会话元数据';
COMMENT ON COLUMN chat_session.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN chat_session.session_id IS '对外业务键';
COMMENT ON COLUMN chat_session.user_id IS '所属用户 app_user.user_id';
COMMENT ON COLUMN chat_session.title IS '会话标题';
COMMENT ON COLUMN chat_session.summary IS '结构化摘要 JSON（滚动合并）';
COMMENT ON COLUMN chat_session.summary_until_time IS '摘要覆盖截止时间';
COMMENT ON COLUMN chat_session.summary_until_message_id IS '摘要覆盖截止消息业务键';
COMMENT ON COLUMN chat_session.message_count IS '消息条数';
COMMENT ON COLUMN chat_session.last_active_time IS '最后活跃时间';
COMMENT ON COLUMN chat_session.deleted IS '软删除标记';
COMMENT ON COLUMN chat_session.create_time IS '创建时间';

-- chat_message
COMMENT ON TABLE chat_message IS '通用会话消息（短期记忆）';
COMMENT ON COLUMN chat_message.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN chat_message.message_id IS '消息业务键';
COMMENT ON COLUMN chat_message.session_id IS '所属 chat_session.session_id';
COMMENT ON COLUMN chat_message.user_id IS '发送方用户 app_user.user_id';
COMMENT ON COLUMN chat_message.role IS 'user|assistant|system|tool';
COMMENT ON COLUMN chat_message.content IS '消息正文';
COMMENT ON COLUMN chat_message.token_count IS '本条估算 token 数';
COMMENT ON COLUMN chat_message.status IS 'STREAMING|COMPLETED|CANCELLED';
COMMENT ON COLUMN chat_message.create_time IS '创建时间';

-- llm_call_log
COMMENT ON TABLE llm_call_log IS '每次 LLM 调用的完整入模参数审计';
COMMENT ON COLUMN llm_call_log.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN llm_call_log.call_id IS '调用业务键';
COMMENT ON COLUMN llm_call_log.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN llm_call_log.session_id IS '关联会话键（chat_session 或 cet lesson）';
COMMENT ON COLUMN llm_call_log.message_id IS '关联消息业务键，可空';
COMMENT ON COLUMN llm_call_log.user_id IS '调用用户 app_user.user_id';
COMMENT ON COLUMN llm_call_log.learner_id IS '学习者键（CET 场景）；通用 Chat 可空';
COMMENT ON COLUMN llm_call_log.biz_source IS '业务来源：CHAT|CET';
COMMENT ON COLUMN llm_call_log.biz_ref_id IS '业务引用键：CET=lesson_session_id 等';
COMMENT ON COLUMN llm_call_log.model_id IS '实际调用模型名';
COMMENT ON COLUMN llm_call_log.provider IS '供应商标识';
COMMENT ON COLUMN llm_call_log.attempt IS '同配置重试序号，从 1 计';
COMMENT ON COLUMN llm_call_log.is_fallback IS '是否走备用模型';
COMMENT ON COLUMN llm_call_log.status IS 'SUCCESS/FAILED 等';
COMMENT ON COLUMN llm_call_log.error_code IS '失败错误码，成功可空';
COMMENT ON COLUMN llm_call_log.latency_ms IS '端到端耗时毫秒';
COMMENT ON COLUMN llm_call_log.prompt_tokens IS '入模 prompt token';
COMMENT ON COLUMN llm_call_log.completion_tokens IS '生成 completion token';
COMMENT ON COLUMN llm_call_log.request_json IS '完整请求参数 JSON';
COMMENT ON COLUMN llm_call_log.response_json IS '响应摘要/全文 JSON，失败可含错误信息';
COMMENT ON COLUMN llm_call_log.create_time IS '创建时间';

-- admin_user
COMMENT ON TABLE admin_user IS '后台运营账号，与 app_user 隔离';
COMMENT ON COLUMN admin_user.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN admin_user.admin_id IS '业务键，写入 Admin JWT';
COMMENT ON COLUMN admin_user.username IS '登录用户名';
COMMENT ON COLUMN admin_user.password_hash IS '密码哈希（BCrypt 等）';
COMMENT ON COLUMN admin_user.display_name IS '展示名';
COMMENT ON COLUMN admin_user.role IS 'SUPER_ADMIN | OPERATOR';
COMMENT ON COLUMN admin_user.status IS 'ACTIVE | DISABLED';
COMMENT ON COLUMN admin_user.is_builtin IS '内置管理员：不可删/改角色/禁用，仅可改密';
COMMENT ON COLUMN admin_user.create_time IS '创建时间';
COMMENT ON COLUMN admin_user.update_time IS '更新时间';

-- admin_audit_log
COMMENT ON TABLE admin_audit_log IS '管理台写操作审计；detail 记录字段级 from→to 变更';
COMMENT ON COLUMN admin_audit_log.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN admin_audit_log.admin_id IS '操作者 admin_user.admin_id';
COMMENT ON COLUMN admin_audit_log.action IS '动作，如 CREATE/UPDATE/DISABLE';
COMMENT ON COLUMN admin_audit_log.resource_type IS '资源类型，如 llm_config/prompt_template';
COMMENT ON COLUMN admin_audit_log.resource_id IS '资源业务键，可空';
COMMENT ON COLUMN admin_audit_log.detail IS 'JSONB 契约: {"changes":[{"field","from","to"}],"meta":{可选}}. apiKey/password 仅记 to=[CHANGED]；禁止明文密钥/密码。';
COMMENT ON COLUMN admin_audit_log.create_time IS '创建时间';

-- cet_lesson_session
COMMENT ON TABLE cet_lesson_session IS 'CET 一次陪练会话';
COMMENT ON COLUMN cet_lesson_session.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_lesson_session.lesson_session_id IS '对外业务键';
COMMENT ON COLUMN cet_lesson_session.user_id IS '开课家长/老师 app_user.user_id';
COMMENT ON COLUMN cet_lesson_session.learner_id IS '学习者 learner_profile.learner_id';
COMMENT ON COLUMN cet_lesson_session.topic IS '陪练主题';
COMMENT ON COLUMN cet_lesson_session.persona_id IS 'AI 外教人设 id';
COMMENT ON COLUMN cet_lesson_session.cefr_level IS '本课 CEFR 级别';
COMMENT ON COLUMN cet_lesson_session.status IS 'CREATED|PLANNING|PRACTICING|EVALUATING|REPLANNING|COMPLETED|ABORTED|SAFETY_BLOCKED';
COMMENT ON COLUMN cet_lesson_session.active_plan_id IS '当前生效 cet_training_plan.plan_id';
COMMENT ON COLUMN cet_lesson_session.extra_json IS '扩展元数据 JSON';
COMMENT ON COLUMN cet_lesson_session.deleted IS '软删除标记';
COMMENT ON COLUMN cet_lesson_session.start_time IS '开课时间';
COMMENT ON COLUMN cet_lesson_session.end_time IS '结课/终止时间';
COMMENT ON COLUMN cet_lesson_session.create_time IS '创建时间';
COMMENT ON COLUMN cet_lesson_session.update_time IS '更新时间';

-- cet_training_plan
COMMENT ON TABLE cet_training_plan IS 'CET 当前生效训练计划（大循环 Planner 产出）';
COMMENT ON COLUMN cet_training_plan.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_training_plan.plan_id IS '计划业务键';
COMMENT ON COLUMN cet_training_plan.lesson_session_id IS '所属 cet_lesson_session.lesson_session_id';
COMMENT ON COLUMN cet_training_plan.learner_id IS '学习者键';
COMMENT ON COLUMN cet_training_plan.version IS '同会话内版本号；Re-plan 递增（修订历史表 MVP-3）';
COMMENT ON COLUMN cet_training_plan.plan_json IS '计划 JSON：topic/cefr/persona/objectives/stages';
COMMENT ON COLUMN cet_training_plan.status IS 'ACTIVE|SUPERSEDED';
COMMENT ON COLUMN cet_training_plan.create_time IS '创建时间';
COMMENT ON COLUMN cet_training_plan.update_time IS '更新时间';

-- cet_tutor_turn
COMMENT ON TABLE cet_tutor_turn IS 'CET 每一轮外教/儿童输入输出';
COMMENT ON COLUMN cet_tutor_turn.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_tutor_turn.turn_id IS '轮次业务键';
COMMENT ON COLUMN cet_tutor_turn.lesson_session_id IS '所属课时会话键';
COMMENT ON COLUMN cet_tutor_turn.learner_id IS '学习者键';
COMMENT ON COLUMN cet_tutor_turn.turn_index IS '会话内从 1 递增的轮次序号';
COMMENT ON COLUMN cet_tutor_turn.stage_id IS '计划 stages[].id';
COMMENT ON COLUMN cet_tutor_turn.tutor_text IS '外教回复文本';
COMMENT ON COLUMN cet_tutor_turn.child_text IS '儿童输入文本（MVP-1）';
COMMENT ON COLUMN cet_tutor_turn.child_asr_json IS 'ASR 结果（MVP-2）；文本陪练可空';
COMMENT ON COLUMN cet_tutor_turn.signals_json IS '中间信号（卡顿、求助等）';
COMMENT ON COLUMN cet_tutor_turn.create_time IS '创建时间';

-- cet_turn_assessment
COMMENT ON TABLE cet_turn_assessment IS 'CET 评测结构化结果';
COMMENT ON COLUMN cet_turn_assessment.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_turn_assessment.assessment_id IS '评测业务键';
COMMENT ON COLUMN cet_turn_assessment.lesson_session_id IS '所属课时会话键';
COMMENT ON COLUMN cet_turn_assessment.turn_id IS '轮次评测时关联 cet_tutor_turn.turn_id；会话摘要可空';
COMMENT ON COLUMN cet_turn_assessment.scope IS 'SESSION|TURN|STAGE';
COMMENT ON COLUMN cet_turn_assessment.assessment_json IS 'grammar/vocabulary/pronunciation/fluency 等评分 JSON';
COMMENT ON COLUMN cet_turn_assessment.create_time IS '创建时间';

-- cet_session_report
COMMENT ON TABLE cet_session_report IS 'CET 结课报告快照';
COMMENT ON COLUMN cet_session_report.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_session_report.report_id IS '报告业务键';
COMMENT ON COLUMN cet_session_report.lesson_session_id IS '所属课时会话键（一对一）';
COMMENT ON COLUMN cet_session_report.learner_id IS '学习者键';
COMMENT ON COLUMN cet_session_report.child_summary IS '儿童可读短评';
COMMENT ON COLUMN cet_session_report.parent_summary IS '家长可读摘要（MVP-4 充实）';
COMMENT ON COLUMN cet_session_report.report_json IS '完整报告结构化 JSON';
COMMENT ON COLUMN cet_session_report.create_time IS '创建时间';

-- cet_safety_event
COMMENT ON TABLE cet_safety_event IS '儿童 Safety 闸门命中事件';
COMMENT ON COLUMN cet_safety_event.id IS '主键（雪花 BIGINT）';
COMMENT ON COLUMN cet_safety_event.event_id IS '事件业务键';
COMMENT ON COLUMN cet_safety_event.lesson_session_id IS '关联课时会话键，可空';
COMMENT ON COLUMN cet_safety_event.turn_id IS '关联轮次键，可空';
COMMENT ON COLUMN cet_safety_event.learner_id IS '学习者键，可空';
COMMENT ON COLUMN cet_safety_event.user_id IS '触发用户键，可空';
COMMENT ON COLUMN cet_safety_event.direction IS 'INPUT|OUTPUT';
COMMENT ON COLUMN cet_safety_event.event_type IS '命中类型（敏感词/越界话题等）';
COMMENT ON COLUMN cet_safety_event.action IS 'BLOCK|REWRITE|ESCALATE|LOG';
COMMENT ON COLUMN cet_safety_event.detail_json IS '命中详情（避免完整敏感原文入应用日志）';
COMMENT ON COLUMN cet_safety_event.create_time IS '创建时间';

