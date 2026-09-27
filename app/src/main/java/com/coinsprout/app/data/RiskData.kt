package com.coinsprout.app.data

data class Allocation(val mmf: Int, val tbill: Int, val unit: Int, val cash: Int)

object RiskData {

    val categories = listOf("Conservative", "Moderate", "Aggressive")

    val allocations = mapOf(
        "Conservative" to Allocation(mmf = 70, tbill = 20, unit = 5, cash = 5),
        "Moderate" to Allocation(mmf = 40, tbill = 25, unit = 25, cash = 10),
        "Aggressive" to Allocation(mmf = 15, tbill = 15, unit = 60, cash = 10)
    )

    val descriptions = mapOf(
        "Conservative" to "You'd rather grow slowly and steadily. We lean heavily on Money Market Funds so your spare change stays safe and liquid.",
        "Moderate" to "You're comfortable with a little movement for a little more upside. We balance safety with steady growth funds.",
        "Aggressive" to "You're playing the long game and can ride out the dips. We tilt more of your round-ups into growth-focused funds."
    )

    // Illustrative growth multipliers for [6 months, 1 year, 5 years]
    val growth = mapOf(
        "Conservative" to listOf(1.021, 1.045, 1.28),
        "Moderate" to listOf(1.028, 1.07, 1.52),
        "Aggressive" to listOf(1.012, 1.09, 2.05)
    )

    fun scoreToCategory(score: Int): String = when {
        score <= 5 -> "Conservative"
        score <= 7 -> "Moderate"
        else -> "Aggressive"
    }
}
