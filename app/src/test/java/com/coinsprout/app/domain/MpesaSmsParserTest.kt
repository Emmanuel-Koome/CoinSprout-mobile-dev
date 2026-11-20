package com.coinsprout.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Samples are representative M-Pesa wordings, NOT a captured corpus - validate against your own real messages. */
class MpesaSmsParserTest {

    private val fallback = 1_700_000_000_000L

    @Test fun paidToTill() {
        val p = MpesaSmsParser.parse(
            "QJ12ABC345 Confirmed. Ksh85.00 paid to MANDAZI VENDOR. on 16/11/26 at 7:30 AM.New M-PESA balance is Ksh1,234.56. Transaction cost, Ksh0.00.", fallback)!!
        assertEquals("QJ12ABC345", p.code)
        assertEquals(MpesaKind.PAID, p.kind)
        assertEquals("MANDAZI VENDOR", p.merchant)
        assertEquals(8500L, p.amountCents)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi")).apply { timeInMillis = p.occurredAtMillis }
        assertEquals(2026, cal.get(Calendar.YEAR)); assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
        assertEquals(16, cal.get(Calendar.DAY_OF_MONTH)); assertEquals(7, cal.get(Calendar.HOUR_OF_DAY)); assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test fun thousandsSeparatorIsNotTruncated() {
        // The original regex turned "Ksh1,250.00" into 1.
        val p = MpesaSmsParser.parse("QJ12ABC346 Confirmed. Ksh1,250.00 paid to NAIVAS WESTLANDS. on 16/11/2026 at 6:45 PM. New M-PESA balance is Ksh9,000.00.", fallback)!!
        assertEquals(125_000L, p.amountCents)
        assertEquals("NAIVAS WESTLANDS", p.merchant)
    }

    @Test fun balanceIsNeverMistakenForTheAmount() {
        val p = MpesaSmsParser.parse("QJ12ABC347 Confirmed. Ksh50.50 paid to KFC.THIKA ROAD. on 1/2/26 at 1:05 PM. New M-PESA balance is Ksh99,999.00.", fallback)!!
        assertEquals(5050L, p.amountCents)
        assertEquals("KFC.THIKA ROAD", p.merchant)
    }

    @Test fun airtime() {
        val p = MpesaSmsParser.parse("QJ12ABC348 confirmed. You bought Ksh50.00 of airtime on 16/11/26 at 10:15 AM.New M-PESA balance is Ksh100.00.", fallback)!!
        assertEquals(MpesaKind.AIRTIME, p.kind); assertEquals(5000L, p.amountCents)
    }

    @Test fun payBillIsTreatedAsSpend() {
        val p = MpesaSmsParser.parse("QJ12ABC349 Confirmed. Ksh800.00 sent to KPLC PREPAID for account 14123456789 on 16/11/26 at 9:00 AM. New M-PESA balance is Ksh10.00.", fallback)!!
        assertEquals(MpesaKind.PAID, p.kind); assertEquals("KPLC PREPAID", p.merchant)
    }

    @Test fun sendToPersonDoesNotKeepThePhoneNumber() {
        val p = MpesaSmsParser.parse("QJ12ABC350 Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 16/11/26 at 3:00 PM. New M-PESA balance is Ksh10.00.", fallback)!!
        assertEquals(MpesaKind.SENT, p.kind); assertEquals("JOHN DOE", p.merchant)
    }

    @Test fun receivedMoneyIsClassifiedAsIncome() {
        val p = MpesaSmsParser.parse("QJ12ABC351 Confirmed.You have received Ksh1,000.00 from JANE DOE 0722000000 on 16/11/26 at 8:00 AM  New M-PESA balance is Ksh1,010.00.", fallback)!!
        assertEquals(MpesaKind.RECEIVED, p.kind); assertEquals(100_000L, p.amountCents); assertEquals("JANE DOE", p.merchant)
    }

    @Test fun withdrawal() {
        val p = MpesaSmsParser.parse("QJ12ABC352 Confirmed. on 16/11/26 at 8:00 AM Withdraw Ksh2,000.00 from 123456 - AGENT NAME New M-PESA balance is Ksh10.00.", fallback)!!
        assertEquals(MpesaKind.WITHDRAW, p.kind); assertEquals(200_000L, p.amountCents)
    }

    @Test fun rejectsNonTransactionMessages() {
        assertNull(MpesaSmsParser.parse("Hi, you have won Ksh1,000,000! Click http://scam.example to claim.", fallback))
        assertNull(MpesaSmsParser.parse("Failed. Ksh100.00 could not be sent. Insufficient funds.", fallback))
        assertNull(MpesaSmsParser.parse("QJ12ABC353 Confirmed. Your M-PESA balance is Ksh50.00.", fallback))   // no recognised verb
        assertNull(MpesaSmsParser.parse("", fallback))
    }

    @Test fun rejectsCodeThatIsNotTenCharacters() {
        assertNull(MpesaSmsParser.parse("QJ12 Confirmed. Ksh85.00 paid to X. on 16/11/26 at 7:30 AM.", fallback))
    }

    @Test fun fallsBackToReceiveTimeWhenDateIsMissing() {
        val p = MpesaSmsParser.parse("QJ12ABC354 Confirmed. Ksh85.00 paid to SHOP.", fallback)!!
        assertEquals(fallback, p.occurredAtMillis)
    }

    @Test fun lowercaseConfirmedAndNewlinesAreHandled() {
        assertNotNull(MpesaSmsParser.parse("QJ12ABC355 confirmed.\nKsh20.00 paid to SHOP\n. on 16/11/26 at 7:30 AM.", fallback))
    }
}
