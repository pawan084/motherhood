"""Shared fixtures: a hermetic app instance on a temp SQLite file.

Modules cache their DB connection at import/init time, so the temp database has
to be configured BEFORE anything imports `db`. Tests that need the app use the
`client` fixture; module reloads keep each test file honest about env deps.
"""
import importlib
import os
import sys

import pytest


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("SQLITE_PATH", str(tmp_path / "test.db"))
    monkeypatch.setenv("ALLOW_DEV_LOGIN", "1")
    monkeypatch.delenv("DATABASE_URL", raising=False)
    monkeypatch.delenv("ENV", raising=False)
    monkeypatch.setenv("RATE_LIMIT_PER_MIN", "0")  # tests hammer endpoints

    # Fresh module graph so every module re-reads the env above.
    for mod in ("app", "chat", "services", "prompts", "safety", "safety_taxonomy",
                "care_context", "device_auth", "accounts", "security", "db", "config"):
        sys.modules.pop(mod, None)
    app_module = importlib.import_module("app")

    from fastapi.testclient import TestClient
    with TestClient(app_module.app) as c:  # context manager runs the lifespan
        yield c
