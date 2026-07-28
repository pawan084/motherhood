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

### Fly.io
```bash
cd backend
fly launch --no-deploy            # uses fly.toml
fly secrets set ENV=production GEMINI_API_KEY=... ADMIN_JWT_SECRET=... \
  APP_SESSION_SECRET=... APP_SHARED_SECRET=... GOOGLE_CLIENT_ID=... \
  ADMIN_ORIGINS=https://admin.aira.app
fly deploy
```
`fly.toml` mounts a volume for the SQLite file; prefer a Postgres `DATABASE_URL`
for real traffic (then the volume is optional).

### Render
Import the repo as a Blueprint (`backend/render.yaml`). `ADMIN_JWT_SECRET`,
`APP_SESSION_SECRET`, `APP_SHARED_SECRET` are auto-generated; set `GEMINI_API_KEY`,
`GOOGLE_CLIENT_ID`, `ADMIN_ORIGINS`, and optionally `DATABASE_URL` in the dashboard.

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

> **CORS:** the web app is browser-based, so the backend must allow its origin —
> add the deployed web URL to `WEB_ORIGINS` (separate from `ADMIN_ORIGINS`). The
> defaults cover local dev (`http://localhost:5173`).

## Android

Build a release with the production backend URL:
```bash
cd apps/android
./gradlew assembleRelease -PairaApiBase=https://api.aira.app
```
Configure `GOOGLE_CLIENT_ID` / `ANDROID_PACKAGE_NAME` on the backend for Google
Sign-In. Note the app uses `ACTION_DIAL` (no `CALL_PHONE` permission needed).

## Pre-launch checklist

- [ ] Clinician review of `aira.safety_classifier` prompt + `safety.py` keyword lists
- [ ] Retention/redaction policy for `safety_flags.message` (sensitive health text)
- [ ] Care Vault byte storage + OCR-approval flow (currently metadata only)
- [ ] Real privacy/terms copy in `legal.py`
- [ ] Localize urgent/safety strings (EN/HI/Hinglish)
