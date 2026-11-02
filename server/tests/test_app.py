"""服务端账号接口与旧表迁移测试：python -m pytest server/tests"""
import hashlib
import importlib
import os
import sqlite3
import sys

import pytest
from fastapi.testclient import TestClient

SERVER_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LEGACY_SCHEMA = """
CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, phone TEXT UNIQUE NOT NULL, token TEXT,
    created_at INTEGER NOT NULL, last_login_at INTEGER NOT NULL, username TEXT, password_hash TEXT, password_salt TEXT);
CREATE TABLE ledgers (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, name TEXT NOT NULL,
    space TEXT NOT NULL, is_default INTEGER DEFAULT 1, created_at INTEGER NOT NULL);
CREATE TABLE otp_codes (phone TEXT PRIMARY KEY, code TEXT NOT NULL, expires_at INTEGER NOT NULL);
"""


def load_app(data_dir):
    os.environ["BOOKKEEPING_DATA"] = str(data_dir)
    sys.path.insert(0, SERVER_DIR)
    try:
        sys.modules.pop("app", None)
        return importlib.import_module("app")
    finally:
        sys.path.remove(SERVER_DIR)


@pytest.fixture
def legacy_dir(tmp_path):
    conn = sqlite3.connect(tmp_path / "bookkeeping.db")
    conn.executescript(LEGACY_SCHEMA)
    # 验证码登录遗留：只有手机号、无用户名与密码
    conn.execute("INSERT INTO users (phone, token, created_at, last_login_at) VALUES ('18000000001', 'oldtoken', 1, 1)")
    conn.execute("INSERT INTO ledgers (user_id, name, space, created_at) VALUES (1, '个人默认账本', 'abc', 1)")
    conn.commit()
    conn.close()
    return tmp_path


def test_legacy_migration_and_register_claims_phone_account(legacy_dir):
    mod = load_app(legacy_dir)
    client = TestClient(mod.app)

    with sqlite3.connect(legacy_dir / "bookkeeping.db") as conn:
        cols = [r[1] for r in conn.execute("PRAGMA table_info(users)")]
        assert "phone" not in cols and "token_hash" in cols
        assert conn.execute("SELECT username FROM users WHERE id = 1").fetchone()[0] == "18000000001"
        assert conn.execute("SELECT count(*) FROM sqlite_master WHERE name = 'otp_codes'").fetchone()[0] == 0
    assert any(f.startswith("bookkeeping-before-users-migration") for f in os.listdir(legacy_dir / "backups"))

    # 旧明文 token 迁移为哈希后仍有效
    me = client.get("/api/auth/me", headers={"Authorization": "Bearer oldtoken"})
    assert me.status_code == 200 and me.json()["user"]["id"] == 1

    # 无密码旧账号不能用密码登录，提示去注册设置密码
    r = client.post("/api/auth/login-password", json={"username": "18000000001", "password": "123456"})
    assert r.status_code == 400 and "设置密码" in r.json()["detail"]

    # 曾导致 500 的场景：用该手机号注册
    r = client.post("/api/auth/register", json={"username": "18000000001", "password": "123456"})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["user"]["id"] == 1 and body["ledger"]["space"] == "abc"

    r = client.post("/api/auth/login-password", json={"username": "18000000001", "password": "123456"})
    assert r.status_code == 200
    token = r.json()["token"]
    with sqlite3.connect(legacy_dir / "bookkeeping.db") as conn:
        stored = {r[0] for r in conn.execute("SELECT token_hash FROM sessions WHERE user_id = 1")}
    assert hashlib.sha256(token.encode()).hexdigest() in stored

    # 已有密码的账号不能被重复注册
    r = client.post("/api/auth/register", json={"username": "18000000001", "password": "999999"})
    assert r.status_code == 409 and r.json()["status"] == "error"


def test_fresh_db_register_login_and_json_errors(tmp_path):
    mod = load_app(tmp_path)
    client = TestClient(mod.app, raise_server_exceptions=False)

    r = client.post("/api/auth/register", json={"username": "Alice", "password": "secret1"})
    assert r.status_code == 200
    # 用户名大小写不敏感去重
    r = client.post("/api/auth/register", json={"username": "alice", "password": "secret1"})
    assert r.status_code == 409

    r = client.post("/api/auth/login-password", json={"username": "ALICE", "password": "wrong!!"})
    assert r.status_code == 400 and r.json()["detail"] == "用户名或密码错误"
    r = client.post("/api/auth/login-password", json={"username": "alice", "password": "secret1"})
    assert r.status_code == 200 and r.json()["user"]["username"] == "Alice"

    # 参数校验错误返回中文 JSON，而不是列表
    r = client.post("/api/auth/register", json={"username": "ab", "password": "1"})
    assert r.status_code == 400 and isinstance(r.json()["detail"], str)
    r = client.post("/api/auth/register", json={"username": "bad name!", "password": "123456"})
    assert r.status_code == 400

    # 验证码登录接口已下线
    assert client.post("/api/auth/login", json={"phone": "18000000000", "code": "888888"}).status_code == 404


def test_sync_requires_auth_and_detects_conflicts(tmp_path):
    mod = load_app(tmp_path)
    client = TestClient(mod.app)
    assert client.get("/api/sync").status_code == 401

    token = client.post("/api/auth/register", json={"username": "bob", "password": "secret1"}).json()["token"]
    auth = {"Authorization": f"Bearer {token}"}
    assert client.get("/api/sync", headers=auth).json()["version"] == 0

    r = client.put("/api/sync", headers=auth, json={"base_version": 0, "blob": "cipher-1"})
    assert r.status_code == 200 and r.json()["version"] == 1
    # 另一台设备基于旧版本上传：冲突
    r = client.put("/api/sync", headers=auth, json={"base_version": 0, "blob": "cipher-stale"})
    assert r.status_code == 409 and r.json()["status"] == "error"
    r = client.put("/api/sync", headers=auth, json={"base_version": 1, "blob": "cipher-2"})
    assert r.json()["version"] == 2
    body = client.get("/api/sync", headers=auth).json()
    assert body["version"] == 2 and body["blob"] == "cipher-2"

    # 其他用户读不到
    other = client.post("/api/auth/register", json={"username": "eve", "password": "secret1"}).json()["token"]
    assert client.get("/api/sync", headers={"Authorization": f"Bearer {other}"}).json()["version"] == 0


def test_multiple_devices_stay_logged_in(tmp_path):
    mod = load_app(tmp_path)
    client = TestClient(mod.app)
    a = client.post("/api/auth/register", json={"username": "carol", "password": "secret1"}).json()["token"]
    b = client.post("/api/auth/login-password", json={"username": "carol", "password": "secret1"}).json()["token"]
    for t in (a, b):
        assert client.get("/api/sync", headers={"Authorization": f"Bearer {t}"}).status_code == 200
    # 超过 10 台设备时只保留最近使用的 10 个会话
    for _ in range(10):
        client.post("/api/auth/login-password", json={"username": "carol", "password": "secret1"})
    with sqlite3.connect(tmp_path / "bookkeeping.db") as conn:
        assert conn.execute("SELECT count(*) FROM sessions").fetchone()[0] == 10
