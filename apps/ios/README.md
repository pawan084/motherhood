# Aira — iOS (SwiftUI)

Native iOS client for Aira, mirroring the web and Android apps: the same FastAPI
backend, the same proactive-companion concept (suggest → confirm), and the same
six screens — Today, Aira (chat), Journey, Learn, Care, You — plus onboarding and
the urgent-help handoff.

> **Status:** scaffold authored on Windows, so it has **never been compiled**.
> Build it in Xcode on a Mac and expect to fix a few things the way the Android
> mirror needed one fix on first compile.

## Build

```bash
brew install xcodegen        # once
cd apps/ios
xcodegen generate            # writes Aira.xcodeproj from project.yml
open Aira.xcodeproj          # or: xed .
```

Then pick a simulator and Run. Requires Xcode 15+ (Swift 5.9, iOS 16 target).

## Point it at the backend

`APIClient` defaults to `http://127.0.0.1:8000`. That works for the iOS
**Simulator** (which shares the Mac's network). On a **physical device**, set the
base URL to your Mac's LAN address, e.g. via an env var in the scheme:

```
AIRA_API_BASE = http://192.168.1.20:8000
```

The `NSAllowsLocalNetworking` ATS exception in `project.yml` permits http on the
local network for development; drop it for a release build over https.

Run the backend first (`cd backend && uvicorn app:app`).

## Layout

```
Aira/
  App/            AiraApp (@main), RootView, AppState (the store)
  Networking/     APIClient, Models (Codable, snake_case → camelCase)
  DesignSystem/   Theme (aubergine/ivory palette, serif display), Components
  Features/       MainTabView + Today / Chat / Journey / Learn / Care / You
                  + Onboarding + Urgent
```

## Parity notes / known gaps

- Auth is anonymous device-token only (email/password + Google sign-in are on the
  other clients; not wired here yet).
- Care supports viewing + the common adds (reminder, medicine, appointment,
  check-in, symptom) and marking done/taken; editing/deleting items and the Care
  Vault document upload are stubbed.
- Learn matches the shipped feature: server-resolved week video, category filter,
  save-for-later, and urgent topics routing to care instead of playback ("in
  production" state — no media yet).
- Values decode with `.convertFromSnakeCase`, so Swift properties are camelCase.
