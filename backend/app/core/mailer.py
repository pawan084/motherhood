"""Small SMTP mailer used by passwordless sign-in.

No dependency and no template engine: this sends one transactional email, and it
fails closed so the account flow never creates a usable login code that nobody
received.
"""
import logging
import smtplib
from email.message import EmailMessage

from app import config

log = logging.getLogger("aira.mailer")


def configured() -> bool:
    return bool(config.SMTP_HOST and config.SMTP_FROM)


def send_login_code(to_email: str, code: str, *, ttl_minutes: int = 10) -> None:
    if not configured():
        raise RuntimeError("SMTP is not configured")

    msg = EmailMessage()
    msg["Subject"] = "Your Aira sign-in code"
    msg["From"] = config.SMTP_FROM
    msg["To"] = to_email
    msg.set_content(
        f"Your Aira sign-in code is {code}.\n\n"
        f"It expires in {ttl_minutes} minutes. If you did not request this, you can ignore it.\n"
    )

    try:
        with smtplib.SMTP(config.SMTP_HOST, config.SMTP_PORT, timeout=10) as smtp:
            if config.SMTP_USE_TLS:
                smtp.starttls()
            if config.SMTP_USERNAME:
                smtp.login(config.SMTP_USERNAME, config.SMTP_PASSWORD)
            smtp.send_message(msg)
    except Exception as e:  # noqa: BLE001
        log.warning("SMTP login-code delivery failed for %s: %s", to_email, e)
        raise RuntimeError("email delivery failed") from e

