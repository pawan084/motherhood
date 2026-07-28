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
