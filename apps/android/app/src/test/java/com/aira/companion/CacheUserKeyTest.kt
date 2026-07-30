package com.aira.companion

import com.aira.companion.data.AiraCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which account's cache gets read.
 *
 * The offline cache is partitioned by user id, and this function is the whole
 * partition. It is the only piece of that mechanism that can be tested without
 * a device, and it is also the piece where being wrong is worst: return the
 * wrong id and someone is shown another account's care; return a CONSTANT and
 * every account on the phone shares one folder, which is the same bug wearing a
 * disguise.
 *
 * Everything else about the cache — that files are written, that they survive a
 * cold start, that deletion removes them — was checked on a phone, because
 * those are filesystem behaviours and a mock would only prove the mock works.
 */
class CacheUserKeyTest {

    /** A token shaped like the backend's: v1.<base64url payload>.<signature>. */
    private fun token(payloadJson: String, prefix: String = "v1", sig: String = "sig"): String {
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.toByteArray())
        return "$prefix.$b64.$sig"
    }

    @Test
    fun readsTheUserIdOutOfARealToken() {
        val t = token("""{"uid": "usr_abc123", "ver": 0, "iat": 1785000000}""")
        assertEquals("usr_abc123", AiraCache.userKey(t))
    }

    @Test
    fun twoAccountsGetTwoDifferentKeys() {
        // The actual requirement, stated as a comparison: whatever the function
        // returns, it must not be the same for two people.
        val a = AiraCache.userKey(token("""{"uid": "usr_aaa"}"""))
        val b = AiraCache.userKey(token("""{"uid": "usr_bbb"}"""))
        assertEquals("usr_aaa", a)
        assertEquals("usr_bbb", b)
    }

    @Test
    fun noTokenMeansNoCache() {
        // Not a shared fallback folder. A signed-out device reads nothing rather
        // than reading whatever the last person left.
        assertNull(AiraCache.userKey(null))
        assertNull(AiraCache.userKey(""))
    }

    @Test
    fun aMalformedTokenIsNullRatherThanACrash() {
        // These arrive from storage, not from a fresh login, so a truncated or
        // half-written value has to fail quietly — the app still works online.
        assertNull(AiraCache.userKey("nonsense"))
        assertNull(AiraCache.userKey("v1."))
        assertNull(AiraCache.userKey("v1.!!!not-base64!!!.sig"))
        assertNull(AiraCache.userKey("v1.${'$'}{}.sig"))
    }

    @Test
    fun aTokenWithoutAUidIsNull() {
        // Valid base64, valid JSON, no uid. Falling back to a default folder
        // here is how every account would end up sharing one.
        assertNull(AiraCache.userKey(token("""{"ver": 0}""")))
        assertNull(AiraCache.userKey(token("""{"uid": ""}""")))
    }
}
