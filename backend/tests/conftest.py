"""Test config: a hermetic SQLite file and no external providers, so the whole
suite runs offline. The safety gate's keyword floor is deterministic without a
Gemini key — which is exactly the fail-safe behaviour we want to pin down.
"""
import os
import tempfile

# Must be set BEFORE app/db import (they read these at import time).
_TMP = tempfile.mkdtemp(prefix="aira-test-")
os.environ["SQLITE_PATH"] = os.path.join(_TMP, "aira-test.db")
os.environ.pop("DATABASE_URL", None)
os.environ.pop("GEMINI_API_KEY", None)        # force keyword-only safety + fallback reply
os.environ.pop("APP_SHARED_SECRET", None)     # coarse gate off in tests
os.environ["ENV"] = "development"
# The limiter is per-IP and every test shares the TestClient's IP, so the whole
# suite draws on one 120-request budget — a slow accumulation that makes an
# unrelated new test fail whichever one happens to run past the cap. Off here;
# test_rate_limit.py exercises the limiter directly instead.
os.environ["RATE_LIMIT_PER_MIN"] = "0"
# Seeded on first init() into the fresh temp DB, so the admin RBAC tests have an
# owner to log in as (and to create lower-privileged admins from).
os.environ["ADMIN_BOOTSTRAP_EMAIL"] = "owner@test.local"
os.environ["ADMIN_BOOTSTRAP_PASSWORD"] = "owner-password-for-tests"

import pytest
from fastapi.testclient import TestClient


@pytest.fixture(scope="session")
def client():
    import app
    with TestClient(app.app) as c:   # context manager runs startup (init tables)
        yield c


@pytest.fixture()
def user(client):
    """A registered anonymous device user + auth headers."""
    r = client.post("/device/register")
    assert r.status_code == 200, r.text
    token = r.json()["token"]
    return {"id": r.json()["user_id"], "headers": {"Authorization": f"Bearer {token}"}}


def admin_login(client, email="owner@test.local", password="owner-password-for-tests"):
    """Log the shared client in as an admin; returns the CSRF header to send with
    mutating requests. Replaces any previously held admin session cookie."""
    r = client.post("/admin/login", json={"email": email, "password": password})
    assert r.status_code == 200, r.text
    return {"X-CSRF-Token": client.cookies.get("csrf_token")}
