package com.coinsprout.app.viewmodel

/** Every network-backed screen must render all four states: nothing yet, loading, data, and a human-readable error. */
sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}
