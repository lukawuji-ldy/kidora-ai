# 数据库 / 表空间一次性引导

本目录脚本**只**创建表空间与空库，**不**建业务表。

## 前置

- PostgreSQL 18（本机服务 `postgresql-x64-18`）
- 超管账号可连 `postgres` 库（开发默认：`postgres` / `1234567890`）
- 表空间目录须为空，且对服务账号可写（本机：`NT AUTHORITY\NetworkService`）

## 锁定名称

| 项 | 值 |
|---|---|
| 表空间 | `ts_kidora` |
| 目录 | `D:/java-sofeware/PostgreSQL/18/tablespaces/ts_kidora` |
| 数据库 | `kidora_ai` |
| JDBC | `jdbc:postgresql://127.0.0.1:5432/kidora_ai` |

## 步骤（Windows）

```powershell
New-Item -ItemType Directory -Force -Path "D:\java-sofeware\PostgreSQL\18\tablespaces\ts_kidora"

& "D:\java-sofeware\PostgreSQL\18\bin\psql.exe" `
  "postgresql://postgres:1234567890@127.0.0.1:5432/postgres" `
  -f schema/bootstrap/01_tablespace_and_db.sql
```

然后启动 `kidora-agent-server`，由 **Flyway** 创建表（推荐路径）。

`schema/all.sql` 仅作无 Java 时的备用；**勿**对同一空库既跑 `all.sql` 又跑 Flyway。
