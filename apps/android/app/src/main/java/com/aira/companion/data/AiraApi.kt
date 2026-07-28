package com.aira.companion.data

import android.content.Context
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

    // ── identity ────────────────────────────────────────────────────────────
    private fun cachedToken(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    private fun storeToken(ctx: Context, token: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token).apply()
    }

    private suspend fun ensureToken(ctx: Context): String {
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
    val locked: Boolean,
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
