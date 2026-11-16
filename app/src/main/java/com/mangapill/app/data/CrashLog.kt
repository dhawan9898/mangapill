package com.mangapill.app.data

import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Keeps the last crash on the device. The app has no crash reporting service, so without this a
 * crash leaves nothing behind to diagnose; Settings offers the saved report for copying.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"
    private const val MAX_CHARS = 20_000

    fun install(filesDir: File, version: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Writing the report must never get in the way of the crash itself.
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(filesDir, FILE).writeText(
                    "Mangapill $version, Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})\n" +
                        "${Date()} on thread ${thread.name}\n\n" + trace.take(MAX_CHARS)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(filesDir: File): String? =
        runCatching { File(filesDir, FILE).takeIf { it.isFile }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(filesDir: File) {
        runCatching { File(filesDir, FILE).delete() }
    }
}
