package com.coinsprout.app.data.repository

import com.coinsprout.app.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

class CoinSproutRepository private constructor() {

    private val _transactions = MutableStateFlow<List<TransactionModel>>(
        listOf(
            TransactionModel("1", "☕", "Mandazi Vendor (Till 8502)", "Today, 7:30 AM", 85.0, 15.0, TransactionStatus.PENDING_ROUNDUP),
            TransactionModel("2", "📱", "Safaricom Airtime", "Today, 10:15 AM", 50.0, 0.0, TransactionStatus.ROUTED_TO_PORTFOLIO),
            TransactionModel("3", "🛵", "Boda Boda Ride", "Today, 1:20 PM", 60.0, 40.0, TransactionStatus.PENDING_ROUNDUP),
            TransactionModel("4", "🍲", "Mama Oliech Restaurant", "Today, 6:45 PM", 230.0, 20.0, TransactionStatus.PENDING_ROUNDUP)
        )
    )
    val transactions: StateFlow<List<TransactionModel>> = _transactions.asStateFlow()

    private val _goals = MutableStateFlow<List<GoalModel>>(
        listOf(
            GoalModel("1", "Emergency Fund", 50000.0, 18500.0, "🛡️", "Dec 2024"),
            GoalModel("2", "Govt T-Bill Investment", 100000.0, 34000.0, "🏛️", "Jun 2025"),
            GoalModel("3", "Land Deposit Fund", 250000.0, 62000.0, "🌾", "Dec 2025")
        )
    )
    val goals: StateFlow<List<GoalModel>> = _goals.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationModel>>(
        listOf(
            NotificationModel("1", "+15 KES Saved!", "Round-up added from Mandazi Vendor (85 KES -> 100 KES)", "7:31 AM"),
            NotificationModel("2", "+40 KES Saved!", "Round-up added from Boda Boda Ride (60 KES -> 100 KES)", "1:21 PM"),
            NotificationModel("3", "Threshold Reached!", "Pending round-ups hit 100 KES. STK Push sent to your M-Pesa.", "6:46 PM")
        )
    )
    val notifications: StateFlow<List<NotificationModel>> = _notifications.asStateFlow()

    private val _settings = MutableStateFlow(UserSettingsModel())
    val settings: StateFlow<UserSettingsModel> = _settings.asStateFlow()

    private val _portfolioBalance = MutableStateFlow(12400.0)
    val portfolioBalance: StateFlow<Double> = _portfolioBalance.asStateFlow()

    private val _pendingRoundUpBalance = MutableStateFlow(75.0)
    val pendingRoundUpBalance: StateFlow<Double> = _pendingRoundUpBalance.asStateFlow()

    private val _riskCategory = MutableStateFlow("Moderate")
    val riskCategory: StateFlow<String> = _riskCategory.asStateFlow()

    fun setRiskCategory(category: String) {
        _riskCategory.value = category
    }

    fun addParsedSmsTransaction(merchant: String, amount: Double): Pair<Double, Boolean> {
        val increment = _settings.value.roundUpIncrement
        val remainder = amount % increment
        val roundUp = if (remainder == 0.0) 0.0 else (increment - remainder)

        val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeString = "Today, " + timeFormatter.format(Date())

        val newTx = TransactionModel(
            id = UUID.randomUUID().toString(),
            emoji = detectEmoji(merchant),
            merchantName = merchant,
            timestamp = timeString,
            originalAmount = amount,
            roundUpAmount = roundUp,
            status = TransactionStatus.PENDING_ROUNDUP
        )

        _transactions.value = listOf(newTx) + _transactions.value
        val newPending = _pendingRoundUpBalance.value + roundUp
        _pendingRoundUpBalance.value = newPending

        val notifMsg = if (roundUp > 0) "+${roundUp.toInt()} KES saved from $merchant!" else "Purchase logged at $merchant."
        addNotification("New SMS Transaction", notifMsg)

        val thresholdHit = newPending >= _settings.value.stkThreshold
        if (thresholdHit && _settings.value.isAutoStkEnabled) {
            triggerStkPush()
        }

        return Pair(roundUp, thresholdHit)
    }

    fun triggerStkPush() {
        val pending = _pendingRoundUpBalance.value
        if (pending > 0) {
            _portfolioBalance.value += pending
            _pendingRoundUpBalance.value = 0.0
            addNotification(
                "M-Pesa STK Push Complete",
                "Successfully routed KES ${pending.toInt()} to your ${_riskCategory.value} portfolio split!"
            )
        }
    }

    fun addDirectDeposit(amount: Double) {
        _portfolioBalance.value += amount
        addNotification(
            "Direct Top-Up Successful",
            "KES ${amount.toInt()} deposited directly into your portfolio."
        )
    }

    fun updateSettings(newSettings: UserSettingsModel) {
        _settings.value = newSettings
    }

    fun addGoal(title: String, targetAmount: Double, emoji: String, deadline: String) {
        val newGoal = GoalModel(UUID.randomUUID().toString(), title, targetAmount, 0.0, emoji, deadline)
        _goals.value = _goals.value + newGoal
    }

    private fun addNotification(title: String, message: String) {
        val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val newNotif = NotificationModel(
            id = UUID.randomUUID().toString(),
            title = title,
            message = message,
            timestamp = timeFormatter.format(Date())
        )
        _notifications.value = listOf(newNotif) + _notifications.value
    }

    private fun detectEmoji(merchant: String): String {
        val lower = merchant.lowercase()
        return when {
            lower.contains("java") || lower.contains("coffee") || lower.contains("mandazi") -> "☕"
            lower.contains("boda") || lower.contains("bolt") || lower.contains("uber") || lower.contains("cab") -> "🛵"
            lower.contains("airtime") || lower.contains("safaricom") -> "📱"
            lower.contains("naivas") || lower.contains("supermarket") || lower.contains("mart") -> "🛒"
            else -> "🛍️"
        }
    }

    companion object {
        val instance: CoinSproutRepository by lazy { CoinSproutRepository() }
    }
}
