#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
初始化测试数据库。

建库 + 从 article_trace.sql 导入表结构。**可重复执行**：每次都先清空测试库再重建，
所以不会残留上一次跑测试留下的数据。

表结构以仓库根目录的 article_trace.sql 为唯一来源，只有一份维护点。

用法：
    python scripts/init_test_db.py
    python scripts/init_test_db.py --host 127.0.0.1 --user root --password xxx

也可以用环境变量覆盖（便于 CI）：
    TEST_DB_HOST / TEST_DB_PORT / TEST_DB_USER / TEST_DB_PASSWORD / TEST_DB_NAME
"""

import argparse
import os
import re
import sys
from pathlib import Path

try:
    import pymysql
except ImportError:
    sys.exit("缺少依赖：pip install pymysql")

# 仓库根目录（本脚本位于 <root>/scripts/ 下）
ROOT = Path(__file__).resolve().parent.parent
SCHEMA_FILE = ROOT / "article_trace.sql"


def parse_args():
    p = argparse.ArgumentParser(description="初始化 article_trace 测试数据库")
    p.add_argument("--host", default=os.getenv("TEST_DB_HOST", "127.0.0.1"))
    p.add_argument("--port", type=int, default=int(os.getenv("TEST_DB_PORT", "3306")))
    p.add_argument("--user", default=os.getenv("TEST_DB_USER", "root"))
    p.add_argument("--password", default=os.getenv("TEST_DB_PASSWORD", "aaabbb123"))
    p.add_argument("--name", default=os.getenv("TEST_DB_NAME", "article_trace_test"))
    return p.parse_args()


def split_statements(sql: str):
    """
    按 mysqldump 的输出格式切分语句（以行尾分号结束）。

    刻意保留 /*! ... */ 条件注释——MySQL 会执行其中的内容，
    早先粗暴剥离导致 CREATE TABLE 语句损坏。
    """
    for part in re.split(r";\s*\n", sql):
        # 去掉纯注释行，保留正文
        lines = [ln for ln in part.split("\n") if not ln.strip().startswith("--")]
        stmt = "\n".join(lines).strip()
        if stmt:
            yield stmt


def main():
    args = parse_args()

    if not SCHEMA_FILE.exists():
        sys.exit(f"找不到表结构文件：{SCHEMA_FILE}")

    schema = SCHEMA_FILE.read_text(encoding="utf-8")

    # 1) 建库（若不存在）
    admin = pymysql.connect(host=args.host, port=args.port,
                            user=args.user, password=args.password, charset="utf8mb4")
    with admin.cursor() as cur:
        cur.execute(
            f"CREATE DATABASE IF NOT EXISTS `{args.name}` "
            "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
        )
    admin.commit()
    admin.close()
    print(f"[1/3] 数据库 `{args.name}` 已就绪")

    # 2) 清空旧表，保证可重复执行
    conn = pymysql.connect(host=args.host, port=args.port, user=args.user,
                           password=args.password, database=args.name, charset="utf8mb4")
    with conn.cursor() as cur:
        cur.execute("SET FOREIGN_KEY_CHECKS=0")
        cur.execute("SHOW TABLES")
        tables = [r[0] for r in cur.fetchall()]
        for t in tables:
            cur.execute(f"DROP TABLE IF EXISTS `{t}`")
        cur.execute("SET FOREIGN_KEY_CHECKS=1")
    conn.commit()
    if tables:
        print(f"[2/3] 已清空 {len(tables)} 张旧表")
    else:
        print("[2/3] 测试库原本为空")

    # 3) 导入表结构
    done, failed = 0, []
    with conn.cursor() as cur:
        cur.execute("SET FOREIGN_KEY_CHECKS=0")
        for stmt in split_statements(schema):
            upper = stmt.upper()
            if upper.startswith(("SET ", "CREATE DATABASE", "USE ", "LOCK TABLES", "UNLOCK TABLES")):
                continue
            try:
                cur.execute(stmt)
                done += 1
            except Exception as e:  # noqa: BLE001 — 逐条执行，收集全部失败再报告
                failed.append(f"{str(e)[:80]}")
        cur.execute("SET FOREIGN_KEY_CHECKS=1")
    conn.commit()

    with conn.cursor() as cur:
        cur.execute("SHOW TABLES")
        tables = sorted(r[0] for r in cur.fetchall())
    conn.close()

    print(f"[3/3] 导入 {done} 条语句，失败 {len(failed)} 条")
    for f in failed:
        print(f"      - {f}")

    if failed:
        sys.exit("建库未完全成功，请检查上面的失败语句")

    print(f"\n完成。测试库 `{args.name}` 现有 {len(tables)} 张表：")
    print("  " + ", ".join(tables))
    print("\n测试配置见 article_trace_back/src/test/resources/application.yml")
    print("（Redis 用独立 DB 14/15，MySQL 指向本库，邮件与后台任务均已关闭）")


if __name__ == "__main__":
    main()
