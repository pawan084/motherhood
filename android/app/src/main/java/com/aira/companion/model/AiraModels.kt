package com.aira.companion.model

enum class AppStage {
    Welcome,
    Onboarding,
    Main,
}

enum class MainDestination(
    val label: String,
) {
    Me("Me"),
    Chat("Chat"),
    Videos("Videos"),
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

/** Journey summary from GET /care-context (week computed server-side). */
data class CareSummary(
    val stage: String,
    val week: Int?,
    val displayName: String,
    val language: String,
)

data class MoodEntry(
    val day: String, // ISO yyyy-MM-dd
    val mood: String,
)

data class Reminder(
    val id: String,
    val title: String,
    val kind: String, // water | exercise | custom | medicine
    val targetPerDay: Int,
    val ticksToday: Int,
    val doneToday: Boolean,
)

data class VideoItem(
    val id: String,
    val title: String,
    val topic: String,
    val stage: String,
    val weekBand: String?,
    val youtubeId: String?,     // legacy/external entries
    val streamPath: String?,    // own hosted media, relative to the API base
    val thumbPath: String?,
    val durationMinutes: Int?,
)

val moodOptions = listOf("great", "okay", "tired", "low", "unwell")

/** Display metadata for the mood picker + history (emoji render via the
 * system emoji font). */
val moodEmoji = mapOf(
    "great" to "😊", "okay" to "🙂", "tired" to "😴",
    "low" to "😔", "unwell" to "🤒",
)

data class Milestone(val week: Int, val label: String, val emoji: String)

/** Week-banded editorial content from GET /journey, plus the journey feel:
 * per-week baby size, stage milestones, and total weeks for progress. */
data class JourneyContent(
    val currentWeek: Int?,
    val shownWeek: Int?,
    val totalWeeks: Int?,
    val sizeEmoji: String?,
    val sizeLabel: String?,
    val milestones: List<Milestone>,
    val title: String,
    val yourself: String,
    val baby: String,
    val prepare: String,
    val disclaimer: String,
)

/** Full-screen detail overlays reachable from the Me tab (same overlay
 * mechanism as Settings — deliberately not MainDestinations). */
enum class DetailPage { Journey, Moods, Care }

/** Per-day history for one reminder (GET /report). */
data class ReportDay(val day: String, val ticks: Int, val done: Boolean)

data class ReminderReport(
    val id: String,
    val title: String,
    val kind: String,
    val targetPerDay: Int,
    val days: List<ReportDay>,
)

data class WellnessReport(
    val days: Int,
    val moods: List<MoodEntry>,
    val reminders: List<ReminderReport>,
)

/** Pure optimistic-update reducers — top-level so unit tests hit them without
 * a ViewModel or coroutines (the networked paths are on-device-verified). */
fun upsertMood(moods: List<MoodEntry>, day: String, mood: String): List<MoodEntry> =
    moods.filterNot { it.day == day } + MoodEntry(day, mood)

fun applyTick(reminders: List<Reminder>, id: String): List<Reminder> =
    reminders.map { reminder ->
        if (reminder.id != id || reminder.doneToday) {
            reminder
        } else {
            val ticks = (reminder.ticksToday + 1).coerceAtMost(reminder.targetPerDay)
            reminder.copy(ticksToday = ticks, doneToday = ticks >= reminder.targetPerDay)
        }
    }

data class OnboardingAnswer(
    val question: String,
    val answer: String,
)

data class AiraUiState(
    val stage: AppStage = AppStage.Welcome,
    val destination: MainDestination = MainDestination.Chat,
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
    val chatDraft: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val snackbarMessage: String? = null,
    val careSummary: CareSummary? = null,
    val moods: List<MoodEntry> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val suggestedVideo: VideoItem? = null,
    val meLoading: Boolean = false,
    val videosLoading: Boolean = false,
    val videosLoaded: Boolean = false,
    val settingsOpen: Boolean = false,
    val detail: DetailPage? = null,
    val journeyContent: JourneyContent? = null,
    val moodHistory: List<MoodEntry> = emptyList(),
    val report: WellnessReport? = null,
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
