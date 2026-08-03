package com.aira.companion.net

import android.content.Context
import com.aira.companion.BuildConfig
import com.aira.companion.model.CareSummary
import com.aira.companion.model.JourneyContent
import com.aira.companion.model.Milestone
import com.aira.companion.model.ActionCard
import com.aira.companion.model.Appointment
import com.aira.companion.model.Medicine
import com.aira.companion.model.MemoryItem
import com.aira.companion.model.PlanItem
import com.aira.companion.model.MoodEntry
import com.aira.companion.model.Reminder
import com.aira.companion.model.ReminderReport
import com.aira.companion.model.ReportDay
import com.aira.companion.model.TodayFeed
import com.aira.companion.model.TodayFocus
import com.aira.companion.model.VideoItem
import com.aira.companion.model.WellnessReport
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
    private const val KEY_ONBOARDED = "onboarded"

    val baseUrl: String get() = BuildConfig.AIRA_BACKEND_URL.trimEnd('/')
    private val base: String get() = baseUrl

    class ApiException(message: String) : Exception(message)

    /** One chat turn's outcome — mirrors the backend's /respond contract. */
    data class Turn(
        val decision: String,          // ok | caution | urgent | error
        val reply: String?,
        val safetyLabel: String?,
        val urgentHeadline: String?,
        val urgentBody: String?,
        val message: String?,          // error copy when decision == error
        val cards: List<ActionCard>,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether this device has completed onboarding — lives next to the
     * device token because they answer the same question ("who is this?")
     * and must be wiped together if a sign-out/erase flow ever lands. */
    fun isOnboarded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

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
        detail = json.optString("detail").takeIf { it.isNotBlank() },
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

    suspend fun untickReminder(context: Context, id: String) = withContext(Dispatchers.IO) {
        request("POST", "/reminders/$id/untick", ensureDeviceToken(context), JSONObject())
    }

    suspend fun medicineUntaken(context: Context, id: String) = withContext(Dispatchers.IO) {
        request("POST", "/medicines/$id/untaken", ensureDeviceToken(context), JSONObject())
    }

    suspend fun setReminderTarget(context: Context, id: String, target: Int) =
        withContext(Dispatchers.IO) {
            request(
                "POST", "/reminders/$id/target", ensureDeviceToken(context),
                JSONObject().put("target_per_day", target),
            )
        }

    /** Name-only context update — PUT would need the anchor dates resent. */
    suspend fun setDisplayName(context: Context, name: String) = withContext(Dispatchers.IO) {
        request("PATCH", "/care-context", ensureDeviceToken(context),
                JSONObject().put("display_name", name))
    }

    suspend fun sendTipFeedback(context: Context, tipId: String, helpful: Boolean) =
        withContext(Dispatchers.IO) {
            request(
                "POST", "/today/tip-feedback", ensureDeviceToken(context),
                JSONObject().put("tip_id", tipId).put("helpful", helpful),
            )
        }

    suspend fun markVideoWatched(context: Context, id: String) = withContext(Dispatchers.IO) {
        request("POST", "/videos/$id/watched", ensureDeviceToken(context), JSONObject())
    }

    /** Week-flip memory, next to the token: celebrate each new week once. */
    fun lastSeenWeek(context: Context): Int? =
        prefs(context).getInt("last_seen_week", -1).takeIf { it >= 0 }

    fun setLastSeenWeek(context: Context, week: Int) {
        prefs(context).edit().putInt("last_seen_week", week).apply()
    }

    fun namePromptDismissed(context: Context): Boolean =
        prefs(context).getBoolean("name_prompt_dismissed", false)

    fun setNamePromptDismissed(context: Context) {
        prefs(context).edit().putBoolean("name_prompt_dismissed", true).apply()
    }

    suspend fun addReminder(
        context: Context,
        title: String,
        kind: String = "custom",
        targetPerDay: Int = 1,
    ) = withContext(Dispatchers.IO) {
        request(
            "POST", "/reminders", ensureDeviceToken(context),
            JSONObject().put("title", title).put("kind", kind)
                .put("target_per_day", targetPerDay),
        )
    }

    suspend fun deleteReminder(context: Context, id: String) = withContext(Dispatchers.IO) {
        request("DELETE", "/reminders/$id", ensureDeviceToken(context), null)
    }

    /** The proactive Me feed; `hour` is the DEVICE's local hour so the
     * server's timezone never decides the learner's morning. */
    suspend fun getToday(context: Context, hour: Int, date: String): TodayFeed =
        withContext(Dispatchers.IO) {
            val token = ensureDeviceToken(context)
            val json = request("GET", "/today?hour=$hour&date=$date", token, null)
            val ctx = json.optJSONObject("context")
            val recap = json.optJSONObject("recap")
            val focusArr = json.optJSONArray("focus") ?: JSONArray()
            TodayFeed(
                slot = json.optString("slot", "morning"),
                dayInWeek = ctx?.let { if (it.isNull("day_in_week")) null else it.getInt("day_in_week") },
                daysToGo = ctx?.let { if (it.isNull("days_to_go")) null else it.getInt("days_to_go") },
                tipText = json.optJSONObject("tip")?.optString("text"),
                tipId = json.optJSONObject("tip")?.optString("id"),
                focus = (0 until focusArr.length()).mapNotNull { i ->
                    focusArr.optJSONObject(i)?.let {
                        TodayFocus(
                            it.getString("kind"), it.getString("title"),
                            it.optString("body"),
                            whenTs = if (it.isNull("when_ts")) null else it.getDouble("when_ts"),
                        )
                    }
                },
                streak = ctx?.optInt("streak", 0) ?: 0,
                recapMoods = recap?.optInt("moods_logged"),
                recapWaterAvg = recap?.optDouble("water_avg"),
            )
        }

    // ── Tool sheets (P11 part 3): the same owner-scoped endpoints the web
    // client uses; every list is enveloped. ─────────────────────────────────

    suspend fun getMedicines(context: Context): List<Medicine> = withContext(Dispatchers.IO) {
        val arr = request("GET", "/medicines", ensureDeviceToken(context), null)
            .optJSONArray("medicines") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                Medicine(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    dose = it.optString("dose"),
                    timeOfDay = it.optString("time_of_day"),
                    takenToday = it.optBoolean("taken_today"),
                )
            }
        }
    }

    suspend fun addMedicine(context: Context, name: String, dose: String, timeOfDay: String) =
        withContext(Dispatchers.IO) {
            request(
                "POST", "/medicines", ensureDeviceToken(context),
                JSONObject().put("name", name).put("dose", dose)
                    .put("time_of_day", timeOfDay),
            )
        }

    suspend fun getAppointments(context: Context): List<Appointment> = withContext(Dispatchers.IO) {
        val arr = request("GET", "/appointments", ensureDeviceToken(context), null)
            .optJSONArray("appointments") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                Appointment(
                    id = it.getString("id"),
                    title = it.getString("title"),
                    whenTs = it.getDouble("when_ts"),
                    location = it.optString("location"),
                )
            }
        }
    }

    suspend fun addAppointment(context: Context, title: String, whenTs: Double, location: String) =
        withContext(Dispatchers.IO) {
            request(
                "POST", "/appointments", ensureDeviceToken(context),
                JSONObject().put("title", title).put("when_ts", whenTs)
                    .put("location", location),
            )
        }

    suspend fun getCarePlan(context: Context): List<PlanItem> = withContext(Dispatchers.IO) {
        val arr = request("GET", "/care-plan", ensureDeviceToken(context), null)
            .optJSONArray("items") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                PlanItem(it.getString("id"), it.getString("title"), it.optBoolean("done"))
            }
        }
    }

    suspend fun addPlanItem(context: Context, title: String) = withContext(Dispatchers.IO) {
        request("POST", "/care-plan", ensureDeviceToken(context), JSONObject().put("title", title))
    }

    suspend fun togglePlanItem(context: Context, id: String) = withContext(Dispatchers.IO) {
        request("POST", "/care-plan/$id/toggle", ensureDeviceToken(context), JSONObject())
    }

    suspend fun getMemory(context: Context): List<MemoryItem> = withContext(Dispatchers.IO) {
        val arr = request("GET", "/memory", ensureDeviceToken(context), null)
            .optJSONArray("items") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { MemoryItem(it.getString("id"), it.getString("content")) }
        }
    }

    suspend fun forgetMemory(context: Context, id: String) = withContext(Dispatchers.IO) {
        request("DELETE", "/memory/$id", ensureDeviceToken(context), null)
    }

    /** The weekly/monthly report source: mood series + each reminder's
     * per-day tick history (medicines included). */
    suspend fun getReport(context: Context, days: Int = 30): WellnessReport =
        withContext(Dispatchers.IO) {
            val token = ensureDeviceToken(context)
            val json = request("GET", "/report?days=$days", token, null)
            val moodsArr = json.optJSONArray("moods") ?: JSONArray()
            val moods = (0 until moodsArr.length()).mapNotNull { i ->
                moodsArr.optJSONObject(i)?.let {
                    MoodEntry(it.getString("day"), it.getString("mood"))
                }
            }
            val remArr = json.optJSONArray("reminders") ?: JSONArray()
            val reminders = (0 until remArr.length()).mapNotNull { i ->
                remArr.optJSONObject(i)?.let { r ->
                    val daysArr = r.optJSONArray("days") ?: JSONArray()
                    ReminderReport(
                        id = r.getString("id"),
                        title = r.getString("title"),
                        kind = r.getString("kind"),
                        targetPerDay = r.optInt("target_per_day", 1),
                        days = (0 until daysArr.length()).mapNotNull { j ->
                            daysArr.optJSONObject(j)?.let { d ->
                                ReportDay(d.getString("day"), d.optInt("ticks", 0),
                                          d.optBoolean("done", false))
                            }
                        },
                    )
                }
            }
            WellnessReport(json.optInt("days", days), moods, reminders)
        }

    /** Week-banded journey content; `week` pages within the caller's own
     * stage without touching the stored context. */
    suspend fun getJourney(context: Context, week: Int? = null): JourneyContent? =
        withContext(Dispatchers.IO) {
            val token = ensureDeviceToken(context)
            val path = if (week != null) "/journey?week=$week" else "/journey"
            val json = try {
                request("GET", path, token, null)
            } catch (e: ApiException) {
                // 404 = no care context yet; that's a state, not a failure.
                if (e.message?.contains("404") == true) return@withContext null
                throw e
            }
            val content = json.optJSONObject("content") ?: return@withContext null
            val size = json.optJSONObject("size")
            val msArr = json.optJSONArray("milestones") ?: JSONArray()
            JourneyContent(
                currentWeek = if (json.isNull("current_week")) null else json.getInt("current_week"),
                shownWeek = if (json.isNull("shown_week")) null else json.getInt("shown_week"),
                totalWeeks = if (json.isNull("total_weeks")) null else json.getInt("total_weeks"),
                sizeEmoji = size?.optString("emoji"),
                sizeLabel = size?.optString("label"),
                milestones = (0 until msArr.length()).mapNotNull { i ->
                    msArr.optJSONObject(i)?.let {
                        Milestone(it.getInt("week"), it.getString("label"),
                                  it.optString("emoji"))
                    }
                },
                title = content.optString("title"),
                yourself = content.optString("yourself"),
                baby = content.optString("baby"),
                prepare = content.optString("prepare"),
                disclaimer = json.optString("disclaimer"),
            )
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
        youtubeId = json.optString("youtube_id").takeIf { it.isNotBlank() && it != "null" },
        streamPath = json.optString("stream_path").takeIf { it.isNotBlank() && it != "null" },
        thumbPath = json.optString("thumb_path").takeIf { it.isNotBlank() && it != "null" },
        durationMinutes = if (json.isNull("duration_minutes")) null else json.getInt("duration_minutes"),
        likeCount = json.optInt("like_count", 0),
        myLike = json.optBoolean("my_like", false),
        avgStars = if (json.isNull("avg_stars")) null else json.getDouble("avg_stars"),
        ratingCount = json.optInt("rating_count", 0),
        myStars = if (json.isNull("my_stars")) null else json.getInt("my_stars"),
    )

    suspend fun toggleVideoLike(context: Context, id: String): Pair<Boolean, Int> =
        withContext(Dispatchers.IO) {
            val json = request("POST", "/videos/$id/like", ensureDeviceToken(context), JSONObject())
            json.getBoolean("liked") to json.getInt("like_count")
        }

    suspend fun rateVideo(context: Context, id: String, stars: Int): Triple<Int, Double, Int> =
        withContext(Dispatchers.IO) {
            val json = request(
                "POST", "/videos/$id/rate", ensureDeviceToken(context),
                JSONObject().put("stars", stars),
            )
            Triple(json.getInt("my_stars"), json.getDouble("avg_stars"),
                   json.getInt("rating_count"))
        }

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
        val cards = mutableListOf<ActionCard>()
        json.optJSONArray("cards")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val title = c.optString("title")
                if (title.isNotBlank()) {
                    cards.add(ActionCard(c.optString("type"), title, c.optString("subtitle")))
                }
            }
        }
        Turn(
            decision = json.optString("decision", "error"),
            reply = json.optString("reply").takeIf { it.isNotBlank() },
            safetyLabel = json.optJSONObject("safety")?.optString("label"),
            urgentHeadline = urgent?.optString("headline"),
            urgentBody = urgent?.optString("body"),
            message = json.optString("message").takeIf { it.isNotBlank() },
            cards = cards,
        )
    }
}
