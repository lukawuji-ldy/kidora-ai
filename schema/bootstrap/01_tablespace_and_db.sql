-- =============================================================================
-- Kidora：一次性创建表空间 + 空库（连 postgres 库执行）
-- 前置：空目录 D:/java-sofeware/PostgreSQL/18/tablespaces/ts_kidora 已存在且可写
-- 用法：
--   psql "postgresql://postgres:PASSWORD@127.0.0.1:5432/postgres" -f schema/bootstrap/01_tablespace_and_db.sql
-- 本脚本不建业务表；表结构由 Flyway（或备用 schema/all.sql）创建。
-- =============================================================================

CREATE TABLESPACE ts_kidora
    LOCATION 'D:/java-sofeware/PostgreSQL/18/tablespaces/ts_kidora';

CREATE DATABASE kidora_ai
    WITH OWNER = postgres
         ENCODING = 'UTF8'
         TABLESPACE = ts_kidora
         TEMPLATE = template0;

ALTER DATABASE kidora_ai SET default_tablespace = ts_kidora;
