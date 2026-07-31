package com.aira.companion.model

import com.aira.companion.data.ActionCard
import com.aira.companion.data.CareData
import com.aira.companion.data.CareItem
import com.aira.companion.data.ConsentFeature
import com.aira.companion.data.MemoryItem
import com.aira.companion.data.PartnerInvite
import com.aira.companion.data.PartnerInviteRow
import com.aira.companion.data.PartnerShare
import com.aira.companion.data.VoicePrefs

enum class AppStage {
    /** Resolving the cached session against the backend before showing anything,
     *  so a returning user is not flashed the Welcome screen they already passed.
     *  The system splash is held for exactly this stage — see MainActivity. */
    Starting,
    /** A three-card introduction, shown once per install and skippable. Sits
     *  before Welcome so someone can decide what Aira is before choosing how to
     *  start; a returning user never sees it again. */
    Tutorial,
    Welcome,
    /** Creating an account or signing in. Reached from Welcome and always
     *  escapable — an account is optional, so this is never a gate. */
    Auth,
    Onboarding,
    Main,
}

/** Which half of the auth screen is showing. */
enum class AuthMode { SignUp, SignIn }

enum class MainDestination(
    val label: String,
) {
    Today("Today"),
    Aira("Aira"),
    Journey("Journey"),
    Learn("Learn"),
    Care("Care"),
    You("You"),
}

/**
 * Two either side of the raised chat button.
 *
 * Six was two too many — Material tops out at five, and at a raised font scale
 * six single-line labels on a 360dp screen truncate. Cutting to three fixed
 * that and created a different problem: a raised centre button needs flankers.
 * With three slots the centre one is an empty spacer, so Today sat at one sixth
 * of the width and Care at five sixths, each pinned to an edge with a gulf
 * around the button. On a device it reads as a bar with things missing from it.
 *
 * That is a layout fact rather than a matter of taste: apps that raise a centre
 * action put two items on each side, and apps with three tabs keep them flat and
 * equal. Three slots plus a raised centre is the one combination almost nobody
 * ships.
 *
 * So the two screens that were only reachable by tapping through something else
 * come back as the flankers. Journey was behind a card on Today, and You behind
 * the header avatar — both recurring destinations that were costing two taps to
 * reach. Learn stays inside Journey: it is the one screen here whose traffic has
 * not yet earned a permanent quarter of the bar.
 */
val bottomBarDestinations: List<MainDestination> = listOf(
    MainDestination.Today,
    MainDestination.Journey,
    MainDestination.Aira,
    MainDestination.Care,
    MainDestination.You,
)

enum class JourneyType(
    val label: String,
    val supportingText: String,
) {
    Trying("Trying to conceive", "Cycle-aware wellness and preparation"),
    Pregnant("Pregnant", "Week-by-week guidance and care planning"),
    Postpartum("Postpartum", "Recovery, feeding and emotional support"),
    // Sits after Postpartum and before Exploring: in the order somebody scans,
    // it belongs with the stages rather than filed at the end as an
    // afterthought. The supporting line promises only what the app will do.
    Loss("After a loss", "Support at your own pace, asking nothing of you"),
    Exploring("Just exploring", "See how Aira can support your journey"),
}

enum class AiraTool(
    val title: String,
    val eyebrow: String,
) {
    // Eyebrows name the subject, not the machinery. "Aira tool", "Notification
    // centre" and "Conversation settings" described the app's own furniture to
    // someone who only wants to know what the sheet is for.
    CheckIn("How are you?", "Daily check-in"),
    Reminder("Create a reminder", "Reminders"),
    Medicines("Medicines", "Your routine"),
    // Eyebrows are static labels, so they must not claim a specific time or week
    // — "Tomorrow · 10:30 AM" and "Week 24 priorities" were shown to every user.
    // "Visit copilot" was product-speak: nobody arriving at a scan thinks of
    // it as piloting anything.
    Appointment("Prepare for a visit", "Appointments"),
    CareVault("Add to Care Vault", "Documents"),
    Reset("A two-minute reset", "A moment to breathe"),
    // The eyebrow was "Track, don't diagnose" — a disclaimer where the label
    // for the tool should be. The caution belongs in the sheet, next to the
    // thing being cautioned about, not as the name of the feature.
    Symptom("Log a symptom", "Symptoms & changes"),
    Movements("Count movements", "Your baby's pattern"),
    Contractions("Time contractions", "How long, how far apart"),
    CarePlan("Your care plan", "Built from your reminders"),
    Privacy("Privacy centre", "Your data"),
    Memory("What Aira remembers", "Everything it knows"),
    Voice("Language", "How Aira speaks with you"),
    Partner("Partner access", "Sharing with someone"),
    Support("Human support", "Help centre"),
    Emergency("Emergency profile", "Available offline"),
}

data class ChatMessage(
    val id: Long,
    val fromAira: Boolean,
    val text: String,
    // "wellness" | "watchful" for an Aira reply from the safety-gated backend; null otherwise.
    val trustLabel: String? = null,
    /** Unix seconds. Messages carried no time at all, so a conversation could
     *  not be placed against the day it happened — which matters once history
     *  survives a restart and yesterday's worry sits above today's. */
    val at: Double? = null,
    /** Set once the person has said whether this reply helped, so the row can
     *  stop asking. Local to the session; the server holds the feedback. */
    val rated: Boolean = false,
    /** This message never reached the server.
     *
     *  Shown on the bubble, because the alternative is a message that looks
     *  sent and is not — in an app where the thing typed may be "I've been
     *  bleeding since this morning", believing it was received is the worst
     *  outcome available. */
    val failed: Boolean = false,
    /** The backend screened this turn as raised concern and asked for the
     *  medical disclaimer to be shown with the reply.
     *
     *  The flag was parsed off the wire and then read by nothing, so an amber
     *  turn — swelling late in pregnancy, say — was presented exactly like an
     *  ordinary one. The web client had always rendered it; only Android
     *  dropped it, which is the worst shape for this kind of bug: the safety
     *  work happens, and is discarded silently at the last step. */
    val disclaimer: Boolean = false,
    /** The one next step this reply suggests, when it suggests one.
     *
     *  Parsed off the wire since the endpoint existed and read by nothing, so
     *  Aira decided "this answer should offer to log a symptom", said so, and
     *  the phone drew a paragraph. The web client had rendered it as a tappable
     *  card the whole time — the same split that hid the amber disclaimer. */
    val card: ActionCard? = null,
)

/** A short orienting line above the composer, shown only while the
 *  conversation is still empty. */
data class ChatTip(val eyebrow: String, val text: String)

/**
 * What Aira can do for you, said once, where the conversation starts.
 *
 * Two things this deliberately is not.
 *
 * It is not clinical. Nothing here tells anyone what is normal, what to worry
 * about, or when to call — that copy is clinician-authored, lives in the
 * backend's content table where an admin can correct it, and is already shown
 * on Today and Journey. A tip hardcoded in the client is a medical claim nobody
 * reviewed and nobody can edit without a release.
 *
 * It is not a proposal. Today proposes the one thing that matters; this says
 * what the conversation is for. Repeating Today's suggestion here is the exact
 * duplication that got the chat's old "Suggested for you" card removed.
 */
fun chatTipFor(journey: String?): ChatTip = when (journey?.trim()?.lowercase()) {
    "pregnant" -> ChatTip(
        "While you're expecting",
        "Aira can gather the questions worth asking before a visit, and keep " +
            "what you've noticed in one place.",
    )
    "postpartum" -> ChatTip(
        "After birth",
        "Aira can keep track of feeding, sleep and how you're doing, so you " +
            "are not holding all of it in your head.",
    )
    "trying" -> ChatTip(
        "While you're trying",
        "Aira can keep track of what you notice and help you prepare what to " +
            "ask, at whatever pace suits you.",
    )
    else -> ChatTip(
        "What Aira is for",
        "Reminders, notes and questions in one place — and a clear signal when " +
            "something is worth taking to your care team.",
    )
}

/** One of the chips above the composer. [tool] is set when tapping it should
 *  open a sheet rather than send the text as a message. */
data class QuickPrompt(val label: String, val tool: AiraTool? = null)

/**
 * The chips above the composer, for where this person actually is.
 *
 * They were three hardcoded strings — "I feel tired", "Set a reminder", "Ask
 * anything" — shown to everyone, in every journey, at every hour. "I feel
 * tired" is a reasonable opener at 34 weeks and a strange one to offer someone
 * who is trying to conceive.
 *
 * They stay deliberately few and deliberately plain. A blank composer is the
 * worst discoverability in the app, and these exist to break it — not to become
 * a menu, and not to propose actions. Today does the proposing; this screen is
 * where a conversation starts.
 *
 * Nothing here names a week, a symptom or a test. These are openers the person
 * completes, so they cannot be wrong about someone's body the way a specific
 * claim could.
 */
fun quickPromptsFor(journey: String?): List<QuickPrompt> {
    val opener = when (journey?.trim()?.lowercase()) {
        "pregnant" -> listOf(
            QuickPrompt("Is this normal?"),
            QuickPrompt("What should I ask at my next visit?"),
        )
        "postpartum" -> listOf(
            QuickPrompt("How am I healing?"),
            QuickPrompt("Feeding is hard today"),
        )
        "trying" -> listOf(
            QuickPrompt("Where do I start?"),
            QuickPrompt("What's worth tracking?"),
        )
        // Includes "exploring" and the case where Today has not loaded yet.
        // Saying nothing journey-specific beats guessing at one.
        else -> listOf(
            QuickPrompt("What can you help with?"),
            QuickPrompt("Is this normal?"),
        )
    }
    return opener + QuickPrompt("Set a reminder", AiraTool.Reminder)
}

/**
 * Which sheet an [ActionCard] opens.
 *
 * The model is asked for one of a fixed vocabulary, but it is a language model
 * and the card is only worth drawing if pressing it goes somewhere. An unknown
 * name returns null and the card is not shown, rather than rendering a control
 * that does nothing.
 */
fun toolForActionCard(tool: String): AiraTool? = when (tool.trim().lowercase()) {
    "checkin" -> AiraTool.CheckIn
    "reminder" -> AiraTool.Reminder
    "medicine", "medicines" -> AiraTool.Medicines
    "appointment" -> AiraTool.Appointment
    "upload", "document", "documents" -> AiraTool.CareVault
    "wellness", "reset" -> AiraTool.Reset
    "symptom" -> AiraTool.Symptom
    "careplan" -> AiraTool.CarePlan
    "support" -> AiraTool.Support
    "emergency" -> AiraTool.Emergency
    "partner" -> AiraTool.Partner
    "memory" -> AiraTool.Memory
    "privacy" -> AiraTool.Privacy
    else -> null
}

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
    /** The week as last reported, before the server advanced it. */
    val weeksReported: Int? = null,
    /** ISO date. When set, this is what the week is derived from — a fixed
     *  point that cannot drift, unlike a reported week carried forward. */
    val dueDate: String? = null,
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
    /** What is worth a call rather than a wait at this week. Null for anyone
     *  who is not pregnant, and for a pregnancy whose week is unknown — the
     *  signals differ by stage, so without a week there is nothing honest to
     *  say. */
    val callTip: CallTip? = null,
)

/**
 * The "when to call" line for a pregnancy week.
 *
 * [reviewed] is false while the copy is the backend's in-code seed, which was
 * drafted from the app's own red-flag list rather than written by a clinician.
 * It turns true once an admin publishes an edit. The screen shows the
 * difference: this is the strongest claim the product makes, and claiming
 * review it has not had would be the worst instance of the overclaiming this
 * codebase keeps removing.
 */
data class CallTip(val title: String, val body: String, val reviewed: Boolean)

/** API journey key -> a human label for headers/rings. */
fun journeyLabel(journey: String?): String =
    when (journey?.lowercase()) {
        "trying" -> "Trying to conceive"
        "pregnant" -> "Pregnant"
        "postpartum" -> "Postpartum"
        "loss" -> "After a loss"
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

/** An educational video topic, served by GET /v1/videos. Timing and duration are
 *  flattened from the backend's nested objects for a simpler UI model. No media
 *  is produced yet, so `playable` is false and the screen shows an "in
 *  production" state; an `urgent` topic routes to the care team, not playback. */
data class VideoTopic(
    val id: String,
    val slug: String,
    val title: String,
    val category: String,
    val categoryLabel: String,
    val journeys: List<String>,
    val timingType: String,
    val startWeek: Int?,
    val endWeek: Int?,
    val minSeconds: Int,
    val maxSeconds: Int,
    val description: String,
    val safetyLevel: String,
    val inAppActions: List<String>,
    val languages: List<String>,
    val status: String,
    val reviewStatus: String,
    /** True only when the topic is published, clinically approved AND has a
     *  [mediaUrl]. All three, because the first two are paperwork and the third
     *  is whether a file exists. */
    val playable: Boolean,
    /** Where the video is, when there is one. Null for every topic today. */
    val mediaUrl: String?,
    /** True when [mediaUrl] is the backend's stand-in rather than a produced
     *  video. The screen says so: a placeholder somebody mistakes for the real
     *  thing is worse than the honest "in production" state it replaces. */
    val mediaIsPlaceholder: Boolean,
    val saved: Boolean,
)

/** One movement-counting session. */
data class MovementSession(
    val id: String,
    val count: Int,
    val minutes: Int,
    val created: Double,
)

/** This person's own typical session — median, not mean, so one very long
 *  count does not drag the baseline they are comparing against. */
data class MovementUsual(val count: Int, val minutes: Int, val sessions: Int)

data class MovementHistory(
    val items: List<MovementSession> = emptyList(),
    /** Null until there are enough sessions to be a pattern. The screen says so
     *  rather than presenting one afternoon as "your usual". */
    val usual: MovementUsual? = null,
)

data class VideoCategory(val key: String, val label: String)

data class VideosResult(
    val items: List<VideoTopic>,
    val weekVideo: VideoTopic?,
    val categories: List<VideoCategory>,
    val savedIds: Set<String>,
)

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
    val activeTool: AiraTool? = null,
    /** The Journey section being read. The three Journey cards used to open a
     *  hardcoded list of unrelated tools — "Your baby" opened avatar settings —
     *  so they now open the section whose text they are showing. */
    val activeJourneySection: JourneySection? = null,
    /** The reminder being edited. Reminders were create-only beyond their
     *  title, which mattered little while they were inert notes and matters a
     *  lot now that they fire: a wrong time pinged you at the wrong hour and
     *  the only fix was deleting and re-creating it. */
    val editingReminder: CareItem? = null,
    /** True when the tutorial was opened from Settings rather than on first
     *  run, so finishing it returns to the app instead of to Welcome. */
    val replayingTutorial: Boolean = false,
    val toolsOpen: Boolean = false,
    val urgentHelpOpen: Boolean = false,
    // The bell's badge is DERIVED — see `updatesCount` — rather than stored.
    // It defaulted to 3, so every fresh install showed a red "3 unread" badge
    // over an empty list; the fix pinned it to 0, and nothing ever wrote to it
    // again, so it could never appear even when the list behind it had a
    // medicine due. A count kept separately from the thing it counts is wrong
    // in one direction or the other.
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
    /** The last screen load failed — almost always no connection.
     *
     *  Every loader used to swallow its exception, so an offline user got a
     *  screen that simply never filled in: no spinner, no message, nothing to
     *  press. The app looked broken rather than disconnected, which is the
     *  difference between "my phone has no signal" and "this app is broken",
     *  and only one of those is recoverable by the person holding it. */
    val loadFailed: Boolean = false,
    /** A refresh failed, but there is a cached copy on screen.
     *
     *  Distinct from [loadFailed], which means there is nothing to show at all.
     *  The two need different treatment: nothing-to-show earns the whole screen,
     *  while stale-but-real content should stay put with a line saying how old
     *  it is. Silently showing yesterday's medicines as though they were today's
     *  would be the worse failure — this app is used to answer "have I taken
     *  it?", and a confident wrong answer to that is not a smaller mistake than
     *  no answer. */
    /** A section's own fetch failed and there is nothing cached for it.
     *
     *  Separate from an empty list, and the distinction matters more here than
     *  in most apps: "Check-ins and symptoms you log will appear here" reads as
     *  "you have logged none", and showing that when the request actually
     *  failed tells someone their symptom log is empty when it is not. */
    val timelineFailed: Boolean = false,
    val documentsFailed: Boolean = false,
    /** A refresh the user asked for by pulling, as opposed to one the app
     *  started on its own. Only this kind shows the spinner — a background
     *  reload that draws one makes the screen look busy for no reason. */
    val refreshing: Boolean = false,
    val showingCached: Boolean = false,
    /** When the cached copy on screen was stored (epoch millis), so the notice
     *  can say a time rather than the word "old". */
    val cachedAt: Long? = null,
    /** Check-ins and symptom logs. Both were write-only while the tools said
     *  "Add to timeline" — saved, then never shown again. */
    val timeline: List<CareItem> = emptyList(),
    /** The Care Vault listed only a count; the files themselves were invisible. */
    val documents: List<CareItem> = emptyList(),
    val memory: List<MemoryItem> = emptyList(),
    val consent: List<ConsentFeature> = emptyList(),
    // Populated from the backend's urgent handoff / emergency profile so the
    // urgent dialer calls a REAL number instead of a hardcoded one.
    val careTeamPhone: String? = null,
    /** What is actually stored in the emergency profile, so its editor can show
     *  it. Null means not loaded (or the load failed) — which the editor has to
     *  distinguish from "loaded, and empty". */
    val emergencyProfile: Map<String, String>? = null,
    val movements: MovementHistory = MovementHistory(),
    val urgentMessage: String? = null,
    // True when only the deterministic keyword floor is screening messages. The
    // chat header claimed "Safety checked" unconditionally, which is a promise
    // about a safety system rather than decoration.
    val screeningDegraded: Boolean = false,
    /** A document being read inside the app. Null when none is open.
     *
     *  Opening one used to fire an ACTION_VIEW intent, which hands the file to
     *  another app — past the lock, past FLAG_SECURE, and into whatever cache
     *  that app keeps. It is rendered here now, and handing it over is a named
     *  action somebody chooses. */
    val openDocument: OpenDocument? = null,
    val snackbarMessage: String? = null,
    /** Set when the snackbar carries an action, e.g. "Undo" after a delete. */
    val snackbarAction: String? = null,
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
    // Accounts. `authError` is shown inline rather than as a snackbar, because a
    // failed sign-in needs to stay on screen next to the field that caused it.
    val authMode: AuthMode = AuthMode.SignUp,
    val authBusy: Boolean = false,
    val authError: String? = null,
    /** Care items on this device's anonymous session. Signing IN switches to the
     *  account's data and leaves these behind, so the screen says so first. */
    val localCareItems: Int = 0,
    val signedIn: Boolean = false,
    /** Where closing the auth screen returns to. Reached from Welcome on first
     *  run, but also from You later — someone who has been using Aira a while is
     *  exactly who wants their care preserved, so the offer can't only exist
     *  before they've used it. */
    val authReturnStage: AppStage = AppStage.Welcome,
    // Set while a Care Vault file is streaming, so the sheet can show progress
    // instead of looking idle through a 20 MB upload.
    val uploadingDocument: Boolean = false,
    // Educational video library (Learn). The server resolves journey + week, so
    // `weekVideo` is the pregnant caller's current week-by-week topic.
    val videos: List<VideoTopic> = emptyList(),
    val weekVideo: VideoTopic? = null,
    val videoCategories: List<VideoCategory> = emptyList(),
    val savedVideoIds: Set<String> = emptySet(),
    val videosLoading: Boolean = false,
)


/**
 * How many things are actually waiting in the updates list.
 *
 * Counts what needs attention — appointments, medicines due,
 * and reminders still open — so the badge and the list can never disagree.
 */
fun updatesCount(care: CareData?): Int =
    (care?.appointments?.size ?: 0) +
        (care?.medicinesDue?.size ?: 0) +
        (care?.reminders?.count { !it.done } ?: 0)

/** Which piece of the care context a prompt collects. */
enum class OnboardingField { Journey, Name, Weeks, Language, Priority }

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
                helper = "So Aira can keep what it shows you relevant to where you are.",
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
                // Not "or use voice" — spoken conversation isn't wired up and
                // the composer's mic is disabled.
                // Every other prompt says why it's being asked; this one said
                // only that the answer was reversible, which answers a
                // different question.
                helper = "Aira replies in the language you pick. You can change " +
                    "it at any time.",
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
        // The "How would you like Aira to be present?" question is gone. It
        // offered "Text & voice", "Talking avatar" and "Chat only" — two of
        // which describe features this build doesn't have — and the answer was
        // never sent anywhere: finishOnboarding posts journey, name, language,
        // priorities and weeks, and `companionPreference` was only ever echoed
        // back in the summary line. Asking someone to choose between two things
        // that don't exist and one that isn't recorded is worse than not
        // asking. Chat is the only mode, so there is nothing to choose yet.
    }

/** A downloaded document held open for reading. The file lives in the app's own
 *  cache directory; nothing else has been granted access to it. */
data class OpenDocument(
    val path: String,
    val title: String,
    val contentType: String?,
)
