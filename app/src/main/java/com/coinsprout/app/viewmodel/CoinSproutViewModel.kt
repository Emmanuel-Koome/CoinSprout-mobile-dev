package com.coinsprout.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coinsprout.app.data.models.*
import com.coinsprout.app.data.remote.*
import com.coinsprout.app.di.ServiceLocator
import com.coinsprout.app.domain.Money
import com.coinsprout.app.sync.SyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class CoinSproutViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.repository(app)
    private fun <T> kotlinx.coroutines.flow.Flow<T>.eager(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    // ---- single source of truth (Room + SessionStore); the UI only reads these
    val transactions = repo.transactions.eager(emptyList())
    val pendingCents = repo.pendingCents.eager(0L)
    val goals = repo.goals.eager(emptyList())
    val notifications = repo.notifications.eager(emptyList())
    val settings: StateFlow<UserSettingsModel> = repo.settings
    val portfolio: StateFlow<PortfolioSnapshot> = repo.portfolio
    val loggedIn: StateFlow<Boolean> = repo.loggedIn

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** One-shot message for a Snackbar. The UI calls [consumeMessage] after showing it. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }
    private fun say(text: String) { _message.value = text }

    fun refresh() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            val r = repo.syncAll()
            _syncing.value = false
            if (r is ApiResult.Err && r.offline) say("Offline - showing saved data")
        }
    }

    // ---------------------------------------------------------------- quiz
    private val _quiz = MutableStateFlow<Load<RecommendationModel>>(Load.Idle)
    val quiz: StateFlow<Load<RecommendationModel>> = _quiz.asStateFlow()

    fun submitQuiz(horizon: Int, tolerance: Int, income: Int) {
        _quiz.value = Load.Loading
        viewModelScope.launch {
            _quiz.value = when (val r = repo.submitRiskProfile(horizon, tolerance, income)) {
                is ApiResult.Ok -> Load.Ready(r.value)
                is ApiResult.Err -> Load.Failed(r.message)
            }
        }
    }
    fun resetQuiz() { _quiz.value = Load.Idle }

    // ---------------------------------------------------------------- settings
    fun updateSettings(new: UserSettingsModel) {
        viewModelScope.launch {
            val r = repo.updateSettings(new)
            if (r is ApiResult.Err && !r.offline) say(r.message)   // offline is fine: it syncs later
        }
    }

    // ---------------------------------------------------------------- purchases
    fun addManualPurchase(merchant: String, amountCents: Long) {
        viewModelScope.launch {
            val outcome = repo.addManualPurchase(merchant, amountCents)
            say(if (outcome.roundUpCents > 0) "Round-up of ${Money.kes(outcome.roundUpCents)} recorded" else "Purchase logged")
            SyncWorker.enqueueNow(getApplication())
            repo.syncAll()
        }
    }

    fun deletePurchase(code: String) = viewModelScope.launch { repo.deletePurchaseLocally(code) }

    // ---------------------------------------------------------------- payments
    private val _payment = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val payment: StateFlow<PaymentState> = _payment.asStateFlow()
    private var pollJob: Job? = null

    fun startSweep() = startPayment { repo.startSweep(UUID.randomUUID().toString()) }
    fun startDeposit(amountCents: Long) = startPayment { repo.startDeposit(amountCents, UUID.randomUUID().toString()) }
    fun resetPayment() { pollJob?.cancel(); _payment.value = PaymentState.Idle }

    private fun startPayment(call: suspend () -> ApiResult<StkDto>) {
        if (_payment.value is PaymentState.Waiting) return
        viewModelScope.launch {
            when (val r = call()) {
                is ApiResult.Ok -> beginPolling(r.value)
                is ApiResult.Err -> _payment.value = PaymentState.Failed(r.message)
            }
        }
    }

    private fun beginPolling(stk: StkDto) {
        _payment.value = PaymentState.Waiting(stk.id, stk.amountCents)
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < deadline) {
                delay(3_000)
                val r = repo.pollPayment(stk.id)
                if (r is ApiResult.Ok && r.value.status != "PENDING") {
                    repo.syncAll()
                    _payment.value = when (r.value.status) {
                        "SUCCESS" -> PaymentState.Success(stk.amountCents)
                        "CANCELLED" -> PaymentState.Failed("The M-Pesa prompt was cancelled. Nothing was charged.")
                        "MISMATCH" -> PaymentState.Failed("The amount did not match, so we held it for review. Nothing was invested.")
                        else -> PaymentState.Failed(r.value.resultDesc ?: "The payment did not complete. Nothing was charged.")
                    }
                    return@launch
                }
            }
            _payment.value = PaymentState.Failed("No answer from M-Pesa yet. If you approved it, your balance updates once it is confirmed.")
        }
    }

    /** Debug builds against the dev backend only: stands in for the customer pressing OK on the M-Pesa prompt. */
    fun devSimulate(outcome: String) {
        val waiting = _payment.value as? PaymentState.Waiting ?: return
        viewModelScope.launch { repo.devSimulate(waiting.stkId, outcome) }
    }

    // ---------------------------------------------------------------- goals
    fun createGoal(title: String, targetCents: Long, deadline: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.createGoal(title, "🎯", targetCents, deadline)) {
                is ApiResult.Ok -> onDone()
                is ApiResult.Err -> say(r.message)
            }
        }
    }

    fun contribute(goalId: String, cents: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.contribute(goalId, cents)) {
                is ApiResult.Ok -> onDone()
                is ApiResult.Err -> say(r.message)
            }
        }
    }

    fun deleteGoal(goalId: String) = viewModelScope.launch { (repo.deleteGoal(goalId) as? ApiResult.Err)?.let { say(it.message) } }

    fun markNotificationsRead() = viewModelScope.launch { repo.markNotificationsRead() }

    // ---------------------------------------------------------------- read-only data
    private val _rates = MutableStateFlow<Load<List<RateDto>>>(Load.Idle)
    val rates: StateFlow<Load<List<RateDto>>> = _rates.asStateFlow()
    fun loadRates() {
        _rates.value = Load.Loading
        viewModelScope.launch { _rates.value = when (val r = repo.rates()) { is ApiResult.Ok -> Load.Ready(r.value); is ApiResult.Err -> Load.Failed(r.message) } }
    }

    private val _forecast = MutableStateFlow<Load<ForecastDto>>(Load.Idle)
    val forecast: StateFlow<Load<ForecastDto>> = _forecast.asStateFlow()
    fun loadForecast(months: Int) {
        _forecast.value = Load.Loading
        viewModelScope.launch {
            val base = portfolio.value.balanceCents + pendingCents.value
            val principal = if (base > 0) base else EXAMPLE_PRINCIPAL_CENTS   // new users see a clearly-labelled example
            _forecast.value = when (val r = repo.forecast(principal, 0, months)) { is ApiResult.Ok -> Load.Ready(r.value); is ApiResult.Err -> Load.Failed(r.message) }
        }
    }

    private val _insights = MutableStateFlow<Load<List<InsightDto>>>(Load.Idle)
    val insights: StateFlow<Load<List<InsightDto>>> = _insights.asStateFlow()
    fun loadInsights() {
        _insights.value = Load.Loading
        viewModelScope.launch { _insights.value = when (val r = repo.insights()) { is ApiResult.Ok -> Load.Ready(r.value); is ApiResult.Err -> Load.Failed(r.message) } }
    }

    private val _agents = MutableStateFlow<Load<List<AgentDto>>>(Load.Idle)
    val agents: StateFlow<Load<List<AgentDto>>> = _agents.asStateFlow()
    fun loadAgents(lat: Double, lng: Double) {
        _agents.value = Load.Loading
        viewModelScope.launch { _agents.value = when (val r = repo.nearbyAgents(lat, lng)) { is ApiResult.Ok -> Load.Ready(r.value); is ApiResult.Err -> Load.Failed(r.message) } }
    }
    fun agentsUnavailable(reason: String) { _agents.value = Load.Failed(reason) }

    // ---------------------------------------------------------------- account
    fun logout() { viewModelScope.launch { repo.logout() } }
    fun deleteAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.deleteAccount()) { is ApiResult.Ok -> onDone(); is ApiResult.Err -> say(r.message) }
        }
    }

    companion object {
        const val EXAMPLE_PRINCIPAL_CENTS = 1_000_000L   // KES 10,000
    }
}
