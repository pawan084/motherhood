# Aira — Native Android Experience

Aira is a private, AI-first maternal wellness companion built natively with Kotlin and Jetpack Compose. The app keeps onboarding, guidance and care actions inside one calm conversation while maintaining explicit safety and privacy boundaries.

## What is implemented

- Warm-ivory / aubergine / sage / lilac Material 3 design system, with a
  System · Light · Dark appearance picker
- Chat-first onboarding for trying to conceive, pregnancy, and postpartum, with an
  English / Hindi / Hinglish preference; returning users skip straight to Me
- **Three destinations — Me · Chat · Videos** — with Settings behind the top-bar
  gear (a full-screen overlay that absorbs the old You tab and the care tools)
- **Me**: a proactive home driven by `GET /today` — week/day hero with baby size,
  streaks, a mood check-in (7-day strip), Today's care check-offs, a daily tip and
  a suggested video
- **Chat**: every turn is `POST /respond` behind the server-side safety gate;
  `urgent` opens the full-screen handoff with a native dialer
- **Videos**: stage-aware catalog with search, likes, 1–5 star ratings, an in-app
  VideoView player, and unwatched-first rotation
- Wired care tools (tool sheets): medicines, reminders, appointments, care plan,
  symptom log, and the AI-memory review — all on real endpoints
- Opt-in daily notification (AlarmManager) and a home-screen widget fed from a
  local cache; pull-to-refresh and an offline staleness banner
- Full-screen urgent-care handoff, an offline emergency-profile stub, and ViewModel
  state tests for onboarding, tool exclusivity, and urgent routing

Still prototype (honest placeholders, no fake data): Care Vault OCR, the wellness
Reset animation, the talking-avatar / companion flow, on-device voice, partner
tasks, and the privacy toggles. The sprint notes at the end of this file spell out
exactly what is and isn't wired.

## Open in Android Studio

1. Open the `android/` folder in Android Studio Otter or newer.
2. Let Gradle sync finish.
3. Select an Android 8.0+ emulator or device.
4. Run the `app` configuration.

The project targets Android API 36 (min 26), uses Java 17, Kotlin 2.3.21, Android
Gradle Plugin 8.13.2 and Compose BOM 2026.06.00. It has **zero third-party
dependencies** — HttpURLConnection, `org.json`, and framework
VideoView/AlarmManager/RemoteViews/Canvas throughout. The backend base URL is
`BuildConfig.AIRA_BACKEND_URL` (debug default `http://10.0.2.2:8001`); override for
a physical device with `-PAIRA_DEV_BACKEND_URL=http://<host>:8001`.

## Architecture

```text
app/src/main/java/com/aira/companion/
├── MainActivity.kt
├── model/
│   └── AiraModels.kt          state + pure reducers (optimistic updates)
├── net/
│   └── AiraApi.kt             the backend client (HttpURLConnection, org.json)
├── notify/
│   ├── CareReminders.kt       opt-in daily notification (AlarmManager)
│   └── CareReminderReceiver.kt
├── widget/
│   └── AiraWidgetProvider.kt  home-screen widget (RemoteViews, prefs cache)
├── util/
│   └── ShareCard.kt           image share card (Canvas)
└── ui/
    ├── AiraApp.kt             the three destinations + Settings overlay
    ├── AiraViewModel.kt
    ├── components/            AiraComponents.kt, RemoteImage.kt
    ├── screens/               Welcome, OnboardingChat, Me, AiraChat, Videos,
    │                          VideoPlayer, Settings, DetailScreens, JourneyPath,
    │                          ToolSheets, UrgentHelpDialog
    └── theme/                 Color.kt, Theme.kt, Type.kt
```

## Production integration points

The UI and local interactions are implemented. Replace prototype callbacks with:

- Aira orchestration API and safety classifier
- Auth and encrypted user profile storage
- Clinical content service with review/version metadata
- Android notification scheduling through WorkManager/AlarmManager
- Encrypted document upload and OCR extraction approval
- Avatar streaming/lip-sync provider
- Consent ledger and data export/deletion APIs
- Analytics configured to exclude sensitive health content

## Important safety boundary

Aira is wellness support, not diagnosis or emergency care. The UI deliberately routes urgent concerns to the user’s care team and does not represent AI output as medical advice.

---

## P11: wired to the Aira backend

This app now runs against the real Aira API (see `net/AiraApi.kt`):
- guest device token minted via `POST /device/register` (SharedPreferences;
  TODO EncryptedSharedPreferences before release)
- onboarding completion persists next to the token, so relaunch (even after
  force-stop) lands returning users straight on Me — no re-onboarding
- onboarding writes the care context (`PUT /care-context`); pregnant/postpartum
  journeys ask a coarse anchor question (approximate week / baby's age)
- every chat turn is `POST /respond` — the server-side safety gate decides;
  `urgent` opens the Urgent Help screen, `error` renders the honest copy
- backend URL: debug builds default to `http://10.0.2.2:8001` (emulator);
  physical device: `./gradlew :app:installDebug -PAIRA_DEV_BACKEND_URL=http://<mac-LAN-ip>:8001`
- debug builds allow cleartext (dev LAN); release pins an invalid URL until a
  production API exists

Not yet ported: SSE streaming, voice, sign-in. Tool sheets wired to real
endpoints: check-in (/moods), reminder (/reminders), medicines, visit
copilot (/appointments), care plan, memory. Still prototype: Care Vault
upload flow, Reset, Symptom log, Companion, Voice, Partner, Support,
Privacy toggles, emergency-profile fields (honest "not recorded").

---

## P11 part 2: Me / Chat / Videos + Settings (2026-08-03)

Restructured from the prototype's five tabs. Me (journey hero, mood check-in
with 7-day strip, Today's care check-offs, suggested video), Chat (unchanged
wired chat), Videos (backend catalog -> YouTube via intent). Settings lives
behind the top-bar gear (full-screen overlay; absorbs the old You screen +
care tools). Optimistic updates via pure reducers in AiraModels.kt.

Dev networking: prefer `adb reverse tcp:8001 tcp:8001` + the default
`http://127.0.0.1:8001` debug URL — works over USB regardless of Wi-Fi.
Re-run the reverse after replugging the cable.

Device quirk (OPPO/ColorOS): `input tap` from ADB can double-fire, and
`am start` from ADB is blocked entirely — launch by tapping the icon.

On-device e2e verified (OPPO CPH2681, real backend + Gemini): onboarding
with the pregnancy anchor question -> real care context (week 8) -> mood
persisted server-side -> water tick 1/8 -> stage-aware suggested video ->
Videos catalog -> YouTube handoff -> Settings with real week -> chat turn
grounded in the real context ("normal to feel tired at 8 weeks"), memory
extracted, gate audited.

---

## World-class Me sprint (2026-08-03/04)

The 55-point home-page review is fully implemented. The app now has:

- Proactive Me driven by `GET /today` (hero with Week · Day, ~cm, streaks;
  focus nudges with tap-learning; daily tip + video; Sunday recap;
  week-flip banner; first-run name prompt)
- Dark mode (AiraPalette state-getters — no per-screen changes), with a
  Settings System/Light/Dark picker
- Undo on ticks, haptics, editable water goal, pace rings on droplets,
  mood notes, postpartum AM/PM check-in slots
- Videos: search, likes, real star averages, in-app VideoView player,
  watched -> unwatched-first rotation, transcript expander (mechanism)
- Opt-in daily notification (AlarmManager; enabling posts a preview through
  the same path — adaptive icons are NOT valid small icons, use the vector)
- Home-screen widget (RemoteViews from a prefs cache the ViewModel writes
  on every Me refresh)
- Pull-to-refresh, offline banner, thumbnail disk cache, l10n pipeline
  (values-hi covers Me chrome; clinical copy stays server-side, HUMAN-GATED)

Still zero new dependencies: HttpURLConnection, org.json, framework
VideoView/AlarmManager/RemoteViews/Canvas throughout.
