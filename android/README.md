# Aira — Native Android Experience

Aira is a private, AI-first maternal wellness companion built natively with Kotlin and Jetpack Compose. The app keeps onboarding, guidance and care actions inside one calm conversation while maintaining explicit safety and privacy boundaries.

## What is implemented

- Premium warm-ivory, aubergine, sage and lilac Material 3 design system
- Chat-first onboarding for trying to conceive, pregnancy, postpartum and exploration
- English, Hindi and Hinglish preference capture
- Five quiet destinations: Today, Aira, Journey, Care and You
- Today view with current context and one meaningful next action
- Aira command-centre chat with text, voice affordance and contextual tool tray
- Native document picker for prescriptions, reports and scans
- Medicine reminders, daily check-ins, symptom tracking and appointment copilot
- Animated two-minute wellness reset
- Talking-avatar preference and consent-safe future-baby story flow
- Native image pickers for both participant photos
- Care plan, Care Vault, care-team support and partner task controls
- Privacy centre, data export affordance, selective deletion and AI-memory review
- Full-screen urgent-care handoff with native phone dialer
- Offline emergency-profile prototype
- ViewModel state tests for onboarding, tool exclusivity and urgent routing

## Open in Android Studio

1. Extract this ZIP.
2. Open the `AiraAndroid` folder in Android Studio Otter or newer.
3. Allow Gradle sync to finish.
4. Select an Android 8.0+ emulator or device.
5. Run the `app` configuration.

The project targets Android API 36, uses Java 17, Kotlin 2.3.21, Android Gradle Plugin 8.13.2 and Compose BOM 2026.06.00.

## Architecture

```text
app/src/main/java/com/aira/companion/
├── MainActivity.kt
├── model/
│   └── AiraModels.kt
└── ui/
    ├── AiraApp.kt
    ├── AiraViewModel.kt
    ├── components/
    │   └── AiraComponents.kt
    ├── screens/
    │   ├── WelcomeScreen.kt
    │   ├── OnboardingChatScreen.kt
    │   ├── TodayScreen.kt
    │   ├── AiraChatScreen.kt
    │   ├── JourneyScreen.kt
    │   ├── CareScreen.kt
    │   ├── YouScreen.kt
    │   ├── ToolSheets.kt
    │   └── UrgentHelpDialog.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
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
- onboarding writes the care context (`PUT /care-context`); pregnant/postpartum
  journeys ask a coarse anchor question (approximate week / baby's age)
- every chat turn is `POST /respond` — the server-side safety gate decides;
  `urgent` opens the Urgent Help screen, `error` renders the honest copy
- backend URL: debug builds default to `http://10.0.2.2:8001` (emulator);
  physical device: `./gradlew :app:installDebug -PAIRA_DEV_BACKEND_URL=http://<mac-LAN-ip>:8001`
- debug builds allow cleartext (dev LAN); release pins an invalid URL until a
  production API exists

Not yet ported: SSE streaming, voice, cards-as-UI, Journey/Care/You backend
data (screens still show prototype content), sign-in.

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
