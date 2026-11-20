package com.coinsprout.app.data

/** Copy only. The allocation itself comes from the server (single source of truth). */
object RiskData {
    val descriptions = mapOf(
        "Conservative" to "You'd rather grow slowly and steadily. We lean heavily on Money Market Funds so your savings stay safe and liquid.",
        "Moderate" to "You're comfortable with a little movement for a little more upside. We balance safety with steady growth funds.",
        "Aggressive" to "You're playing the long game and can ride out the dips. We tilt more of your savings into growth-focused funds."
    )
}
