-- =============================================================================
-- Kidora AI：一键建表（UTF-8），按序 \ir 引入分文件
-- 对齐 docs/database-design.md
--
-- 【互斥】推荐开发路径：bootstrap 建库/表空间后，由 kidora-agent-server Flyway 建表。
-- 本文件仅作无 Java/Flyway 时的备用；勿对同一空库既执行本文件又跑 Flyway。
--
-- 用法（仓库根目录，库已存在）：
--   "D:\java-sofeware\PostgreSQL\18\bin\psql.exe" ^
--     "postgresql://postgres:PASSWORD@127.0.0.1:5432/kidora_ai" -f schema/all.sql
-- =============================================================================

\ir 00_extensions.sql
\ir 01_app_user.sql
\ir 02_learner_profile.sql
\ir 03_llm_config.sql
\ir 04_prompt_template.sql
\ir 05_prompt_template_version.sql
\ir 06_chat_session.sql
\ir 07_chat_message.sql
\ir 08_llm_call_log.sql
\ir 09_admin_user.sql
\ir 10_admin_audit_log.sql
\ir 11_cet_lesson_session.sql
\ir 12_cet_training_plan.sql
\ir 13_cet_tutor_turn.sql
\ir 14_cet_turn_assessment.sql
\ir 15_cet_session_report.sql
\ir 16_cet_safety_event.sql
\ir 17_mcp_server_ref.sql
\ir 18_mcp_tool_binding.sql
\ir 19_speech_vendor_config.sql
\ir 20_speech_route.sql
