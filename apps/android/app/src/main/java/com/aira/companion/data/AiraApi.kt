package com.aira.companion.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.aira.companion.BuildConfig
import com.aira.companion.model.JourneyData
import com.aira.companion.model.JourneySection
import com.aira.companion.model.TodayData
import com.aira.companion.model.TodayNextAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal networking client for the Aira backend — no third-party dependencies
 * (HttpURLConnection + org.json, both in the Android platform).
 *
 * The safety gate runs on the SERVER. [chatTurn] returns a [TurnResult] whose
 * `urgent` flag and `urgentHelp` (with a REAL care-team number from the user's
 * emergency profile) tell the UI to route to the urgent-help handoff instead of
 * showing an AI reply — the piece both prototypes were missing.
 *
 * Identity is an anonymous device token minted on first use and cached in
 * SharedPreferences; every request sends it as a Bearer token.
 */
object AiraApi {
    private const val TAG = "AiraApi"
    private const val PREFS = "aira_session"
    private const val KEY_TOKEN = "session_token"
    private val base = BuildConfig.AIRA_API_BASE.trimEnd('/')

    // The backend's coarse edge gate (`X-App-Token`). Blank against a zero-config
    // dev server; REQUIRED in production, where app.py refuses to boot without
    // APP_SHARED_SECRET and every route 401s before identity is even checked.
    private val appToken = BuildConfig.AIRA_APP_TOKEN

    // Mirrors security.MAX_UPLOAD_BYTES so an oversized file is rejected before
    // it is streamed rather than after the server has read 20 MB of it.
    private const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024

    // Must match privacy.DELETE_CONFIRMATION exactly; the server 400s otherwise.
    const val DELETE_CONFIRMATION = "DELETE MY DATA"

    // ── identity ────────────────────────────────────────────────────────────
    private fun cachedToken(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    private fun storeToken(ctx: Context, token: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token).apply()
    }

    // The application context, captured the first time a call resolves a token.
    // Held so the transport can clear a dead session on a 401 without threading
    // a Context through every request signature. `applicationContext` is a
    // process singleton, so this holds no activity and leaks nothing.
    @Volatile
    private var appCtx: Context? = null

    private suspend fun ensureToken(ctx: Context): String {
        appCtx = ctx.applicationContext
        cachedToken(ctx)?.let { return it }
        val res = request("POST", "/device/register", null, token = null)
        val token = res.getString("token")
        storeToken(ctx, token)
        return token
    }

    // ── public calls ─────────────────────────────────────────────────────────
    suspend fun chatTurn(ctx: Context, message: String,
                         history: List<Pair<String, String>> = emptyList()): TurnResult {
        val token = ensureToken(ctx)
        val body = JSONObject().put("message", message).put("history",
            JSONArray().apply {
                history.forEach { (role, content) ->
                    put(JSONObject().put("role", role).put("content", content))
                }
            })
        val res = request("POST", "/v1/chat/turn", body, token)
        return TurnResult.from(res)
    }

    suspend fun onboarding(ctx: Context, journey: String, name: String?, language: String?,
                           priorities: List<String>, weeks: Int?) {
        val token = ensureToken(ctx)
        val body = JSONObject()
            .put("journey", journey)
            .put("name", name ?: JSONObject.NULL)
            .put("language", language ?: JSONObject.NULL)
            .put("priorities", JSONArray(priorities))
            .put("weeks", weeks ?: JSONObject.NULL)
        request("POST", "/v1/onboarding", body, token)
    }

    suspend fun emergencyProfile(ctx: Context): JSONObject =
        request("GET", "/v1/emergency-profile", null, ensureToken(ctx))

    /**
     * Unauthenticated liveness probe. Used to seed the chat header's trust state
     * before the first turn: with no classifier configured, screening is
     * keyword-only from the very first message, and the badge should say so
     * rather than defaulting to a reassuring "Safety checked".
     */
    suspend fun screeningDegraded(ctx: Context): Boolean {
        appCtx = ctx.applicationContext
        return !request("GET", "/health", null, null).optBoolean("llm_configured", false)
    }

    suspend fun today(ctx: Context): TodayData {
        val o = request("GET", "/v1/today", null, ensureToken(ctx))
        val na = o.optJSONObject("next_action")
        return TodayData(
            name = o.optString("name"),
            journey = o.optString("journey"),
            contextLine = o.optString("context_line"),
            weeks = o.optIntOrNull("weeks"),
            nextAction = na?.let {
                TodayNextAction(
                    tool = it.optString("tool"),
                    title = it.optString("title"),
                    detail = it.optString("detail"),
                    minutes = it.optIntOrNull("minutes"),
                )
            },
            priorities = o.optJSONArray("priorities").toStringList(),
        )
    }

    suspend fun journey(ctx: Context): JourneyData {
        val o = request("GET", "/v1/journey", null, ensureToken(ctx))
        val arr = o.optJSONArray("sections")
        val sections = mutableListOf<JourneySection>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                sections.add(JourneySection(s.optString("title"), s.optString("text")))
            }
        }
        return JourneyData(
            journey = o.optString("journey"),
            title = o.optString("title"),
            weeks = o.optIntOrNull("weeks"),
            thisWeek = o.optString("this_week"),
            body = o.optString("body"),
            sections = sections,
        )
    }

    /** The signed-in user. Used at launch to decide whether onboarding is needed. */
    suspend fun me(ctx: Context): UserProfile {
        val o = request("GET", "/account/me", null, ensureToken(ctx))
            .optJSONObject("user") ?: JSONObject()
        return UserProfile(
            id = o.optString("id"),
            name = o.optStringOrNull("name").orEmpty(),
            journey = o.optStringOrNull("journey").orEmpty(),
            language = o.optStringOrNull("language") ?: "English",
            onboarded = o.optBoolean("onboarded", false),
        )
    }

    // ── care ─────────────────────────────────────────────────────────────────

    suspend fun care(ctx: Context): CareData {
        val o = request("GET", "/v1/care", null, ensureToken(ctx))
        val plan = o.optJSONObject("care_plan") ?: JSONObject()
        return CareData(
            appointments = o.optJSONArray("appointments").toCareItems(),
            medicinesDue = o.optJSONArray("medicines_due").toCareItems(),
            reminders = o.optJSONArray("reminders").toCareItems(),
            documentsCount = o.optInt("documents_count", 0),
            planTotal = plan.optInt("total", 0),
            planOnTrack = plan.optInt("on_track", 0),
        )
    }

    suspend fun addReminder(ctx: Context, title: String, time: String?, repeat: String?) {
        request(
            "POST", "/v1/care/reminders",
            JSONObject().put("title", title)
                .put("time", time ?: JSONObject.NULL)
                .put("repeat", repeat ?: "Daily"),
            ensureToken(ctx),
        )
    }

    suspend fun addMedicine(ctx: Context, name: String, dose: String?, time: String?) {
        request(
            "POST", "/v1/care/medicines",
            JSONObject().put("name", name)
                .put("dose", dose ?: JSONObject.NULL)
                .put("time", time ?: JSONObject.NULL)
                .put("schedule", "Daily"),
            ensureToken(ctx),
        )
    }

    suspend fun markMedicineTaken(ctx: Context, id: String) {
        request("POST", "/v1/care/medicines/$id/taken", null, ensureToken(ctx))
    }

    /**
     * Complete or re-open a reminder. Toggleable, unlike a medicine dose: a dose
     * marked taken is a fact about the past, but a reminder ticked by mistake is
     * just a mistake.
     */
    suspend fun setReminderDone(ctx: Context, id: String, done: Boolean) {
        request(
            "POST", "/v1/care/reminders/$id/done",
            JSONObject().put("done", done), ensureToken(ctx),
        )
    }

    suspend fun addAppointment(ctx: Context, doctor: String, place: String?, whenText: String?) {
        request(
            "POST", "/v1/care/appointments",
            JSONObject().put("doctor", doctor)
                .put("place", place ?: JSONObject.NULL)
                .put("when", whenText ?: JSONObject.NULL),
            ensureToken(ctx),
        )
    }

    suspend fun addCheckIn(ctx: Context, feeling: String?, sleepHours: Double?, note: String?) {
        request(
            "POST", "/v1/care/checkin",
            JSONObject().put("feeling", feeling ?: JSONObject.NULL)
                .put("sleep_hours", sleepHours ?: JSONObject.NULL)
                .put("note", note ?: JSONObject.NULL),
            ensureToken(ctx),
        )
    }

    suspend fun addSymptom(ctx: Context, what: String, severity: String?, started: String?) {
        request(
            "POST", "/v1/care/symptom",
            JSONObject().put("what", what)
                .put("severity", severity ?: JSONObject.NULL)
                .put("started", started ?: JSONObject.NULL),
            ensureToken(ctx),
        )
    }

    suspend fun documents(ctx: Context): List<CareItem> =
        request("GET", "/v1/care/documents", null, ensureToken(ctx))
            .optJSONArray("items").toCareItems()

    /**
     * Upload a picked document to the Care Vault.
     *
     * This previously did not exist: the picker's result Uri was discarded and
     * "Save to Care Vault" showed a toast, so the Care screen's document count
     * stayed at 0 while telling the user their file was "saved privately".
     *
     * Streams the content Uri straight into the request body rather than reading
     * it into a ByteArray first — a 20 MB scan otherwise lands on the heap in one
     * piece. The size is checked against the server's cap as it streams, so an
     * oversized file fails before the whole thing goes over the network.
     */
    suspend fun uploadDocument(ctx: Context, uri: Uri, kind: String): Unit =
        withContext(Dispatchers.IO) {
            val resolver = ctx.contentResolver
            val name = displayName(ctx, uri) ?: "document"
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val token = ensureToken(ctx)
            val boundary = "----AiraBoundary" + java.util.UUID.randomUUID().toString().take(16)

            val conn = (URL("$base/v1/care/documents").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 60000          // uploads are slower than JSON calls
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                if (appToken.isNotBlank()) setRequestProperty("X-App-Token", appToken)
                setRequestProperty("Authorization", "Bearer $token")
                doOutput = true
                doInput = true
                setChunkedStreamingMode(0)   // don't buffer the file in memory
            }
            try {
                conn.outputStream.use { out ->
                    out.write(
                        ("--$boundary\r\n" +
                            "Content-Disposition: form-data; name=\"kind\"\r\n\r\n" +
                            "$kind\r\n" +
                            "--$boundary\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; " +
                            "filename=\"${name.replace('"', '_')}\"\r\n" +
                            "Content-Type: $mime\r\n\r\n").toByteArray(),
                    )
                    val input = resolver.openInputStream(uri)
                        ?: throw AiraApiException(0, "That file couldn't be opened.")
                    var total = 0L
                    input.use { ins ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = ins.read(buf)
                            if (n <= 0) break
                            total += n
                            if (total > MAX_UPLOAD_BYTES) {
                                throw AiraApiException(413, "That file is larger than 20 MB.")
                            }
                            out.write(buf, 0, n)
                        }
                    }
                    out.write("\r\n--$boundary--\r\n".toByteArray())
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val text = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "{}"
                    Log.w(TAG, "POST /v1/care/documents -> $code: $text")
                    throw AiraApiException(code, text)
                }
                conn.inputStream?.close()
            } finally {
                conn.disconnect()
            }
        }

    /** The picked file's human-readable name, for the Care Vault row. */
    private fun displayName(ctx: Context, uri: Uri): String? =
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.ifBlank { null } else null
                }
        }.getOrNull()

    // ── memory / consent / emergency / feedback ──────────────────────────────

    suspend fun memory(ctx: Context): List<MemoryItem> {
        val arr = request("GET", "/v1/memory", null, ensureToken(ctx)).optJSONArray("items")
        val out = mutableListOf<MemoryItem>()
        for (i in 0 until (arr?.length() ?: 0)) {
            val o = arr?.optJSONObject(i) ?: continue
            out.add(
                MemoryItem(
                    id = o.optString("id"),
                    label = o.optString("label"),
                    value = o.optString("value"),
                    approved = o.optBoolean("approved", true),
                ),
            )
        }
        return out
    }

    suspend fun setMemoryApproved(ctx: Context, id: String, approved: Boolean) {
        request("PATCH", "/v1/memory/$id", JSONObject().put("approved", approved), ensureToken(ctx))
    }

    suspend fun forgetMemory(ctx: Context, id: String) {
        request("DELETE", "/v1/memory/$id", null, ensureToken(ctx))
    }

    suspend fun consent(ctx: Context): List<ConsentFeature> {
        val arr = request("GET", "/v1/consent", null, ensureToken(ctx)).optJSONArray("features")
        val out = mutableListOf<ConsentFeature>()
        for (i in 0 until (arr?.length() ?: 0)) {
            val o = arr?.optJSONObject(i) ?: continue
            out.add(
                ConsentFeature(
                    key = o.optString("key"),
                    label = o.optString("label"),
                    granted = o.optBoolean("granted", false),
                    locked = o.optBoolean("locked", false),
                    available = o.optBoolean("available", true),
                ),
            )
        }
        return out
    }

    suspend fun setConsent(ctx: Context, feature: String, granted: Boolean) {
        request(
            "POST", "/v1/consent",
            JSONObject().put("feature", feature).put("granted", granted),
            ensureToken(ctx),
        )
    }

    suspend fun putEmergencyProfile(ctx: Context, fields: Map<String, String>) {
        val body = JSONObject()
        fields.forEach { (k, v) -> body.put(k, v.ifBlank { JSONObject.NULL }) }
        request("PUT", "/v1/emergency-profile", body, ensureToken(ctx))
    }

    suspend fun reportAnswer(ctx: Context, kind: String, message: String) {
        request(
            "POST", "/v1/feedback/report",
            JSONObject().put("kind", kind).put("message", message),
            ensureToken(ctx),
        )
    }

    // ── preferences / partner ────────────────────────────────────────────────

    suspend fun prefs(ctx: Context): VoicePrefs {
        val o = request("GET", "/v1/prefs", null, ensureToken(ctx))
        return VoicePrefs(
            voice = o.optStringOrNull("voice") ?: "Aira warm",
            spokenReplies = o.optBoolean("spoken_replies", false),
        )
    }

    /** Persists the voice choice. The backend 400s an unknown voice. */
    suspend fun setVoice(ctx: Context, voice: String) {
        request("PUT", "/v1/prefs", JSONObject().put("voice", voice), ensureToken(ctx))
    }

    /**
     * Create a real, revocable partner invite. Returns a single-use code the
     * user shares themselves — the backend deliberately sends no email or SMS,
     * so no contact detail for a third party is ever collected.
     */
    suspend fun createPartnerInvite(
        ctx: Context,
        appointments: Boolean,
        reminders: Boolean,
        healthDetails: Boolean,
    ): PartnerInvite {
        val o = request(
            "POST", "/v1/partner/invite",
            JSONObject()
                .put("appointments", appointments)
                .put("reminders", reminders)
                .put("health_details", healthDetails),
            ensureToken(ctx),
        )
        return PartnerInvite(
            id = o.optString("id"),
            code = o.optString("code"),
            shareText = o.optString("share_text"),
        )
    }

    /** Invites this user has issued, with their current state. */
    suspend fun partnerInvites(ctx: Context): List<PartnerInviteRow> {
        val arr = request("GET", "/v1/partner/invites", null, ensureToken(ctx))
            .optJSONArray("items")
        val out = mutableListOf<PartnerInviteRow>()
        for (i in 0 until (arr?.length() ?: 0)) {
            val o = arr?.optJSONObject(i) ?: continue
            val scopes = o.optJSONObject("scopes") ?: JSONObject()
            out.add(
                PartnerInviteRow(
                    id = o.optString("id"),
                    // Only present while the invite is still redeemable — the
                    // server stops echoing a spent code.
                    code = o.optStringOrNull("code"),
                    state = o.optString("state"),
                    scopeSummary = listOfNotNull(
                        "Appointments".takeIf { scopes.optBoolean("appointments") },
                        "Reminders".takeIf { scopes.optBoolean("reminders") },
                        "Health details".takeIf { scopes.optBoolean("health_details") },
                    ).joinToString(" · ").ifBlank { "Nothing shared" },
                ),
            )
        }
        return out
    }

    suspend fun revokePartnerInvite(ctx: Context, inviteId: String) {
        request("POST", "/v1/partner/invites/$inviteId/revoke", null, ensureToken(ctx))
    }

    /** Redeem a code someone shared. Returns whose care you can now see. */
    suspend fun acceptPartnerInvite(ctx: Context, code: String): String {
        val o = request(
            "POST", "/v1/partner/accept",
            JSONObject().put("code", code.trim()), ensureToken(ctx),
        )
        return o.optStringOrNull("shared_by") ?: "your partner"
    }

    /** What this user can see as somebody else's invited partner. */
    suspend fun partnerShared(ctx: Context): List<PartnerShare> {
        val arr = request("GET", "/v1/partner/shared", null, ensureToken(ctx))
            .optJSONArray("items")
        val out = mutableListOf<PartnerShare>()
        for (i in 0 until (arr?.length() ?: 0)) {
            val o = arr?.optJSONObject(i) ?: continue
            val data = o.optJSONObject("data") ?: JSONObject()
            out.add(
                PartnerShare(
                    inviteId = o.optString("invite_id"),
                    sharedBy = o.optStringOrNull("shared_by") ?: "your partner",
                    appointments = data.optJSONArray("appointments").toCareItems(),
                    reminders = data.optJSONArray("reminders").toCareItems(),
                    medicines = data.optJSONArray("medicines").toCareItems(),
                    // Only present when the `health_details` scope was granted.
                    // These were being fetched and thrown away, so granting that
                    // scope showed the partner nothing at all.
                    symptomCount = data.optIntOrNull("symptom_count"),
                    checkinCount = data.optIntOrNull("checkin_count"),
                    documentsCount = data.optIntOrNull("documents_count"),
                ),
            )
        }
        return out
    }

    // ── data rights ──────────────────────────────────────────────────────────

    /**
     * Everything Aira holds for this user, as one JSON document.
     *
     * Android had neither this nor deletion, while legal.py and the privacy page
     * both promise you can export or delete "at any time" — a promise only the
     * web client could keep.
     */
    suspend fun exportAccount(ctx: Context): String =
        request("GET", "/v1/account/export", null, ensureToken(ctx)).toString(2)

    /**
     * Irreversible. The confirmation string is required by the server so a leaked
     * token is not one request away from erasing someone's care history; it must
     * match privacy.DELETE_CONFIRMATION exactly.
     *
     * On success the caller's token is already dead, so the session is cleared
     * here rather than leaving a token that can only 401.
     */
    suspend fun deleteAccount(ctx: Context) {
        request(
            "POST", "/v1/account/delete",
            JSONObject().put("confirm", DELETE_CONFIRMATION), ensureToken(ctx),
        )
        clearSession(ctx)
    }

    /** Forget the cached device token; the next call registers a fresh user. */
    fun clearSession(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TOKEN).apply()
    }

    // ── transport ─────────────────────────────────────────────────────────────
    private suspend fun request(method: String, path: String, body: JSONObject?,
                                token: String?): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Content-Type", "application/json")
            if (appToken.isNotBlank()) setRequestProperty("X-App-Token", appToken)
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            doInput = true
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray()) }
            }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            if (code !in 200..299) {
                Log.w(TAG, "$method $path -> $code: $text")
                // A 401 means the cached token is revoked, expired, or belongs
                // to a deleted account. Without this the app kept resending the
                // dead token forever: every screen fell back to placeholder
                // content and the only cure was reinstalling — `pm clear` is
                // blocked by some OEMs, so a real user could not recover at all.
                // The web client has always dropped the token here; this brings
                // Android in line, and the next call re-registers.
                if (code == 401 && path != "/device/register") {
                    appCtx?.let { clearSession(it) }
                }
                throw AiraApiException(code, text)
            }
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}

class AiraApiException(val code: Int, message: String) : Exception(message)

/** Parsed chat-turn result. A red turn has [urgent] = true, [reply] = null. */
data class TurnResult(
    val level: String,
    /** True when the LLM classifier was unavailable and only the deterministic
     *  keyword floor ran. The user is still protected, but the chat header must
     *  say so rather than claiming full screening. */
    val degraded: Boolean,
    val urgent: Boolean,
    val reply: String?,
    val trustLabel: String?,
    val actionCard: ActionCard?,
    val disclaimerNeeded: Boolean,
    val urgentHelp: UrgentHelp?,
) {
    companion object {
        fun from(o: JSONObject): TurnResult {
            val safety = o.optJSONObject("safety") ?: JSONObject()
            val card = o.optJSONObject("action_card")?.let {
                ActionCard(it.optString("tool"), it.optString("title"), it.optString("detail"))
            }
            val uh = o.optJSONObject("urgent_help")?.let {
                val ct = it.optJSONObject("care_team") ?: JSONObject()
                val ec = it.optJSONObject("emergency_contact") ?: JSONObject()
                UrgentHelp(
                    headline = it.optString("headline"),
                    message = it.optString("message"),
                    careTeamName = ct.optStringOrNull("name"),
                    careTeamPhone = ct.optStringOrNull("phone"),
                    emergencyContactPhone = ec.optStringOrNull("phone"),
                )
            }
            return TurnResult(
                level = safety.optString("level", "green"),
                degraded = safety.optBoolean("degraded", false),
                urgent = o.optBoolean("urgent", false),
                reply = if (o.isNull("reply")) null else o.optString("reply"),
                trustLabel = if (o.isNull("trust_label")) null else o.optString("trust_label"),
                actionCard = card,
                disclaimerNeeded = o.optBoolean("disclaimer_needed", false),
                urgentHelp = uh,
            )
        }
    }
}

data class ActionCard(val tool: String, val title: String, val detail: String)

data class UrgentHelp(
    val headline: String,
    val message: String,
    val careTeamName: String?,
    val careTeamPhone: String?,   // the REAL number for the dialer, or null
    val emergencyContactPhone: String?,
)

/** The signed-in user, enough to decide whether onboarding still needs to run. */
data class UserProfile(
    val id: String,
    val name: String,
    val journey: String,
    val language: String,
    val onboarded: Boolean,
)

/**
 * One care row. The backend stores each item's payload as free-form JSON keyed
 * by kind, so the label and detail are flattened here rather than modelling six
 * near-identical shapes.
 */
data class CareItem(
    val id: String,
    val kind: String,
    val done: Boolean,
    val title: String,
    val subtitle: String,
)

data class CareData(
    val appointments: List<CareItem> = emptyList(),
    val medicinesDue: List<CareItem> = emptyList(),
    val reminders: List<CareItem> = emptyList(),
    val documentsCount: Int = 0,
    val planTotal: Int = 0,
    val planOnTrack: Int = 0,
)

data class MemoryItem(
    val id: String,
    val label: String,
    val value: String,
    val approved: Boolean,
)

data class ConsentFeature(
    val key: String,
    val label: String,
    val granted: Boolean,
    /** A permanent policy denial (health data for ads) — a statement, not a control. */
    val locked: Boolean,
    /** False when the feature doesn't exist in this build, so the consent
     *  governs nothing and the server refuses to record a grant for it. */
    val available: Boolean = true,
)

/**
 * The stored voice preference. [spokenReplies] is false in this build — there is
 * no speech synthesis behind it yet — so the UI saves the choice but says
 * plainly that it doesn't take effect until spoken replies ship.
 */
data class VoicePrefs(
    val voice: String = "Aira warm",
    val spokenReplies: Boolean = false,
)

/** A created partner invite: a single-use code, plus ready-to-share wording. */
data class PartnerInvite(
    val id: String,
    val code: String,
    val shareText: String,
)

/** An issued invite as it appears in the owner's list. */
data class PartnerInviteRow(
    val id: String,
    val code: String?,          // null once spent, revoked or expired
    val state: String,          // pending | accepted | revoked | expired
    val scopeSummary: String,
)

/**
 * Somebody else's care, as far as the scopes they granted allow.
 *
 * The three counts are null unless `health_details` was granted, and they are
 * counts by design — the server never sends the text of a symptom log or a
 * private check-in note to a partner, whatever scope is set.
 */
data class PartnerShare(
    val inviteId: String,
    val sharedBy: String,
    val appointments: List<CareItem> = emptyList(),
    val reminders: List<CareItem> = emptyList(),
    val medicines: List<CareItem> = emptyList(),
    val symptomCount: Int? = null,
    val checkinCount: Int? = null,
    val documentsCount: Int? = null,
)

/** Flatten `{id, kind, done, ...payload}` into a display row. */
private fun JSONArray?.toCareItems(): List<CareItem> {
    if (this == null) return emptyList()
    val out = ArrayList<CareItem>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        // Primary label, in the order the different kinds name their subject.
        val title = o.optStringOrNull("title")
            ?: o.optStringOrNull("name")
            ?: o.optStringOrNull("doctor")
            ?: o.optStringOrNull("what")
            ?: o.optString("kind").replaceFirstChar { it.uppercase() }
        val subtitle = listOfNotNull(
            o.optStringOrNull("dose"),
            o.optStringOrNull("place"),
            o.optStringOrNull("when"),
            o.optStringOrNull("schedule"),
            o.optStringOrNull("time"),
            o.optStringOrNull("repeat"),
            o.optStringOrNull("severity"),
        ).joinToString(" · ")
        out.add(
            CareItem(
                id = o.optString("id"),
                kind = o.optString("kind"),
                done = o.optBoolean("done", false),
                title = title,
                subtitle = subtitle,
            ),
        )
    }
    return out
}

// JSON helpers: treat a missing/null field as absent rather than 0/"".
private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

/**
 * A string field, or null when the key is missing, JSON-null, or empty.
 *
 * Do NOT use bare `optString(key).ifBlank { null }` on a nullable field:
 * org.json coerces a JSON null to the four-character string "null", which is
 * not blank. That bug made the urgent-help screen believe a care-team number
 * existed when the backend had explicitly sent `"phone": null`, so it offered
 * "Call care team" and would have dialled `tel:null` — the exact inert-button
 * failure the server-side handoff was built to eliminate.
 */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).ifBlank { null } else null

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) out.add(optString(i))
    return out
}
