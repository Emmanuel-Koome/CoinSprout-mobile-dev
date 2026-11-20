package com.coinsprout.app.domain

/**
 * Round-up rule. Must stay identical to backend/app/services/roundup.py (the server is authoritative;
 * this local copy only gives an instant, offline estimate that the server then confirms or corrects).
 *
 * M-Pesa Express only accepts whole shillings, so the purchase is first rounded UP to a whole shilling and
 * then up to the next multiple of the user's increment. The result is capped per purchase.
 */
object RoundUpCalculator {

    fun compute(amountCents: Long, incrementCents: Long, maxCents: Long): Long {
        if (amountCents <= 0 || incrementCents < 100) return 0
        val wholeKes = (amountCents + 99) / 100
        val incKes = incrementCents / 100
        val remainder = wholeKes % incKes
        if (remainder == 0L) return 0
        val roundUp = (incKes - remainder) * 100
        return minOf(roundUp, (maxCents / 100) * 100)
    }
}
