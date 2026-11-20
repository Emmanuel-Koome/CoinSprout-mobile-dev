package com.coinsprout.app.domain

import java.util.Locale

/**
 * Money is ALWAYS integer cents (KES x 100). Floating point (Double) must never hold currency:
 * 0.1 + 0.2 != 0.3, and rounding drift is unacceptable in a ledger.
 */
object Money {

    /** 1240000 -> "KES 12,400";  8550 -> "KES 85.50" */
    fun kes(cents: Long): String {
        val abs = if (cents < 0) -cents else cents
        val whole = String.format(Locale.US, "%,d", abs / 100)
        val frac = (abs % 100).toInt()
        val body = if (frac == 0) whole else "$whole.${frac.toString().padStart(2, '0')}"
        return (if (cents < 0) "-" else "") + "KES " + body
    }

    private val AMOUNT = Regex("""^\d{1,9}(\.\d{1,2})?$""")

    /** "1,250.50" -> 125050.  Returns null for blank, negative, zero, or malformed input. */
    fun parseKesToCents(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace(" ", "")
        if (!AMOUNT.matches(cleaned)) return null
        val parts = cleaned.split('.')
        val cents = parts[0].toLong() * 100 + (parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L)
        return if (cents > 0) cents else null
    }
}
