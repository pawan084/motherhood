package com.aira.companion.model

import com.aira.companion.data.CareData
import com.aira.companion.data.ConsentFeature
import com.aira.companion.data.MemoryItem
import com.aira.companion.data.PartnerInvite
import com.aira.companion.data.PartnerInviteRow
import com.aira.companion.data.PartnerShare
import com.aira.companion.data.VoicePrefs

enum class AppStage {
    /** Resolving the cached session against the backend before showing anything,
     *  so a returning user is not flashed the Welcome screen they already passed. */
    Starting,
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
    // Eyebrows are static labels, so they must not claim a specific time or week
    // — "Tomorrow · 10:30 AM" and "Week 24 priorities" were shown to every user.
    Appointment("Visit copilot", "Appointments"),
    CareVault("Add to Care Vault", "Private document upload"),
    Reset("A two-minute reset", "Guided wellness"),
    Symptom("Log a symptom", "Track, don’t diagnose"),
    Companion("Companion mode", "Avatar & connection"),
    CarePlan("Your care plan", "Built from your reminders"),
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
    // "wellness" | "watchful" for an Aira reply from the safety-gated backend; null otherwise.
    val trustLabel: String? = null,
)

data class OnboardingAnswer(
    val question: String,
    val answer: String,
)

// ── Live data from the backend (GET /v1/today, GET /v1/journey) ──────────────

data class TodayNextAction(
    val tool: String,
    val title: String,
    val detail: String,
    val minutes: Int?,
)

data class TodayData(
    val name: String,
    val journey: String,
    val contextLine: String,
    val weeks: Int?,
    val nextAction: TodayNextAction?,
    val priorities: List<String>,
)

data class JourneySection(
    val title: String,
    val text: String,
)

data class JourneyData(
    val journey: String,
    val title: String,
    val weeks: Int?,
    val thisWeek: String,
    val body: String,
    val sections: List<JourneySection>,
)

/** API journey key -> a human label for headers/rings. */
fun journeyLabel(journey: String?): String =
    when (journey?.lowercase()) {
        "trying" -> "Trying to conceive"
        "pregnant" -> "Pregnant"
        "postpartum" -> "Postpartum"
        else -> "Exploring"
    }

/** Map a backend next-action tool key to the in-app tool sheet. */
fun toolKeyToTool(key: String?): AiraTool? =
    when (key?.lowercase()) {
        "checkin" -> AiraTool.CheckIn
        "reminder" -> AiraTool.Reminder
        "appointment" -> AiraTool.Appointment
        "upload" -> AiraTool.CareVault
        "wellness" -> AiraTool.Reset
        "symptom" -> AiraTool.Symptom
        "careplan" -> AiraTool.CarePlan
        "support" -> AiraTool.Support
        else -> null
    }

data class AiraUiState(
    val stage: AppStage = AppStage.Starting,
    val destination: MainDestination = MainDestination.Aira,
    val onboardingStep: Int = 0,
    val onboardingAnswers: List<OnboardingAnswer> = emptyList(),
    val journey: JourneyType? = null,
    // Collected during onboarding and sent to POST /v1/onboarding. Both used to
    // be hardcoded to null on the way out.
    val name: String = "",
    val weeks: Int? = null,
    val language: String = "English",
    val priority: String = "",
    val companionPreference: String = "Text & voice",
    val activeTool: AiraTool? = null,
    val toolsOpen: Boolean = false,
    val urgentHelpOpen: Boolean = false,
    // Starts at zero. This defaulted to 3, so every fresh install showed a red
    // "3 unread" badge over a notification list nothing had ever written to.
    val notificationCount: Int = 0,
    val chatDraft: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    // Journey-aware content loaded from the backend for the Today/Journey screens.
    val todayData: TodayData? = null,
    val journeyData: JourneyData? = null,
    // The Care hub, memory and consent — all read from the backend rather than
    // the fixed sample data these screens used to render.
    val careData: CareData? = null,
    val careLoading: Boolean = false,
    val memory: List<MemoryItem> = emptyList(),
    val consent: List<ConsentFeature> = emptyList(),
    // Populated from the backend's urgent handoff / emergency profile so the
    // urgent dialer calls a REAL number instead of a hardcoded one.
    val careTeamPhone: String? = null,
    val urgentMessage: String? = null,
    val snackbarMessage: String? = null,
    // The stored voice preference, and the partner invite most recently created.
    // Both used to be toasts that persisted nothing.
    val voicePrefs: VoicePrefs = VoicePrefs(),
    val partnerInvite: PartnerInvite? = null,
    // Invites this user issued, and care other people have shared with them.
    // Until these existed a code could be created but never redeemed, listed or
    // revoked from any client.
    val partnerInvites: List<PartnerInviteRow> = emptyList(),
    val partnerShared: List<PartnerShare> = emptyList(),
    // Data rights, which Android could not exercise at all.
    val exporting: Boolean = false,
    val deleting: Boolean = false,
    // Set while a Care Vault file is streaming, so the sheet can show progress
    // instead of looking idle through a 20 MB upload.
    val uploadingDocument: Boolean = false,
)

/** Which piece of the care context a prompt collects. */
enum class OnboardingField { Journey, Name, Weeks, Language, Priority, Companion }

data class OnboardingPrompt(
    val field: OnboardingField,
    val question: String,
    val helper: String,
    /** Empty means a free-text answer rather than a list of choices. */
    val options: List<String> = emptyList(),
    val inputHint: String = "",
    val numeric: Boolean = false,
    val skippable: Boolean = false,
)

/**
 * The onboarding questions for a given journey.
 *
 * Dynamic because the pregnancy-week question only makes sense for someone who
 * is pregnant — asking a postpartum user "how many weeks are you?" is the same
 * category of error as the old hardcoded "Week 24".
 *
 * Name and weeks were previously never asked at all, so the app sent
 * `name = null, weeks = null` on every signup: Today could not greet anyone,
 * and the backend's week-banded pregnancy content was unreachable from Android.
 */
fun onboardingPromptsFor(journey: JourneyType?): List<OnboardingPrompt> =
    buildList {
        add(
            OnboardingPrompt(
                field = OnboardingField.Journey,
                question = "Where are you in your journey?",
                helper = "This helps Aira shape a private care context.",
                options = JourneyType.entries.map { it.label },
            ),
        )
        add(
            OnboardingPrompt(
                field = OnboardingField.Name,
                question = "What should Aira call you?",
                helper = "Only used to greet you. Skip it and Aira simply won't use a name.",
                inputHint = "Your name",
                skippable = true,
            ),
        )
        if (journey == JourneyType.Pregnant) {
            add(
                OnboardingPrompt(
                    field = OnboardingField.Weeks,
                    question = "How many weeks are you?",
                    helper = "This is what makes your Journey content match where you actually are.",
                    inputHint = "e.g. 24",
                    numeric = true,
                    skippable = true,
                ),
            )
        }
        add(
            OnboardingPrompt(
                field = OnboardingField.Language,
                question = "How should we speak with you?",
                helper = "You can change language or use voice at any time.",
                options = listOf("English", "Hindi", "Hinglish"),
            ),
        )
        add(
            OnboardingPrompt(
                field = OnboardingField.Priority,
                question = "What would feel most helpful first?",
                helper = "Aira will keep Today focused on one meaningful action.",
                options =
                    // "Understand changes" used to be subtitled "Week-by-week body
                    // and baby context", which reads as pregnancy copy to everyone.
                    listOf(
                        "Understand changes",
                        "Prepare for a visit",
                        "Feel calmer",
                        "Plan my care",
                    ),
            ),
        )
        add(
            OnboardingPrompt(
                field = OnboardingField.Companion,
                question = "How would you like Aira to be present?",
                helper = "Choose a calm interface now; this stays under your control.",
                options = listOf("Text & voice", "Talking avatar", "Chat only"),
            ),
        )
    }
