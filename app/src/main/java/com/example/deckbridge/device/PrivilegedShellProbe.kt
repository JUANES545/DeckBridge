package com.example.deckbridge.device

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Best-effort: whether `su -c id` succeeds for **this app process** (e.g. KernelSU grant).
 * Cached briefly to avoid blocking IO on every USB refresh.
 */
object PrivilegedShellProbe {

    private val lock = Any()

    @Volatile
    private var cachedAtElapsed: Long = 0L

    @Volatile
    private var cachedValue: Boolean = false

    private const val CACHE_MS = 30_000L
    private const val WAIT_MS = 1_500L

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (now - cachedAtElapsed < CACHE_MS) {
                return@withContext cachedValue
            }
        }
        val ok = runSuId()
        synchronized(lock) {
            cachedAtElapsed = SystemClock.elapsedRealtime()
            cachedValue = ok
        }
        ok
    }

    /** Call after user toggles KernelSU / root grant so the next probe re-runs. */
    fun invalidateCache() {
        synchronized(lock) {
            cachedAtElapsed = 0L
        }
    }

    private fun runSuId(): Boolean {
        return try {
            val p = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val finished = p.waitFor(WAIT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                p.destroyForcibly()
                return false
            }
            if (p.exitValue() != 0) return false
            val out = ByteArrayOutputStream()
            p.inputStream.use { it.copyTo(out) }
            out.toString(Charsets.UTF_8).contains("uid=0")
        } catch (_: Throwable) {
            false
        }
    }
}
