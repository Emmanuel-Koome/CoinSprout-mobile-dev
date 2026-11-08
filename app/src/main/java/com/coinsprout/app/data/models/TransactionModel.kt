package com.coinsprout.app.data.models

enum class TransactionStatus {
    PENDING_ROUNDUP,
    DEDUCTED_STK,
    ROUTED_TO_PORTFOLIO
}

data class TransactionModel(
    val id: String,
    val emoji: String,
    val merchantName: String,
    val timestamp: String,
    val originalAmount: Double,
    val roundUpAmount: Double,
    val status: TransactionStatus = TransactionStatus.PENDING_ROUNDUP
)
