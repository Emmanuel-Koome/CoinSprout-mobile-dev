package com.coinsprout.app.domain

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class MpesaKind { PAID, AIRTIME, SENT, WITHDRAW, RECEIVED }

data class ParsedMpesa(
    val code: String,            // M-Pesa receipt, e.g. QJ12ABC345 - used as the idempotency key everywhere
    val kind: MpesaKind,
    val merchant: String,        // never contains a phone number (data minimisation)
    val amountCents: Long,
    val occurredAtMillis: Long
)

/**
 * Best-effort parser for M-Pesa confirmation messages. Pure Kotlin so it can be unit-tested on the JVM.
 *
 * LIMITS (be honest about them in the report): Safaricom can change the wording at any time, the samples in the
 * unit tests are representative formats, not a captured corpus, and sender IDs can be spoofed. Therefore:
 * (1) a message is only accepted if it starts with a 10-character receipt code followed by "Confirmed";
 * (2) the server de-duplicates on that code; (3) the user can see and delete every captured item.
 */
object MpesaSmsParser {

    private val CODE = Regex("""^\s*([A-Z0-9]{10})\s+(?i:confirmed)""")
    private val AMOUNT = Regex("""(?i:Ksh|KES)\.?\s*(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{1,2}))?""")
    private val DATE = Regex("""(\d{1,2}/\d{1,2}/\d{2,4})\s+(?i:at)\s+(\d{1,2}:\d{2})\s*([AaPp][Mm])""")
    private val PAID_TO = Regex("""(?i)paid to\s+(.+?)\.?\s+on\s+\d{1,2}/\d{1,2}/\d{2,4}""")
    private val SENT_TO_ACCOUNT = Regex("""(?i)sent to\s+(.+?)\s+for account""")
    private val SENT_TO = Regex("""(?i)sent to\s+(.+?)\s+on\s+\d{1,2}/\d{1,2}/\d{2,4}""")
    private val RECEIVED_FROM = Regex("""(?i)from\s+(.+?)\s+on\s+\d{1,2}/\d{1,2}/\d{2,4}""")
    private val TRAILING_PHONE = Regex("""\s+\+?\d{9,13}$""")

    /** Returns null for anything that is not a recognised M-Pesa transaction confirmation. */
    fun parse(body: String, fallbackMillis: Long): ParsedMpesa? {
        val text = body.replace('\n', ' ').trim()
        val code = CODE.find(text)?.groupValues?.get(1) ?: return null

        // The first amount is the transaction; later ones are the new balance and the transaction cost.
        val head = text.split(Regex("""(?i)new m-?pesa balance"""), limit = 2)[0]
        val amountMatch = AMOUNT.find(head) ?: return null
        val whole = amountMatch.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        val frac = amountMatch.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
        val amountCents = whole * 100 + frac
        if (amountCents <= 0) return null

        val lower = head.lowercase(Locale.ROOT)
        val (kind, merchant) = when {
            " paid to " in lower -> MpesaKind.PAID to (PAID_TO.find(head)?.groupValues?.get(1) ?: "M-Pesa merchant")
            "airtime" in lower && "bought" in lower -> MpesaKind.AIRTIME to "Airtime"
            " sent to " in lower && " for account " in lower ->
                MpesaKind.PAID to (SENT_TO_ACCOUNT.find(head)?.groupValues?.get(1) ?: "Paybill")
            " sent to " in lower -> MpesaKind.SENT to (SENT_TO.find(head)?.groupValues?.get(1) ?: "Person")
            "you have received" in lower || " received " in lower ->
                MpesaKind.RECEIVED to (RECEIVED_FROM.find(head)?.groupValues?.get(1) ?: "Person")
            "withdraw" in lower -> MpesaKind.WITHDRAW to "Agent withdrawal"
            else -> return null
        }
        val cleanMerchant = merchant.replace(TRAILING_PHONE, "").trim().trimEnd('.').ifBlank { "M-Pesa merchant" }.take(120)
        return ParsedMpesa(code, kind, cleanMerchant, amountCents, parseDate(head) ?: fallbackMillis)
    }

    private fun parseDate(text: String): Long? {
        val m = DATE.find(text) ?: return null
        return try {
            val fmt = SimpleDateFormat("d/M/yy h:mm a", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("Africa/Nairobi")
                isLenient = false
            }
            fmt.parse("${m.groupValues[1]} ${m.groupValues[2]} ${m.groupValues[3].uppercase(Locale.ROOT)}")?.time
        } catch (_: Exception) {
            null
        }
    }
}
