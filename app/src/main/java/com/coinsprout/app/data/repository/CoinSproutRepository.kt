package com.coinsprout.app.data.repository

import com.coinsprout.app.data.local.CoinSproutDatabase
import com.coinsprout.app.data.local.GoalEntity
import com.coinsprout.app.data.local.NotificationEntity
import com.coinsprout.app.data.local.TransactionEntity
import com.coinsprout.app.data.models.*
import com.coinsprout.app.data.prefs.SessionStore
import com.coinsprout.app.data.remote.*
import com.coinsprout.app.domain.IsoTime
import com.coinsprout.app.domain.MpesaKind
import com.coinsprout.app.domain.ParsedMpesa
import com.coinsprout.app.domain.RoundUpCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class RecordOutcome(val roundUpCents: Long, val thresholdHit: Boolean, val duplicate: Boolean)

/**
 * Offline-first: every write lands in Room immediately (so an SMS received with no signal is never lost) and is
 * pushed to the server later by [syncAll]. The server's numbers are authoritative and overwrite local estimates.
 */
class CoinSproutRepository(
    private val db: CoinSproutDatabase,
    private val api: ApiService,
    private val session: SessionStore
) {
    private val txDao = db.transactionDao()
    private val goalDao = db.goalDao()
    private val notifDao = db.notificationDao()

    val transactions: Flow<List<TransactionModel>> = txDao.observeAll().map { rows -> rows.map { it.toModel() } }
    val pendingCents: Flow<Long> = txDao.observePendingCents()
    val goals: Flow<List<GoalModel>> = goalDao.observeAll().map { rows -> rows.map { GoalModel(it.id, it.title, it.emoji, it.targetCents, it.savedCents, it.deadline) } }
    val notifications: Flow<List<NotificationModel>> = notifDao.observeAll().map { rows -> rows.map { NotificationModel(it.id, it.title, it.message, it.createdAtMillis, it.isRead) } }
    val settings: StateFlow<UserSettingsModel> = session.settings
    val portfolio: StateFlow<PortfolioSnapshot> = session.portfolio
    val loggedIn: StateFlow<Boolean> = session.loggedIn

    // ---------------------------------------------------------------- auth
    suspend fun requestOtp(phone: String) = apiCall { api.requestOtp(OtpRequestBody(phone)) }

    suspend fun verifyOtp(phone: String, otp: String): ApiResult<TokenPairDto> {
        val result = apiCall { api.verifyOtp(OtpVerifyBody(phone, otp)) }
        if (result is ApiResult.Ok) session.saveTokens(result.value.accessToken, result.value.refreshToken)
        return result
    }

    suspend fun logout() {
        session.refreshToken?.let { token -> apiCall { api.logout(RefreshBody(token)) } }
        wipeLocal()
    }

    /** Right to erasure: server first, then everything on the device. */
    suspend fun deleteAccount(): ApiResult<Unit> {
        val result = apiCall { api.deleteAccount() }
        if (result is ApiResult.Ok) wipeLocal()
        return result
    }

    private suspend fun wipeLocal() {
        txDao.clear(); goalDao.clear(); notifDao.clear(); session.clearAll()
    }

    // ---------------------------------------------------------------- onboarding / profile
    suspend fun submitRiskProfile(horizon: Int, tolerance: Int, income: Int, experience: Int): ApiResult<RecommendationModel> {
        val result = apiCall { api.putRiskProfile(RiskProfileBody(horizon, tolerance, income, experience)) }
        return if (result is ApiResult.Ok) {
            val rec = result.value.toModel()
            session.savePortfolio(session.portfolio.value.copy(recommendation = rec))
            ApiResult.Ok(rec)
        } else result as ApiResult.Err
    }

    suspend fun updateSettings(new: UserSettingsModel): ApiResult<Unit> {
        session.saveSettings(new)
        session.settingsDirty = true
        return pushSettings()
    }

    private suspend fun pushSettings(): ApiResult<Unit> {
        val s = session.settings.value
        val result = apiCall {
            api.putSettings(SettingsDto(s.roundUpIncrementCents, s.maxRoundUpCents, s.dailyCapCents, s.stkThresholdCents, s.smsListenerEnabled, s.autoStkEnabled))
        }
        return when (result) {
            is ApiResult.Ok -> { session.settingsDirty = false; ApiResult.Ok(Unit) }
            is ApiResult.Err -> result
        }
    }

    // ---------------------------------------------------------------- purchases
    /** Called by the SMS receiver (no network needed). Idempotent on the M-Pesa receipt code. */
    suspend fun recordPurchase(p: ParsedMpesa): RecordOutcome {
        val s = session.settings.value
        val eligible = p.kind == MpesaKind.PAID || p.kind == MpesaKind.AIRTIME
        val roundUp = if (eligible) RoundUpCalculator.compute(p.amountCents, s.roundUpIncrementCents, s.maxRoundUpCents) else 0L
        val entity = TransactionEntity(
            mpesaCode = p.code, merchantName = p.merchant, kind = p.kind.name, amountCents = p.amountCents, roundUpCents = roundUp,
            status = if (roundUp > 0) TxStatus.PENDING_ROUNDUP.name else TxStatus.SKIPPED.name,
            occurredAtMillis = p.occurredAtMillis, synced = false
        )
        if (txDao.insertIgnore(entity) == -1L) return RecordOutcome(0, false, duplicate = true)
        return RecordOutcome(roundUp, thresholdHit = false, duplicate = false)
    }

    /** Manual entry (the only capture method in the Play build). */
    suspend fun addManualPurchase(merchant: String, amountCents: Long): RecordOutcome {
        val code = "MAN" + UUID.randomUUID().toString().replace("-", "").take(7).uppercase()
        return recordPurchase(ParsedMpesa(code, MpesaKind.PAID, merchant.trim(), amountCents, System.currentTimeMillis()))
    }

    suspend fun deletePurchaseLocally(code: String) = txDao.delete(code)

    // ---------------------------------------------------------------- sync
    /** Pushes unsynced purchases and settings, then refreshes everything from the server. */
    suspend fun syncAll(): ApiResult<Unit> {
        if (!session.isLoggedIn) return ApiResult.Ok(Unit)
        pushUnsynced().let { if (it is ApiResult.Err) return it }
        if (session.settingsDirty) pushSettings().let { if (it is ApiResult.Err) return it }
        else pullSettings()
        pullPortfolio().let { if (it is ApiResult.Err) return it }
        pullTransactions()
        pullGoals()
        pullNotifications()
        return ApiResult.Ok(Unit)
    }

    private suspend fun pushUnsynced(): ApiResult<Unit> {
        for (e in txDao.unsynced()) {
            val result = apiCall { api.postTransaction(TransactionBody(e.mpesaCode, e.merchantName, e.kind, e.amountCents, IsoTime.format(e.occurredAtMillis))) }
            when (result) {
                is ApiResult.Ok -> txDao.upsert(result.value.transaction.toEntity())
                is ApiResult.Err -> when {
                    result.offline || result.code == 401 || result.code == 429 || (result.code ?: 0) >= 500 -> return result   // retry later
                    else -> txDao.upsert(e.copy(roundUpCents = 0, status = TxStatus.SKIPPED.name, synced = true))              // permanently rejected
                }
            }
        }
        return ApiResult.Ok(Unit)
    }

    private suspend fun pullSettings() {
        val r = apiCall { api.getSettings() }
        if (r is ApiResult.Ok) with(r.value) {
            session.saveSettings(UserSettingsModel(roundUpIncrementCents, maxRoundUpCents, dailyCapCents, stkThresholdCents, smsListenerEnabled, autoStkEnabled))
        }
    }

    suspend fun pullPortfolio(): ApiResult<Unit> {
        val r = apiCall { api.getPortfolio() }
        return when (r) {
            is ApiResult.Ok -> {
                val p = r.value
                session.savePortfolio(PortfolioSnapshot(p.balanceCents, p.inFlightCents, p.unallocatedCents, p.recommendation?.toModel(), IsoTime.parse(p.asOf) ?: System.currentTimeMillis()))
                ApiResult.Ok(Unit)
            }
            is ApiResult.Err -> r
        }
    }

    private suspend fun pullTransactions() {
        val r = apiCall { api.getTransactions(100) }
        if (r is ApiResult.Ok) txDao.upsertAll(r.value.items.map { it.toEntity() })
    }

    private suspend fun pullGoals() {
        val r = apiCall { api.getGoals() }
        if (r is ApiResult.Ok) goalDao.replaceAll(r.value.map { GoalEntity(it.id, it.title, it.emoji, it.targetCents, it.savedCents, it.deadline) })
    }

    private suspend fun pullNotifications() {
        val r = apiCall { api.getNotifications() }
        if (r is ApiResult.Ok) notifDao.replaceAll(r.value.map { NotificationEntity(it.id, it.title, it.message, IsoTime.parse(it.createdAt) ?: 0L, it.isRead) })
    }

    // ---------------------------------------------------------------- payments (server initiates; the phone only shows the M-Pesa prompt)
    suspend fun startSweep(idempotencyKey: String) = apiCall { api.sweep(idempotencyKey) }
    suspend fun startDeposit(amountCents: Long, idempotencyKey: String) = apiCall { api.deposit(DepositBody(amountCents), idempotencyKey) }
    suspend fun pollPayment(id: String) = apiCall { api.getStk(id) }
    suspend fun devSimulate(id: String, outcome: String) = apiCall { api.devSimulate(id, outcome) }

    // ---------------------------------------------------------------- goals
    suspend fun createGoal(title: String, emoji: String, targetCents: Long, deadline: String?): ApiResult<Unit> {
        val r = apiCall { api.createGoal(GoalBody(title, emoji, targetCents, deadline)) }
        return when (r) { is ApiResult.Ok -> { pullGoals(); ApiResult.Ok(Unit) }; is ApiResult.Err -> r }
    }

    suspend fun contribute(goalId: String, amountCents: Long): ApiResult<Unit> {
        val r = apiCall { api.contribute(goalId, ContributeBody(amountCents)) }
        return when (r) { is ApiResult.Ok -> { pullGoals(); pullPortfolio(); ApiResult.Ok(Unit) }; is ApiResult.Err -> r }
    }

    suspend fun deleteGoal(goalId: String): ApiResult<Unit> {
        val r = apiCall { api.deleteGoal(goalId) }
        return when (r) { is ApiResult.Ok -> { pullGoals(); pullPortfolio(); ApiResult.Ok(Unit) }; is ApiResult.Err -> r }
    }

    suspend fun markNotificationsRead() { apiCall { api.readAllNotifications() }; pullNotifications() }

    // ---------------------------------------------------------------- read-only
    suspend fun rates() = apiCall { api.getRates() }
    suspend fun forecast(principal: Long, monthly: Long, months: Int) = apiCall { api.forecast(ForecastBody(principal, monthly, months)) }
    suspend fun insights() = apiCall { api.insights() }
    suspend fun nearbyAgents(lat: Double, lng: Double) = apiCall { api.nearbyAgents(lat, lng) }
}

// ---- mappers
private fun TransactionEntity.toModel() = TransactionModel(
    mpesaCode, merchantName, kind, amountCents, roundUpCents,
    runCatching { TxStatus.valueOf(status) }.getOrDefault(TxStatus.SKIPPED), occurredAtMillis, synced
)

private fun TransactionDto.toEntity() = TransactionEntity(
    mpesaCode, merchantName, kind, amountCents, roundUpCents, status, IsoTime.parse(occurredAt) ?: System.currentTimeMillis(), synced = true
)

fun RecommendationDto.toModel() = RecommendationModel(
    category, Allocation(allocation.mmf, allocation.equity),
    personalized, aiLabel, reasoning.orEmpty(), confidenceScore, expectedAnnualYieldPct
)
