"""Admin sign-in for the endpoint monitor (10-07 종훈 · https://otto.shelldocs.cloud/).

The monitor and `/stats` were open to anyone on the public domain. Now they need an admin session:

  POST /admin/login   form `user` · `password` → an HttpOnly, Secure, SameSite=Strict cookie (12 h)
  POST /admin/logout  clears it
  GET  /admin/check   204 with a valid session, else 401 — nginx `auth_request` asks this before serving the page

No password is stored or shipped: `.env` holds `ADMIN_USER` and `ADMIN_PASSWORD_HASH`, a PBKDF2-SHA256 hash
made by `backend/scripts/admin_password.py` (asks for the password without echoing it). With no hash set the
monitor stays locked — there is no default password. The session is an HMAC over user · expiry, keyed by
`ADMIN_SESSION_SECRET` (or, if unset, by the password hash itself, so changing the password signs everyone out).
Failed sign-ins are throttled per client address.
"""
import base64
import hashlib
import hmac
import secrets
import time
from collections import defaultdict, deque

from fastapi import APIRouter, Form, HTTPException, Request, Response

from app.config import settings

router = APIRouter(prefix="/admin")

COOKIE = "otto_admin"
SESSION_S = 12 * 3600
PBKDF2_ROUNDS = 310_000
FAILS_PER_WINDOW = 5
FAIL_WINDOW_S = 300

_fails: dict[str, deque[float]] = defaultdict(deque)


def hash_password(password: str, *, salt: bytes | None = None, rounds: int = PBKDF2_ROUNDS) -> str:
    """`pbkdf2_sha256$rounds$salt$hash` (base64) — the line `.env` keeps"""
    salt = salt or secrets.token_bytes(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, rounds)
    b64 = lambda b: base64.b64encode(b).decode()  # noqa: E731
    return f"pbkdf2_sha256${rounds}${b64(salt)}${b64(dk)}"


def verify_password(password: str, stored: str) -> bool:
    try:
        algo, rounds, salt, want = stored.split("$")
        if algo != "pbkdf2_sha256":
            return False
        dk = hashlib.pbkdf2_hmac("sha256", password.encode(), base64.b64decode(salt), int(rounds))
        return hmac.compare_digest(dk, base64.b64decode(want))
    except (ValueError, TypeError):
        return False


def configured() -> bool:
    return bool(settings.admin_user and settings.admin_password_hash)


def _key() -> bytes:
    return (settings.admin_session_secret or settings.admin_password_hash).encode()


def _sign(user: str, exp: int) -> str:
    body = f"{user}|{exp}"
    mac = hmac.new(_key(), body.encode(), hashlib.sha256).hexdigest()
    return f"{body}|{mac}"


def session_ok(request: Request) -> bool:
    if not configured():
        return False
    token = request.cookies.get(COOKIE, "")
    try:
        user, exp, mac = token.rsplit("|", 2)
        exp_i = int(exp)
    except ValueError:
        return False
    good = hmac.new(_key(), f"{user}|{exp}".encode(), hashlib.sha256).hexdigest()
    return hmac.compare_digest(mac, good) and user == settings.admin_user and exp_i > time.time()


def require_admin(request: Request) -> None:
    """Dependency for anything only the admin may read (`/stats`)"""
    if not session_ok(request):
        raise HTTPException(401, "admin sign-in required")


def _client(request: Request) -> str:
    # The peer address only, never a forwarded header: the backend is also public (otto-back), and a client could
    # pick any X-Real-IP to dodge the throttle. Behind the tunnel every sign-in shares one address — then the
    # throttle is global, which is the safe side for a one-admin page.
    return request.client.host if request.client else "?"


def _throttled(ip: str) -> bool:
    q = _fails[ip]
    now = time.time()
    while q and now - q[0] > FAIL_WINDOW_S:
        q.popleft()
    return len(q) >= FAILS_PER_WINDOW


@router.post("/login", status_code=204)
def login(request: Request, response: Response, user: str = Form(...), password: str = Form(...)) -> None:
    if not configured():
        raise HTTPException(503, "admin sign-in is not set up on this server")
    ip = _client(request)
    if _throttled(ip):
        raise HTTPException(429, "too many attempts — try again in a few minutes")
    # always run the hash, so a wrong user name takes as long as a wrong password
    ok_pw = verify_password(password, settings.admin_password_hash)
    if not (ok_pw and hmac.compare_digest(user.encode(), settings.admin_user.encode())):
        _fails[ip].append(time.time())
        raise HTTPException(401, "wrong user name or password")
    _fails.pop(ip, None)
    exp = int(time.time()) + SESSION_S
    response.set_cookie(COOKIE, _sign(user, exp), max_age=SESSION_S, httponly=True,
                        secure=settings.admin_cookie_secure, samesite="strict", path="/")


@router.post("/logout", status_code=204)
def logout(response: Response) -> None:
    response.delete_cookie(COOKIE, path="/")


@router.get("/check", status_code=204)
def check(request: Request) -> None:
    require_admin(request)
