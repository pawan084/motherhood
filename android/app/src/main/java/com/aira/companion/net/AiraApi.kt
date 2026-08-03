package com.aira.companion.net

import android.content.Context
import com.aira.companion.BuildConfig
import com.aira.companion.model.CareSummary
import com.aira.companion.model.MoodEntry
import com.aira.companion.model.Reminder
import com.aira.companion.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal client for the Aira backend — deliberately zero new dependencies
 * (HttpURLConnection + org.json), matching the prototype's lean setup. The
 * web client (web/src/api.ts) is the reference implementation.
 *
 * Identity: a guest device token minted once via POST /device/register and
 * persisted in SharedPreferences, sent as X-Device-Token on every learner
 * call. TODO: move to EncryptedSharedPreferences before any release build.
 */
object AiraApi {
    private const val PREFS = "aira_net"
    private const val KEY_TOKEN = "device_token"

    private val base: String get() = BuildConfig.AIRA_BACKEND_URL.trimEnd('/')

    class ApiException(message: String) : Exception(message)

    /** One chat turn's outcome — mirrors the backend's /respond contract. */
    data class Turn(
        val decision: String,          // ok | caution | urgent | error
        val reply: String?,
        val safetyLabel: String?,
        val urgentHeadline: String?,
        val urgentBody: String?,
        val message: String?,          // error copy when decision == error
        val cardTitles: List<String>,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun request(
        method: String,
        path: String,
        token: String?,
        body: JSONObject?,
    ): JSONObject {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Content-Type", "application/json")
        if (token != null) conn.setRequestProperty("X-Device-Token", token)
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        if (code >= 400) throw ApiException("HTTP $code: ${text.take(200)}")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    /** The persisted device token, minting one on first use. */
    suspend fun ensureDeviceToken(context: Context): String = withContext(Dispatchers.IO) {
        prefs(context).getString(KEY_TOKEN, null) ?: run {
            val minted = request("POST", "/device/register", null, JSONObject())
                .getString("device_token")
            prefs(context).edit().putString(KEY_TOKEN, minted).apply()
            minted
        }
    }

    suspend fun putCareContext(
        context: Context,
        stage: String,
        dueDate: String? = null,
        birthDate: String? = null,
        displayName: String? = null,
        language: String? = null,
    ) = withContext(Dispatchers.IO) {
        val token = ensureDeviceToken(context)
        val body = JSONObject().apply {
            put("stage", stage)
            dueDate?.let { put("due_date", it) }
            birthDate?.let { put("birth_date", it) }
            displayName?.let { put("display_name", it) }
            language?.let { put("language", it) }
        }
        request("PUT", "/care-context", token, body)
    }

    // ── Wellness + care context (P11 part 2) ────────────────────────────────
    // All list responses are enveloped ({"reminders": [...]}), matching the
    // backend convention, so request() stays JSONObject-only.

    suspend fun getCareContext(context: Context): CareSummary? = withContext(Dispatchers.IO) {
        val token = ensureDeviceToken(context)
        val ctx = request("GET", "/care-context", token, null).optJSONObject("context")
            ?: return@withContext null
        CareSummary(
            stage = ctx.optString("stage"),
            week = if (ctx.isNull("week")) null else ctx.getInt("week"),
            displayName = ctx.optString("display_name"),
            language = ctx.optString("language", "en"),
        )
    }

    suspend fun postMood(context: Context, mood: String, note: String? = null) =
        withContext(Dispatchers.IO) {
            val token = ensureDeviceToken(context)
            val body = JSONObject().put("mood", mood)
            note?.let { body.put("note", it) }
            request("POST", "/moods", token, body)
        }

    suspend fun getMoods(context: Context, days: Int = 7): List<MoodEntry> =
        withContext(Dispatchers.IO) {
            val token = ensureDeviceToken(context)
            val arr = request("GET", "/moods?days=$days", token, null).optJSONArray("moods")
                ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { MoodEntry(it.getString("day"), it.getString("mood")) }
            }
        }

    private fun parseReminder(json: JSONObject) = Reminder(
        id = json.getString("id"),
        title = json.getString("title"),
        kind = json.getString("kind"),
        targetPerDay = json.optInt("target_per_day", 1),
        ticksToday = json.optInt("ticks_today", 0),
        doneToday = json.optBoolean("done_today", false),
    )

    suspend fun getReminders(context: Context): List<Reminder> = withContext(Dispatchers.IO) {
        val token = ensureDeviceToken(context)
        val arr = request("GET", "/reminders?include_medicines=true", token, null)
            .optJSONArray("reminders") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseReminder) }
    }

    suspend fun tickReminder(context: Context, id: String) = withContext(Dispatchers.IO) {
        request("POST", "/reminders/$id/tick", ensureDeviceToken(context), JSONObject())
    }

    suspend fun markMedicineTaken(context: Context, id: String) = withContext(Dispatchers.IO) {
        request("POST", "/medicines/$id/taken", ensureDeviceToken(context), JSONObject())
    }

    private fun parseVideo(json: JSONObject) = VideoItem(
        id = json.getString("id"),
        title = json.getString("title"),
        topic = json.optString("topic"),
        stage = json.optString("stage"),
        weekBand = json.optString("week_band").takeIf { it.isNotBlank() && it != "null" },
        youtubeId = json.getString("youtube_id"),
        durationMinutes = if (json.isNull("duration_minutes")) null else json.getInt("duration_minutes"),
    )

    suspend fun getVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val token = ensureDeviceToken(context)
        val arr = request("GET", "/videos", token, null).optJSONArray("videos") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseVideo) }
    }

    suspend fun getSuggestedVideo(context: Context): VideoItem? = withContext(Dispatchers.IO) {
        val token = ensureDeviceToken(context)
        request("GET", "/videos/suggested", token, null).optJSONObject("video")?.let(::parseVideo)
    }

    /** One gated chat turn. Never throws for gate/generation outcomes — those
     * arrive as decisions; only transport-level failures raise. */
    suspend fun respond(
        context: Context,
        text: String,
        history: List<Pair<Boolean, String>>, // (fromAira, text), oldest first
    ): Turn = withContext(Dispatchers.IO) {
        val token = ensureDeviceToken(context)
        val body = JSONObject().apply {
            put("text", text)
            put("history", JSONArray().apply {
                history.takeLast(20).forEach { (fromAira, content) ->
                    put(JSONObject().apply {
                        put("role", if (fromAira) "assistant" else "user")
                        put("content", content)
                    })
                }
            })
        }
        val json = request("POST", "/respond", token, body)
        val urgent = json.optJSONObject("urgent_help")
        val cards = mutableListOf<String>()
        json.optJSONArray("cards")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("title")?.takeIf { it.isNotBlank() }
                    ?.let(cards::add)
            }
        }
        Turn(
            decision = json.optString("decision", "error"),
            reply = json.optString("reply").takeIf { it.isNotBlank() },
            safetyLabel = json.optJSONObject("safety")?.optString("label"),
            urgentHeadline = urgent?.optString("headline"),
            urgentBody = urgent?.optString("body"),
            message = json.optString("message").takeIf { it.isNotBlank() },
            cardTitles = cards,
        )
    }
}
