package com.aira.companion.model

enum class AppStage {
    Welcome,
    Onboarding,
    Main,
}

enum class MainDestination(
    val label: String,
) {
    Today("Today"),
    Aira("Aira"),
    Journey("Journey"),
    Care("Care"),
    You("You"),
}

enum class JourneyType(
    val label: String,
    val supportingText: String,
) {
    Trying("Trying to conceive", "Cycle-aware wellness and preparation"),
    Pregnant("Pregnant", "Week-by-week guidance and care planning"),
    Postpartum("Postpartum", "Recovery, feeding and emotional support"),
    Exploring("Just exploring", "See how Aira can support your journey"),
}

enum class AiraTool(
    val title: String,
    val eyebrow: String,
) {
    Notifications("Updates", "Notification centre"),
    CheckIn("How are you?", "Daily check-in"),
    Reminder("Create a reminder", "Aira tool"),
    Medicines("Medicines", "Care routine"),
    Appointment("Visit copilot", "Tomorrow · 10:30 AM"),
    CareVault("Add to Care Vault", "Private document upload"),
    Reset("A two-minute reset", "Guided wellness"),
    Symptom("Log a symptom", "Track, don’t diagnose"),
    Companion("Companion mode", "Avatar & connection"),
    CarePlan("Your care plan", "Week 24 priorities"),
    Privacy("Privacy centre", "Your data, your control"),
    Memory("What Aira remembers", "Care context"),
    Voice("Voice & language", "Conversation settings"),
    Partner("Partner actions", "Practical support"),
    Support("Human support", "Help centre"),
    Emergency("Emergency profile", "Available offline"),
}

data class ChatMessage(
    val id: Long,
    val fromAira: Boolean,
    val text: String,
)

data class OnboardingAnswer(
    val question: String,
    val answer: String,
)

data class AiraUiState(
    val stage: AppStage = AppStage.Welcome,
    val destination: MainDestination = MainDestination.Aira,
    val onboardingStep: Int = 0,
    val onboardingAnswers: List<OnboardingAnswer> = emptyList(),
    // Live prompt list: an anchor question (how far along / baby's age) is
    // appended once the journey answer makes it relevant — the backend needs
    // a real anchor date for pregnant/postpartum contexts.
    val prompts: List<OnboardingPrompt> = onboardingPrompts,
    val anchorChoice: String = "",
    val journey: JourneyType? = null,
    val language: String = "English",
    val priority: String = "",
    val companionPreference: String = "Text & voice",
    val activeTool: AiraTool? = null,
    val toolsOpen: Boolean = false,
    val urgentHelpOpen: Boolean = false,
    val notificationCount: Int = 3,
    val chatDraft: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val snackbarMessage: String? = null,
)

data class OnboardingPrompt(
    val question: String,
    val helper: String,
    val options: List<String>,
)

/** Coarse anchor questions — honest approximations the person can refine
 * later; the backend computes the week from the resulting date. */
val pregnancyAnchorPrompt =
    OnboardingPrompt(
        question = "About how far along are you?",
        helper = "An approximate week is enough — you can refine it later.",
        options = listOf("~8 weeks", "~16 weeks", "~24 weeks", "~32 weeks"),
    )

val postpartumAnchorPrompt =
    OnboardingPrompt(
        question = "How old is your baby?",
        helper = "Roughly is fine — this shapes recovery guidance.",
        options = listOf("Under 2 weeks", "About a month", "2–3 months", "4+ months"),
    )

val onboardingPrompts =
    listOf(
        OnboardingPrompt(
            question = "Where are you in your journey?",
            helper = "This helps Aira shape a private care context.",
            options = JourneyType.entries.map { it.label },
        ),
        OnboardingPrompt(
            question = "How should we speak with you?",
            helper = "You can change language or use voice at any time.",
            options = listOf("English", "Hindi", "Hinglish"),
        ),
        OnboardingPrompt(
            question = "What would feel most helpful first?",
            helper = "Aira will keep Today focused on one meaningful action.",
            options =
                listOf(
                    "Understand changes",
                    "Prepare for a visit",
                    "Feel calmer",
                    "Plan my care",
                ),
        ),
        OnboardingPrompt(
            question = "How would you like Aira to be present?",
            helper = "Choose a calm interface now; this stays under your control.",
            options = listOf("Text & voice", "Talking avatar", "Chat only"),
        ),
    )
