package com.aira.companion.data

import android.content.Context
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Care items created with no signal, held until there is some.
 *
 * Reading offline came first; this is the other half. Adding a reminder in a
 * hospital corridor used to fail with "Aira isn't connected right now" and lose
 * what was typed — at the moment someone had just been told something they
 * wanted to remember.
 *
 * ── Only creates ──
 *
 * Not edits, not deletes, not "taken". A create is a complete statement of
 * intent that stands on its own: it needs nothing to exist on the server first,
 * and replaying it later means the same thing it meant when it was typed. The
 * others do not have that property. A delete queued against an id, replayed
 * after that item changed on another device, destroys something the user did
 * not mean; and a "taken" replayed an hour later records a dose at the wrong
 * time, which in a medicine list is worse than recording nothing. Those need a
 * timestamped, conflict-aware design, and pretending otherwise here would build
 * the harmful version of the feature.
 *
 * Each entry carries a client id generated when the user pressed Save. The
 * server dedupes on it, so a reply lost on the way back costs a retry rather
 * than a duplicate — see care._add_item.
 */
object PendingWrites {

    private const val DIR = "aira-pending"

    private fun file(ctx: Context, uid: String): File =
        File(ctx.filesDir, DIR).apply { mkdirs() }.let { File(it, "$uid.json") }

    data class Pending(val clientId: String, val path: String, val body: JSONObject) {
        /** The list this belongs to on the Care screen, for showing it before
         *  it has ever reached the server. */
        val kind: String
            get() = when {
                path.endsWith("/reminders") -> "reminder"
                path.endsWith("/medicines") -> "medicine"
                path.endsWith("/appointments") -> "appointment"
                path.endsWith("/checkin") -> "checkin"
                path.endsWith("/symptom") -> "symptom"
                else -> "unknown"
            }
    }

    fun all(ctx: Context, uid: String?): List<Pending> {
        if (uid == null) return emptyList()
        val f = file(ctx, uid)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    Pending(
                        clientId = it.optString("client_id"),
                        path = it.optString("path"),
                        body = it.optJSONObject("body") ?: JSONObject(),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Queue one create and hand back its client id. */
    fun add(ctx: Context, uid: String?, path: String, body: JSONObject): String {
        val clientId = UUID.randomUUID().toString()
        if (uid == null) return clientId
        val next = all(ctx, uid) + Pending(clientId, path, body)
        save(ctx, uid, next)
        return clientId
    }

    fun remove(ctx: Context, uid: String?, clientId: String) {
        if (uid == null) return
        save(ctx, uid, all(ctx, uid).filterNot { it.clientId == clientId })
    }

    private fun save(ctx: Context, uid: String, items: List<Pending>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("client_id", it.clientId)
                    .put("path", it.path)
                    .put("body", it.body),
            )
        }
        runCatching { file(ctx, uid).writeText(arr.toString()) }
    }

    /** Everything, for every account. Called with the cache, on sign-out and
     *  deletion — an unsent note is still the user's writing, and leaving it on
     *  a phone whose account was deleted is the same broken promise. */
    fun clearAll(ctx: Context) {
        runCatching { File(ctx.filesDir, DIR).deleteRecursively() }
    }
}
