package com.example.deckbridge.logging

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One UTF-8 log file per process (session), under app sandbox [filesDir]/session_logs/.
 *
 * **Retention (on next [init]):** delete session files older than [MAX_AGE_MS]; then keep at most
 * [MAX_SESSION_FILES] newest by lastModified (same naming prefix).
 *
 * Logcat is unchanged; this is an additional sink for ADB / post-mortem debugging.
 */
object SessionFileLog {

    const val RELATIVE_DIR = "session_logs"
    private const val FILE_PREFIX = "deckbridge_session_"
    private const val MAX_SESSION_FILES = 5
    private const val MAX_AGE_MS = 72L * 60L * 60L * 1000L

    private val started = AtomicBoolean(false)
    private var writer: BufferedWriter? = null
    private var sessionFile: File? = null
    private var sessionId: String = ""
    private val lock = Any()

    private val utcStamp: SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    /**
     * Creates a new session file and purges old ones. Safe to call once from [Application.onCreate].
     */
    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        val dir = File(app.filesDir, RELATIVE_DIR).apply { mkdirs() }
        purgeOldSessions(dir)
        sessionId = UUID.randomUUID().toString().take(8)
        val stamp = utcStamp.format(Date())
        val f = File(dir, "${FILE_PREFIX}${stamp}_$sessionId.log")
        sessionFile = f
        writer = BufferedWriter(
            OutputStreamWriter(FileOutputStream(f, true), StandardCharsets.UTF_8),
            8192,
        )
        appendRaw("INFO", "SESSION", "Android process start sessionId=$sessionId path=${f.absolutePath}")
    }

    fun sessionIdOrDash(): String = if (sessionId.isNotEmpty()) sessionId else "—"

    fun currentFileOrNull(): File? = sessionFile

    /**
     * @param level one of VERBOSE, DEBUG, INFO, WARN, ERROR (Logcat-aligned)
     */
    fun append(level: String, category: String, message: String) {
        val w = writer ?: return
        val line = "${isoNow()} | $level | $category | ${message.trimEnd().replace("\n", " ↳ ")}"
        synchronized(lock) {
            runCatching {
                w.write(line)
                w.newLine()
            }
        }
    }

    fun flush() {
        synchronized(lock) {
            runCatching { writer?.flush() }
        }
    }

    private fun appendRaw(level: String, category: String, message: String) = append(level, category, message)

    private fun isoNow(): String = utcStamp.format(Date())

    private fun purgeOldSessions(dir: File) {
        val files = dir.listFiles { child ->
            child.isFile && child.name.startsWith(FILE_PREFIX) && child.name.endsWith(".log")
        } ?: return
        val now = System.currentTimeMillis()
        for (f in files) {
            if (now - f.lastModified() > MAX_AGE_MS) {
                f.delete()
            }
        }
        val remaining = dir.listFiles { child ->
            child.isFile && child.name.startsWith(FILE_PREFIX) && child.name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() } ?: return
        remaining.drop(MAX_SESSION_FILES).forEach { it.delete() }
    }
}
