"""SMTP mailer contract without contacting a real server."""
from app import config
from app.core import mailer


def test_mailer_sends_login_code_over_smtp(monkeypatch):
    sent = {}

    monkeypatch.setattr(config, "SMTP_HOST", "smtp.example.com")
    monkeypatch.setattr(config, "SMTP_PORT", 2525)
    monkeypatch.setattr(config, "SMTP_FROM", "no-reply@example.com")
    monkeypatch.setattr(config, "SMTP_USERNAME", "user")
    monkeypatch.setattr(config, "SMTP_PASSWORD", "secret")
    monkeypatch.setattr(config, "SMTP_USE_TLS", True)

    class FakeSMTP:
        def __init__(self, host, port, timeout):
            sent["connect"] = (host, port, timeout)

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

        def starttls(self):
            sent["tls"] = True

        def login(self, username, password):
            sent["login"] = (username, password)

        def send_message(self, msg):
            sent["to"] = msg["To"]
            sent["from"] = msg["From"]
            sent["subject"] = msg["Subject"]
            sent["body"] = msg.get_content()

    monkeypatch.setattr(mailer.smtplib, "SMTP", FakeSMTP)

    mailer.send_login_code("person@example.com", "123456", ttl_minutes=10)

    assert sent["connect"] == ("smtp.example.com", 2525, 10)
    assert sent["tls"] is True
    assert sent["login"] == ("user", "secret")
    assert sent["to"] == "person@example.com"
    assert "123456" in sent["body"]
    assert "10 minutes" in sent["body"]

