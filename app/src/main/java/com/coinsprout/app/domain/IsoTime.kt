package com.coinsprout.app.domain

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** java.time needs API 26 (minSdk is 24), so ISO-8601 UTC handling is done with SimpleDateFormat. */
object IsoTime {
    private val FRACTION = Regex("""\.\d+""")

    private fun fmt(pattern: String, tz: TimeZone = TimeZone.getTimeZone("UTC")) =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = tz; isLenient = false }

    fun format(millis: Long): String = fmt("yyyy-MM-dd'T'HH:mm:ss'Z'").format(millis)

    /** Parses "2026-11-16T04:30:00Z" and "2026-11-16T19:37:59.817494Z". Returns null if malformed. */
    fun parse(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return try {
            fmt("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(text.replace(FRACTION, ""))?.time
        } catch (_: Exception) {
            null
        }
    }

    /** "16 Nov, 7:30 AM" in Nairobi time, for display. */
    fun display(millis: Long): String = fmt("d MMM, h:mm a", TimeZone.getTimeZone("Africa/Nairobi")).format(millis)

    /** Validates a user-typed deadline such as 2027-06-30. */
    fun isValidDate(text: String): Boolean = try {
        text.length == 10 && fmt("yyyy-MM-dd").parse(text) != null
    } catch (_: Exception) {
        false
    }
}
