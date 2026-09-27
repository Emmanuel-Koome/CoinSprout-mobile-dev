package com.coinsprout.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Simple shared UI state, passed down through the nav graph.
 * Stands in for a ViewModel for this mockup/prototype build.
 */
class AppState {
    var riskCategory by mutableStateOf("Moderate")
    var useAiMode by mutableStateOf(true)
    var trustRating by mutableStateOf(0)
    var principal by mutableStateOf(8450)

    fun deposit(amount: Int) {
        principal += amount
    }
}
