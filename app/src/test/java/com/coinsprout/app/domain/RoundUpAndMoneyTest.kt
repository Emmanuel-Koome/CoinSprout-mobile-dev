package com.coinsprout.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoundUpAndMoneyTest {

    @Test fun roundUpToTen() = assertEquals(500L, RoundUpCalculator.compute(8500, 1000, 5000))
    @Test fun roundUpToFifty() = assertEquals(1500L, RoundUpCalculator.compute(8500, 5000, 5000))
    @Test fun exactMultipleEarnsNothing() = assertEquals(0L, RoundUpCalculator.compute(10000, 5000, 5000))
    @Test fun fractionalPurchaseRoundsUpToWholeShilling() = assertEquals(400L, RoundUpCalculator.compute(8550, 1000, 5000))
    @Test fun perPurchaseCapProtectsSmallPurchases() = assertEquals(5000L, RoundUpCalculator.compute(500, 10000, 5000))
    @Test fun nonPositiveAmountEarnsNothing() { assertEquals(0L, RoundUpCalculator.compute(0, 1000, 5000)); assertEquals(0L, RoundUpCalculator.compute(-5, 1000, 5000)) }

    @Test fun roundUpIsAlwaysWholeShillings() {
        for (a in 1L..20000L step 37) assertEquals(0L, RoundUpCalculator.compute(a, 1000, 5000) % 100)
    }

    @Test fun formatsMoney() {
        assertEquals("KES 12,400", Money.kes(1_240_000))
        assertEquals("KES 85.50", Money.kes(8550))
        assertEquals("KES 0", Money.kes(0))
        assertEquals("-KES 5", Money.kes(-500))
    }

    @Test fun parsesMoney() {
        assertEquals(125_050L, Money.parseKesToCents("1,250.50"))
        assertEquals(50_000L, Money.parseKesToCents(" 500 "))
        assertEquals(550L, Money.parseKesToCents("5.5"))
        assertNull(Money.parseKesToCents("")); assertNull(Money.parseKesToCents("abc")); assertNull(Money.parseKesToCents("-5"))
        assertNull(Money.parseKesToCents("0")); assertNull(Money.parseKesToCents("1.234")); assertNull(Money.parseKesToCents("1e5"))
    }
}
