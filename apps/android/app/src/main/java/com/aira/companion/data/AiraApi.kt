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

    // ── transport ─────────────────────────────────────────────────────────────
    private suspend fun request(method: String, path: String, body: JSONObject?,
                                token: String?): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Content-Type", "application/json")
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
                    careTeamName = ct.optString("name").ifBlank { null },
                    careTeamPhone = ct.optString("phone").ifBlank { null },
                    emergencyContactPhone = ec.optString("phone").ifBlank { null },
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

// JSON helpers: treat a missing/null field as absent rather than 0/"".
private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) out.add(optString(i))
    return out
}
