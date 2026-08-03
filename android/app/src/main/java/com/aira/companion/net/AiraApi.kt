package com.aira.companion.net

import android.content.Context
import com.aira.companion.BuildConfig
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
