"""Bookkeeping account & encrypted-backup server.

- 账号：用户名 + 密码（PBKDF2 加盐），登录令牌只保存 sha256 哈希。
- 备份：端到端加密的不透明密文（客户端尚未接入），服务端无法读取账目。
所有错误统一返回 JSON：{"status": "error", "detail": "<中文提示>"}。
"""
import hashlib
import hmac
import json
import logging
import os
import re
import secrets
import sqlite3
import threading
import time
from collections import defaultdict, deque
from contextlib import closing

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException as StarletteHTTPException

DATA_DIR = os.environ.get("BOOKKEEPING_DATA", "/var/lib/bookkeeping")
DIST_DIR = os.environ.get("BOOKKEEPING_DIST", "/opt/bookkeeping/web")
DB_PATH = os.path.join(DATA_DIR, "bookkeeping.db")
MAX_BLOB = 8 * 1024 * 1024
HEX64 = re.compile(r"^[0-9a-f]{64}$")
USERNAME_RE = re.compile(r"^[A-Za-z0-9_.@\-一-龥]{3,32}$")
PBKDF2_ROUNDS = 100_000

log = logging.getLogger("bookkeeping")
app = FastAPI(title="Bookkeeping", docs_url=None, redoc_url=None, openapi_url=None)
_lock = threading.Lock()


def db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


# ───────────────────────── 表结构与迁移 ─────────────────────────

USERS_DDL = """CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT,
    password_salt TEXT,
    token_hash TEXT,
    created_at INTEGER NOT NULL,
    last_login_at INTEGER NOT NULL
)"""


def _columns(conn: sqlite3.Connection, table: str) -> dict:
    return {r[1]: r for r in conn.execute(f"PRAGMA table_info({table})").fetchall()}


def _token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _migrate_users(conn: sqlite3.Connection) -> None:
    """
    旧版 users 表来自手机号验证码登录：phone UNIQUE NOT NULL、明文 token、username 可空。
    重建为以 username 为唯一键的新表；无用户名的旧账号以手机号作为用户名（无密码，可通过注册设置密码认领），
    已有明文 token 转为哈希，保持已登录客户端有效。
    """
    cols = _columns(conn, "users")
    if not cols or ("token_hash" in cols and "phone" not in cols):
        return
    backup = os.path.join(DATA_DIR, "backups", f"bookkeeping-before-users-migration-{int(time.time())}.db")
    os.makedirs(os.path.dirname(backup), exist_ok=True)
    with closing(sqlite3.connect(backup)) as dst:
        conn.backup(dst)

    select_cols = [c for c in ("id", "phone", "username", "password_hash", "password_salt", "token", "created_at", "last_login_at") if c in cols]
    rows = [dict(zip(select_cols, r)) for r in conn.execute(f"SELECT {', '.join(select_cols)} FROM users ORDER BY id").fetchall()]
    conn.execute("ALTER TABLE users RENAME TO users_legacy")
    conn.execute(USERS_DDL)
    taken: set[str] = set()
    for r in rows:
        name = (r.get("username") or "").strip() or (r.get("phone") or "").strip() or f"user{r['id']}"
        if name.lower() in taken:
            name = f"{name}_{r['id']}"
        taken.add(name.lower())
        token = r.get("token")
        conn.execute(
            "INSERT INTO users (id, username, password_hash, password_salt, token_hash, created_at, last_login_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            (r["id"], name, r.get("password_hash"), r.get("password_salt"), _token_hash(token) if token else None,
             r.get("created_at") or 0, r.get("last_login_at") or 0),
        )
    conn.execute("DROP TABLE users_legacy")
    log.warning("users table migrated (%d rows), backup at %s", len(rows), backup)


def init_db() -> None:
    os.makedirs(DATA_DIR, exist_ok=True)
    with closing(db()) as conn, conn:
        conn.execute(
            """CREATE TABLE IF NOT EXISTS backups (
                space TEXT PRIMARY KEY,
                auth_hash TEXT NOT NULL,
                blob TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
        conn.execute(USERS_DDL)
        _migrate_users(conn)
        conn.execute(
            """CREATE TABLE IF NOT EXISTS ledgers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                space TEXT NOT NULL,
                is_default INTEGER DEFAULT 1,
                created_at INTEGER NOT NULL
            )"""
        )
        conn.execute("CREATE INDEX IF NOT EXISTS idx_ledgers_user ON ledgers(user_id)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_users_token ON users(token_hash)")
        # 验证码登录已下线（曾存在万能码），清理残留表
        conn.execute("DROP TABLE IF EXISTS otp_codes")


init_db()


# ───────────────────────── 统一错误响应 ─────────────────────────

def _error(status: int, detail: str) -> JSONResponse:
    return JSONResponse({"status": "error", "detail": detail}, status_code=status)


@app.exception_handler(StarletteHTTPException)
async def _http_error(_: Request, exc: StarletteHTTPException):
    detail = exc.detail if isinstance(exc.detail, str) else "请求失败"
    return _error(exc.status_code, detail)


@app.exception_handler(RequestValidationError)
async def _validation_error(_: Request, exc: RequestValidationError):
    fields = {str(e.get("loc", [""])[-1]) for e in exc.errors()}
    if "username" in fields:
        msg = "用户名需为 3-32 位字母、数字、中文或 _.@-"
    elif "password" in fields:
        msg = "密码需为 6-64 位"
    else:
        msg = "请求参数不正确"
    return _error(400, msg)


@app.exception_handler(Exception)
async def _unexpected_error(_: Request, exc: Exception):
    log.exception("unhandled error: %s", exc)
    return _error(500, "服务器开小差了，请稍后重试")


# ───────────────────────── 限流 ─────────────────────────

_hits: dict[str, deque] = defaultdict(deque)
_fails: dict[str, deque] = defaultdict(deque)
_last_sweep = 0.0


def _client_ip(request: Request) -> str:
    cf = request.headers.get("CF-Connecting-IP")
    if cf:
        return cf.strip()
    fwd = request.headers.get("X-Forwarded-For")
    if fwd:
        return fwd.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


def _sweep(now: float) -> None:
    """定期清理过期 IP 记录，避免内存随访问 IP 数无限增长"""
    global _last_sweep
    if now - _last_sweep < 300:
        return
    _last_sweep = now
    for store, window in ((_hits, 60), (_fails, 600)):
        for ip in [k for k, dq in store.items() if not dq or dq[-1] < now - window]:
            store.pop(ip, None)


def _check_rate(ip: str, limit: int = 120, window: int = 60) -> None:
    now = time.time()
    _sweep(now)
    dq = _hits[ip]
    while dq and dq[0] < now - window:
        dq.popleft()
    if len(dq) >= limit:
        raise HTTPException(status_code=429, detail="请求过于频繁，请稍后再试")
    dq.append(now)


def _check_fail_rate(ip: str, limit: int = 20, window: int = 600) -> None:
    now = time.time()
    dq = _fails[ip]
    while dq and dq[0] < now - window:
        dq.popleft()
    if len(dq) >= limit:
        raise HTTPException(status_code=429, detail="失败次数过多，请 10 分钟后再试")


def _record_fail(ip: str) -> None:
    _fails[ip].append(time.time())


# ───────────────────────── 基础接口 ─────────────────────────

@app.get("/api/health")
def health(request: Request):
    _check_rate(_client_ip(request))
    return {"status": "ok", "app": "bookkeeping", "time": int(time.time())}


@app.get("/api/version")
def version(request: Request):
    _check_rate(_client_ip(request))
    path = os.path.join(DIST_DIR, "version.json")
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="尚未发布版本")
    with open(path, "r", encoding="utf-8") as f:
        return JSONResponse(json.load(f))


# ───────────────────────── 加密备份（客户端尚未接入） ─────────────────────────

class BackupPayload(BaseModel):
    blob: str = Field(..., max_length=MAX_BLOB)
    updated_at: int = Field(default_factory=lambda: int(time.time() * 1000))


@app.get("/api/backup/{space}")
def get_backup(space: str, request: Request, x_auth: str = Header(None)):
    ip = _client_ip(request)
    _check_rate(ip)
    _check_fail_rate(ip)
    if not HEX64.match(space):
        raise HTTPException(status_code=400, detail="invalid space format")
    if not x_auth or not HEX64.match(x_auth):
        raise HTTPException(status_code=401, detail="missing or invalid auth")

    with closing(db()) as conn:
        row = conn.execute(
            "SELECT auth_hash, blob, updated_at FROM backups WHERE space = ?", (space,)
        ).fetchone()

    if not row:
        return JSONResponse({"exists": False, "blob": None, "updated_at": 0})

    stored_hash, blob, updated_at = row
    if not hmac.compare_digest(stored_hash, _token_hash(x_auth)):
        _record_fail(ip)
        raise HTTPException(status_code=401, detail="auth mismatch")

    return {"exists": True, "blob": blob, "updated_at": updated_at}


@app.put("/api/backup/{space}")
def put_backup(space: str, payload: BackupPayload, request: Request, x_auth: str = Header(None)):
    ip = _client_ip(request)
    _check_rate(ip)
    _check_fail_rate(ip)
    if not HEX64.match(space):
        raise HTTPException(status_code=400, detail="invalid space format")
    if not x_auth or not HEX64.match(x_auth):
        raise HTTPException(status_code=401, detail="missing or invalid auth")

    auth_hash = _token_hash(x_auth)
    now_ms = int(time.time() * 1000)

    with _lock, closing(db()) as conn, conn:
        row = conn.execute(
            "SELECT auth_hash, updated_at FROM backups WHERE space = ?", (space,)
        ).fetchone()

        if row:
            stored_hash, stored_ts = row
            if not hmac.compare_digest(stored_hash, auth_hash):
                _record_fail(ip)
                raise HTTPException(status_code=401, detail="auth mismatch")
            if payload.updated_at < stored_ts:
                raise HTTPException(status_code=409, detail="stale backup ignored")
            conn.execute(
                "UPDATE backups SET blob = ?, updated_at = ? WHERE space = ?",
                (payload.blob, payload.updated_at, space),
            )
        else:
            conn.execute(
                "INSERT INTO backups (space, auth_hash, blob, updated_at, created_at) VALUES (?, ?, ?, ?, ?)",
                (space, auth_hash, payload.blob, payload.updated_at, now_ms),
            )

    return {"status": "ok", "space": space, "updated_at": payload.updated_at}


# ───────────────────────── 账号 ─────────────────────────

def hash_password(password: str, salt: bytes | None = None) -> tuple[str, str]:
    if salt is None:
        salt = os.urandom(16)
    hashed = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ROUNDS)
    return hashed.hex(), salt.hex()


def verify_password(password: str, hashed_hex: str, salt_hex: str) -> bool:
    try:
        computed, _ = hash_password(password, bytes.fromhex(salt_hex))
        return hmac.compare_digest(computed, hashed_hex)
    except ValueError:
        return False


class CredentialPayload(BaseModel):
    username: str = Field(..., min_length=3, max_length=32)
    password: str = Field(..., min_length=6, max_length=64)


def _clean_username(raw: str) -> str:
    username = raw.strip()
    if not USERNAME_RE.match(username):
        raise HTTPException(status_code=400, detail="用户名需为 3-32 位字母、数字、中文或 _.@-")
    return username


def _ensure_ledger(conn: sqlite3.Connection, user_id: int, now: int) -> dict:
    row = conn.execute(
        "SELECT name, space FROM ledgers WHERE user_id = ? ORDER BY is_default DESC, id ASC LIMIT 1", (user_id,)
    ).fetchone()
    if row:
        return {"name": row[0], "space": row[1]}
    space = hashlib.sha256(f"user_{user_id}_single_ledger".encode()).hexdigest()
    conn.execute(
        "INSERT INTO ledgers (user_id, name, space, is_default, created_at) VALUES (?, ?, ?, 1, ?)",
        (user_id, "个人账本", space, now),
    )
    return {"name": "个人账本", "space": space}


def _issue_token(conn: sqlite3.Connection, user_id: int, now: int) -> str:
    token = secrets.token_hex(32)
    conn.execute("UPDATE users SET token_hash = ?, last_login_at = ? WHERE id = ?", (_token_hash(token), now, user_id))
    return token


def _auth_response(user_id: int, username: str, token: str, ledger: dict) -> dict:
    return {"status": "ok", "token": token, "user": {"id": user_id, "username": username}, "ledger": ledger}


@app.post("/api/auth/register")
def register(payload: CredentialPayload, request: Request):
    ip = _client_ip(request)
    _check_rate(ip, limit=10, window=60)
    username = _clean_username(payload.username)
    now = int(time.time())
    pwd_hash, pwd_salt = hash_password(payload.password)

    with _lock, closing(db()) as conn, conn:
        row = conn.execute("SELECT id, username, password_hash FROM users WHERE username = ?", (username,)).fetchone()
        if row and row[2]:
            raise HTTPException(status_code=409, detail="该用户名已被注册，请直接登录")
        if row:
            # 旧版验证码登录遗留的无密码账号：注册即为其设置密码
            user_id, username = row[0], row[1]
            conn.execute("UPDATE users SET password_hash = ?, password_salt = ? WHERE id = ?", (pwd_hash, pwd_salt, user_id))
        else:
            cur = conn.execute(
                "INSERT INTO users (username, password_hash, password_salt, created_at, last_login_at) VALUES (?, ?, ?, ?, ?)",
                (username, pwd_hash, pwd_salt, now, now),
            )
            user_id = cur.lastrowid
        token = _issue_token(conn, user_id, now)
        ledger = _ensure_ledger(conn, user_id, now)

    return _auth_response(user_id, username, token, ledger)


@app.post("/api/auth/login-password")
def login_password(payload: CredentialPayload, request: Request):
    ip = _client_ip(request)
    _check_rate(ip)
    _check_fail_rate(ip)
    username = payload.username.strip()
    now = int(time.time())

    with _lock, closing(db()) as conn, conn:
        row = conn.execute(
            "SELECT id, username, password_hash, password_salt FROM users WHERE username = ?", (username,)
        ).fetchone()
        if row and not row[2]:
            raise HTTPException(status_code=400, detail="该账号尚未设置密码，请在「注册新账号」中用同一用户名设置密码")
        if not row or not verify_password(payload.password, row[2], row[3]):
            _record_fail(ip)
            raise HTTPException(status_code=400, detail="用户名或密码错误")
        user_id, username = row[0], row[1]
        token = _issue_token(conn, user_id, now)
        ledger = _ensure_ledger(conn, user_id, now)

    return _auth_response(user_id, username, token, ledger)


@app.get("/api/auth/me")
def get_current_user(request: Request, authorization: str = Header(None)):
    ip = _client_ip(request)
    _check_rate(ip)
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="未登录或登录已失效")
    token = authorization[len("Bearer "):].strip()

    with _lock, closing(db()) as conn, conn:
        row = conn.execute("SELECT id, username FROM users WHERE token_hash = ?", (_token_hash(token),)).fetchone()
        if not row:
            raise HTTPException(status_code=401, detail="登录已失效，请重新登录")
        ledger = _ensure_ledger(conn, row[0], int(time.time()))

    return {"status": "ok", "user": {"id": row[0], "username": row[1]}, "ledger": ledger}
