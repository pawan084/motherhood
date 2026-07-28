"""Public legal/informational pages — reachable with no auth (App Review and
browsers must load them freely; they're in security._OPEN_PATHS). Minimal HTML
so the endpoints are real; production swaps in the reviewed legal copy.
"""
from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter(tags=["legal"])

_PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Aira — {title}</title>
<style>body{{font:16px/1.6 -apple-system,Segoe UI,Roboto,sans-serif;max-width:44rem;
margin:3rem auto;padding:0 1.25rem;color:#211d20;background:#f8f4ee}}
h1{{font-weight:600;color:#4a234b}} a{{color:#4a234b}}</style></head>
<body><h1>{title}</h1>{body}
<p style="margin-top:2rem;color:#716a6f">Aira is wellness support, not diagnosis
or emergency care. For emergencies, contact your care team or local emergency
services.</p></body></html>"""


def _page(title: str, body: str) -> HTMLResponse:
    return HTMLResponse(_PAGE.format(title=title, body=body))


@router.get("/privacy")
def privacy():
    return _page("Privacy", "<p>Your health data is private by design and is never "
                 "used for advertising. You control what Aira remembers, and you can "
                 "export or delete your data at any time. This placeholder is replaced "
                 "by the reviewed privacy policy before launch.</p>")


@router.get("/terms")
def terms():
    return _page("Terms", "<p>By using Aira you agree it provides wellness support "
                 "only and is not a substitute for professional medical care. "
                 "Placeholder — replaced by the reviewed terms before launch.</p>")


@router.get("/faq")
def faq():
    return _page("FAQ", "<p>Common questions about using Aira, privacy, and safety. "
                 "Placeholder content.</p>")


@router.get("/legal")
def legal_index():
    return _page("Legal", '<p><a href="/privacy">Privacy</a> · '
                 '<a href="/terms">Terms</a> · <a href="/faq">FAQ</a></p>')
