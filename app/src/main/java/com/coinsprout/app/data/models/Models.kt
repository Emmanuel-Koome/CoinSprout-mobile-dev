package com.coinsprout.app.data.models

enum class TxStatus { PENDING_ROUNDUP, STK_REQUESTED, ROUTED_TO_PORTFOLIO, SKIPPED }

/** All money fields are integer cents (KES x 100). */
data class TransactionModel(
    val mpesaCode: String,
    val merchantName: String,
    val kind: String,
    val amountCents: Long,
    val roundUpCents: Long,
    val status: TxStatus,
    val occurredAtMillis: Long,
    val synced: Boolean
)

data class GoalModel(
    val id: String,
    val title: String,
    val emoji: String,
    val targetCents: Long,
    val savedCents: Long,
    val deadline: String?
)

data class NotificationModel(
    val id: String,
    val title: String,
    val message: String,
    val createdAtMillis: Long,
    val isRead: Boolean
)

data class UserSettingsModel(
    val roundUpIncrementCents: Long = 1000,   // KES 10
    val maxRoundUpCents: Long = 5000,         // per purchase
    val dailyCapCents: Long = 20000,
    val stkThresholdCents: Long = 10000,      // KES 100
    val smsListenerEnabled: Boolean = true,
    val autoStkEnabled: Boolean = false       // opt-in: a payment prompt should never surprise the user
)

data class Allocation(val mmf: Int, val tbill: Int, val unit: Int, val cash: Int)

data class RecommendationModel(
    val category: String,
    val allocation: Allocation,
    val personalized: Boolean,
    val aiLabel: Boolean,
    val reasoning: String,
    val confidenceScore: Double,
    val expectedAnnualYieldPct: Double
)

data class PortfolioSnapshot(
    val balanceCents: Long = 0,
    val inFlightCents: Long = 0,
    val unallocatedCents: Long = 0,
    val recommendation: RecommendationModel? = null,
    val asOfMillis: Long = 0
)

sealed interface PaymentState {
    data object Idle : PaymentState
    data class Waiting(val stkId: String, val amountCents: Long) : PaymentState
    data class Success(val amountCents: Long) : PaymentState
    data class Failed(val message: String) : PaymentState
}
