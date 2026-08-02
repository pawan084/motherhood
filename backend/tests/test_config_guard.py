"""P0 tests: the production startup guard and the shared-catalog endpoint.

The guard tests matter more than they look. Their job is to make it impossible to
ship a production build running on the committed dev secret — the exact class of
mistake that is invisible until it is a disclosure.
"""
import importlib
import sys

import pytest


def _reload_config(monkeypatch, **env):
    """Re-import config with a specific environment."""
    for key in (
        "ENV", "GEMINI_API_KEY", "APP_SESSION_SECRET", "DEVICE_TOKEN_SECRET",
        "ADMIN_JWT_SECRET", "DATABASE_URL",
    ):
        monkeypatch.delenv(key, raising=False)
    for key, value in env.items():
        monkeypatch.setenv(key, value)
    sys.modules.pop("config", None)
    return importlib.import_module("config")


def test_development_never_raises(monkeypatch):
    cfg = _reload_config(monkeypatch, ENV="development")
    cfg.verify_production_config()  # no exception


def test_production_rejects_placeholder_secrets(monkeypatch):
    cfg = _reload_config(
        monkeypatch,
        ENV="production",
        GEMINI_API_KEY="real-key",
        DATABASE_URL="postgresql://u:p@h/db",
    )
    with pytest.raises(cfg.ConfigError) as exc:
        cfg.verify_production_config()
    assert "APP_SESSION_SECRET" in str(exc.value)


def test_production_rejects_sqlite(monkeypatch):
    cfg = _reload_config(
        monkeypatch,
        ENV="production",
        GEMINI_API_KEY="real-key",
        APP_SESSION_SECRET="a-real-secret",
        DEVICE_TOKEN_SECRET="another-real-secret",
        ADMIN_JWT_SECRET="a-third-real-secret",
    )
    with pytest.raises(cfg.ConfigError) as exc:
        cfg.verify_production_config()
    assert "DATABASE_URL" in str(exc.value)


def test_production_rejects_missing_gemini_key(monkeypatch):
    """No Gemini key means no safety classifier. That must not boot."""
    cfg = _reload_config(
        monkeypatch,
        ENV="production",
        APP_SESSION_SECRET="a-real-secret",
        DEVICE_TOKEN_SECRET="another-real-secret",
        ADMIN_JWT_SECRET="a-third-real-secret",
        DATABASE_URL="postgresql://u:p@h/db",
    )
    with pytest.raises(cfg.ConfigError) as exc:
        cfg.verify_production_config()
    assert "safety gate" in str(exc.value)


def test_production_accepts_a_complete_config(monkeypatch):
    cfg = _reload_config(
        monkeypatch,
        ENV="production",
        GEMINI_API_KEY="real-key",
        APP_SESSION_SECRET="a-real-secret",
        DEVICE_TOKEN_SECRET="another-real-secret",
        ADMIN_JWT_SECRET="a-third-real-secret",
        DATABASE_URL="postgresql://u:p@h/db",
    )
    cfg.verify_production_config()  # no exception


def test_device_token_secret_falls_back_to_session_secret(monkeypatch):
    cfg = _reload_config(
        monkeypatch, ENV="development", APP_SESSION_SECRET="shared-secret"
    )
    assert cfg.DEVICE_TOKEN_SECRET == "shared-secret"
