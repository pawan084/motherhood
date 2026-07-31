package com.aira.companion.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
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
 *
 * ── Encrypted, and what that is actually worth ──
 *
 * The bodies are written through EncryptedFile with a Keystore-held key. It is
 * worth being precise about what that buys, because "we encrypt your data" is
 * the sort of claim this codebase exists to keep honest.
 *
 * It does NOT close holes that were open. The manifest already sets
 * `allowBackup="false"` and excludes every domain from cloud backup and device
 * transfer, so the cache was never in a backup. `filesDir` is unreadable by
 * other apps, and Android's file-based encryption already protects it at rest
 * on a locked device.
 *
 * What it adds is one specific thing: a copy of `/data` taken off the device —
 * by a root shell, or from a compromised or physically extracted phone — is
 * useless, because the key lives in the Keystore and cannot leave it. For a
 * cache holding somebody's care team's number, their medicines and their
 * conversation with Aira, that is worth the dependency. It is defence in depth,
 * not a fix for something broken.
 *
 * Failure is a cache miss, never a crash. If the key is unavailable or a file
 * cannot be decrypted — a restored device, an entry from an older build — the
 * read returns null and the screen falls back to the network, which is the same
 * path it already takes when nothing is stored.
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

    /** The Keystore-held key these files are written with.
     *
     *  Deliberately not bound to biometric or device-credential auth. The app
     *  lock is a separate control somebody can turn off, and binding the cache
     *  key to it would mean offline reads failing for a reason the person never
     *  chose — on the screens whose whole purpose is working when nothing else
     *  does. */
    private fun masterKeyAlias(): String =
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private fun encrypted(ctx: Context, file: File): EncryptedFile =
        EncryptedFile.Builder(
            file, ctx, masterKeyAlias(),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

    fun write(ctx: Context, uid: String?, path: String, body: String) {
        if (uid == null) return
        runCatching {
            val file = fileFor(ctx, uid, path)
            // EncryptedFile refuses to open an existing file for writing, so a
            // rewrite has to be a replace.
            if (file.exists()) file.delete()
            encrypted(ctx, file).openFileOutput().use { it.write(body.toByteArray()) }
        }
    }

    /** The stored body and when it was stored, or null if nothing is held.
     *
     *  Null also covers "held but unreadable" — a key that is gone, or a file
     *  left by an older build. The caller treats that exactly like a cache miss
     *  and goes to the network. */
    fun read(ctx: Context, uid: String?, path: String): Cached? {
        if (uid == null) return null
        val f = fileFor(ctx, uid, path)
        if (!f.exists()) return null
        return runCatching {
            val body = encrypted(ctx, f).openFileInput().use { String(it.readBytes()) }
            Cached(body, f.lastModified())
        }.getOrElse {
            // A file we cannot read is worse than none: it will never become
            // readable, and it is still somebody's care data sitting on disk.
            runCatching { f.delete() }
            null
        }
    }

    /** Everything, for every account on this device. Used on sign-out and on
     *  account deletion, where leaving one file behind is the whole problem. */
    fun clearAll(ctx: Context) {
        runCatching { File(ctx.filesDir, DIR).deleteRecursively() }
    }

    data class Cached(val body: String, val savedAt: Long)
}
