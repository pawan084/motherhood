# Aira — Deploy

## Backend

The backend fails **closed** in production: with `ENV=production` it refuses to
start unless these are set to real values (see `app.py`):

| Secret | Purpose |
|---|---|
| `ADMIN_JWT_SECRET` | signs admin session cookies |
| `APP_SESSION_SECRET` | signs mobile/web app-session tokens |
| `APP_SHARED_SECRET` | the `X-App-Token` edge gate |
| `GEMINI_API_KEY` | LLM (warned, not blocked — keyword gate still works) |
| `GOOGLE_CLIENT_ID` | Google Sign-In audience (warned; 503s at runtime if unset) |
| `ADMIN_ORIGINS` | CORS allow-list for the admin console |

Optional: `DATABASE_URL` (Postgres; else SQLite), `REDIS_URL` (multi-worker rate
limiting), `SENTRY_DSN`, `ADMIN_BOOTSTRAP_EMAIL`/`ADMIN_BOOTSTRAP_PASSWORD`.

> **Storage:** the default SQLite file lives wherever `SQLITE_PATH` points
> (default: next to `db.py`, i.e. *inside the image* — replaced on every deploy).
> Both blueprints below set `SQLITE_PATH` to a mounted volume; if you deploy some
> other way, set it yourself or your data is discarded on each release. The
> Postgres driver (`psycopg[binary]`) ships in `requirements.txt`, so setting
> `DATABASE_URL` is the only step needed to switch — and is the better answer for
> anything beyond a single instance, since a volume pins you to one machine.

### Fly.io
```bash
cd backend
fly launch --no-deploy            # uses fly.toml
fly secrets set ENV=production GEMINI_API_KEY=... ADMIN_JWT_SECRET=... \
  APP_SESSION_SECRET=... APP_SHARED_SECRET=... GOOGLE_CLIENT_ID=... \
  ADMIN_ORIGINS=https://admin.aira.app
fly deploy
```
`fly.toml` mounts a volume at `/data` **and** sets `SQLITE_PATH=/data/aira.db` so
the database actually lands on it. Prefer a Postgres `DATABASE_URL` for real
traffic (then the volume is optional, and you're no longer pinned to one machine).

### Render
Import the repo as a Blueprint (`backend/render.yaml`). `ADMIN_JWT_SECRET`,
`APP_SESSION_SECRET`, `APP_SHARED_SECRET` are auto-generated; set `GEMINI_API_KEY`,
`GOOGLE_CLIENT_ID`, `ADMIN_ORIGINS`, and optionally `DATABASE_URL` in the dashboard.
The blueprint declares a 1 GB disk at `/data` (with `SQLITE_PATH` pointed at it)
because Render's container filesystem is otherwise ephemeral. A disk pins the
service to one instance and disables zero-downtime deploys — drop both the disk
and `SQLITE_PATH` once you attach Postgres.

### Docker (any host)
```bash
cd backend
docker build -t aira-backend .
docker run -p 8000:8000 --env-file .env aira-backend
```

## Admin (Next.js)

Deploy on Vercel/Netlify or any Node host. Set `NEXT_PUBLIC_API_URL` to the
backend origin, and add that admin URL to the backend's `ADMIN_ORIGINS`.

> **Cookie note:** the session cookie is `SameSite=Strict` by default. Serve the
> admin and API on the same site, or set `ADMIN_COOKIE_DOMAIN=.aira.app` and put
> both on subdomains (`admin.` / `api.`). Mixing `localhost` and `127.0.0.1`
> loops login.

## Web (Vite + Cloudflare Worker)

`apps/web` targets Cloudflare via the Vinext adapter (`npm run build`). Set
`VITE_API_URL` to the backend, and `VITE_APP_TOKEN` if `APP_SHARED_SECRET` is on.

> **CI must install inside `apps/web`,** not at the repo root. Its toolchain is
> self-contained — own `package-lock.json`, own npm cache under `.sites-runtime`,
> and `build-verified.sh` resolves `node_modules/.bin/vinext` relative to the app
> directory. Use `npm ci` (or `npm run install:ci`) with `apps/web` as the working
> directory. The repo is intentionally not an npm workspace; hoisting to the root
> breaks the build.

> **CORS:** the web app is browser-based, so the backend must allow its origin —
> add the deployed web URL to `WEB_ORIGINS` (separate from `ADMIN_ORIGINS`). The
> defaults cover local dev (`http://localhost:5173`).

## Mobile (React Native)

The Kotlin and SwiftUI clients were removed; one React Native app replaces both.
Two things decide whether it can talk to a deployed backend at all.

**The app token is not optional.** `app.py` refuses to boot without
`APP_SHARED_SECRET`, and every route — including `/device/register` — 401s
before identity is checked when `X-App-Token` is missing. A build without it
fails at first launch, not at build time, which is how the deleted iOS client
shipped without ever sending the header.

**HTTPS only.** Both mobile platforms block cleartext by default (Android since
targetSdk 28, iOS via ATS), so the production URL must be `https://`. Keep any
loopback exception in a debug-only config that cannot merge into a release
build.

Configure `GOOGLE_CLIENT_ID` on the backend for Google Sign-In — one client id,
which is the only accepted `aud`. The mobile client must send a token minted for
that same (web) client id, which Google's guidance calls the server client id; a
token minted for a platform-specific client will be rejected. The web app needs
the same value as `VITE_GOOGLE_CLIENT_ID`, or its Sign in dialog says it isn't
configured rather than offering a button that can only 503.

For the urgent handoff, dial via the platform's *dialer intent* rather than
placing a call directly — it needs no call permission and leaves the person in
control of the last tap.
## Pre-launch checklist

- [ ] Clinician review of `aira.safety_classifier` prompt + `app/safety/gate.py` keyword lists
- [x] Retention/redaction for `safety_flags.message` — green turns are never
      stored, amber/red expire after `SAFETY_FLAG_RETENTION_DAYS` (default 90,
      purged at startup), and the text is withheld from `viewer` admins.
      Confirm the window matches your privacy policy before launch.
- [x] Data export + account deletion (`GET /v1/account/export`,
      `POST /v1/account/delete`) — wire them into every client's privacy centre.
- [ ] Care Vault byte storage + OCR-approval flow (currently metadata only)
- [ ] Real privacy/terms copy in `legal.py`
- [ ] Localize urgent/safety strings (EN/HI/Hinglish)
