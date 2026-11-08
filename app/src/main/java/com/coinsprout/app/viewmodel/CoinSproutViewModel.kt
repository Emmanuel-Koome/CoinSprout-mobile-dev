package com.coinsprout.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinsprout.app.data.models.*
import com.coinsprout.app.data.repository.CoinSproutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CoinSproutViewModel(
    private val repository: CoinSproutRepository = CoinSproutRepository.instance
) : ViewModel() {

    val transactions: StateFlow<List<TransactionModel>> = repository.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<GoalModel>> = repository.goals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationModel>> = repository.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<UserSettingsModel> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettingsModel())

    val portfolioBalance: StateFlow<Double> = repository.portfolioBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 12400.0)

    val pendingRoundUpBalance: StateFlow<Double> = repository.pendingRoundUpBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 75.0)

    val riskCategory: StateFlow<String> = repository.riskCategory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Moderate")

    fun setRiskCategory(category: String) {
        repository.setRiskCategory(category)
    }

    fun triggerStkPush() {
        repository.triggerStkPush()
    }

    fun deposit(amount: Double) {
        repository.addDirectDeposit(amount)
    }

    fun simulateSmsTransaction(merchant: String, amount: Double) {
        repository.addParsedSmsTransaction(merchant, amount)
    }

    fun updateSettings(newSettings: UserSettingsModel) {
        repository.updateSettings(newSettings)
    }

    fun addGoal(title: String, targetAmount: Double, emoji: String, deadline: String) {
        repository.addGoal(title, targetAmount, emoji, deadline)
    }
}
