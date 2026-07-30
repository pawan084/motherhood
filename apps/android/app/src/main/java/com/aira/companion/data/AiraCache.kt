package com.aira.companion.data

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * The last good answer from each read endpoint, kept on disk.
 *
 * Until now nothing was cached, so every screen needed the network to show
 * anything at all and a failed load replaced the whole screen with a retry
 * button. That is the wrong behaviour for this app in particular: the places
 * people actually open it — a hospital corridor, a lift, a ward at 3am, a bus —
 * are the places with no signal, and "which tablet, and had I taken it?" is a
 * question whose answer we already had five minutes ago.
 *
 * Deliberately dumb. It stores the raw JSON body exactly as the server sent it,
 * keyed by path, so every parser upstream stays untouched and a cached read
 * cannot drift from a live one — they run the same code over the same bytes.
 *
 * ── Where it lives, and when it dies ──
 *
 * Under `filesDir`, which is app-private and covered by the device's
 * file-based encryption, and never on external storage. Partitioned by user id
 * so signing into a different account cannot show the previous one's care, and
 * wiped outright on sign-out and on account deletion — an export that claims
 * everything is gone while a copy of it sits in a cache file would be a lie of
 * exactly the kind this project has spent its time removing.
 */
object AiraCache {

    private const val DIR = "aira-cache"

    /** The account a cached file belongs to, read out of the session token.
     *
     *  The token is `v1.<base64url payload>.<sig>` and the payload carries the
     *  uid. Read locally rather than asked for over the network, because the
     *  entire point is to work when the network does not. Nothing here trusts
     *  the value for authorisation — it only decides which folder to read. */
    fun userKey(token: String?): String? {
        val payload = token?.split(".")?.getOrNull(1) ?: return null
        return runCatching {
            // java.util.Base64 rather than android.util.Base64: it exists on
            // every device this app supports (minSdk 26) and, unlike the
            // android one, it is real in JVM unit tests instead of a stub that
            // throws "not mocked" — so the partition can actually be tested.
            val json = String(java.util.Base64.getUrlDecoder().decode(payload))
            JSONObject(json).optString("uid").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun fileFor(ctx: Context, uid: String, path: String): File {
        val dir = File(ctx.filesDir, "$DIR/$uid").apply { mkdirs() }
        // Paths are ours, but a slash in a filename silently creates a folder,
        // so they are flattened rather than trusted.
        return File(dir, path.trim('/').replace('/', '_') + ".json")
    }

    fun write(ctx: Context, uid: String?, path: String, body: String) {
        if (uid == null) return
        runCatching { fileFor(ctx, uid, path).writeText(body) }
    }

    /** The stored body and when it was stored, or null if nothing is held. */
    fun read(ctx: Context, uid: String?, path: String): Cached? {
        if (uid == null) return null
        val f = fileFor(ctx, uid, path)
        if (!f.exists()) return null
        return runCatching { Cached(f.readText(), f.lastModified()) }.getOrNull()
    }

    /** Everything, for every account on this device. Used on sign-out and on
     *  account deletion, where leaving one file behind is the whole problem. */
    fun clearAll(ctx: Context) {
        runCatching { File(ctx.filesDir, DIR).deleteRecursively() }
    }

    data class Cached(val body: String, val savedAt: Long)
}
