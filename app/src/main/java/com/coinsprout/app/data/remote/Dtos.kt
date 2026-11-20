package com.coinsprout.app.data.remote

// JSON contracts - field names intentionally match the FastAPI backend (camelCase). Money is integer cents.

data class OtpRequestBody(val phone: String)
data class OtpRequestedDto(val status: String?, val expiresInSeconds: Int, val devOtp: String?)
data class OtpVerifyBody(val phone: String, val otp: String)
data class RefreshBody(val refreshToken: String)
data class TokenPairDto(val accessToken: String, val refreshToken: String, val expiresInSeconds: Int, val isNewUser: Boolean)

data class AllocationDto(val mmf: Int, val tbill: Int, val unit: Int, val cash: Int)
data class RiskProfileBody(val riskHorizon: Int, val riskTolerance: Int, val incomeStability: Int, val currentSavingsCents: Long)
data class RecommendationDto(
    val category: String, val allocation: AllocationDto, val personalized: Boolean, val aiLabel: Boolean,
    val reasoning: String?, val confidenceScore: Double, val expectedAnnualYieldPct: Double
)

data class SettingsDto(
    val roundUpIncrementCents: Long, val maxRoundUpCents: Long, val dailyCapCents: Long, val stkThresholdCents: Long,
    val smsListenerEnabled: Boolean, val autoStkEnabled: Boolean
)

data class RateDto(
    val id: String, val key: String, val assetClass: String, val annualYieldRate: Double,
    val effectiveDate: String, val providerName: String, val riskScore: Int, val illustrative: Boolean
)

data class TransactionBody(val mpesaCode: String, val merchantName: String, val kind: String, val amountCents: Long, val occurredAt: String)
data class TransactionDto(
    val id: String, val mpesaCode: String, val merchantName: String, val kind: String,
    val amountCents: Long, val roundUpCents: Long, val status: String, val occurredAt: String
)
data class TransactionCreatedDto(
    val transaction: TransactionDto, val duplicate: Boolean, val pendingRoundUpCents: Long,
    val thresholdReached: Boolean, val autoStkDue: Boolean
)
data class TransactionPageDto(val items: List<TransactionDto>, val nextCursor: String?)

data class PortfolioDto(
    val balanceCents: Long, val pendingRoundUpCents: Long, val inFlightCents: Long, val unallocatedCents: Long,
    val recommendation: RecommendationDto?, val asOf: String
)
data class DepositBody(val amountCents: Long)
data class StkDto(
    val id: String, val purpose: String, val amountCents: Long, val status: String, val customerMessage: String?,
    val receiptNumber: String?, val resultDesc: String?, val createdAt: String, val completedAt: String?
)

data class GoalBody(val title: String, val emoji: String, val targetCents: Long, val deadline: String?)
data class GoalDto(
    val id: String, val title: String, val emoji: String, val targetCents: Long, val savedCents: Long,
    val deadline: String?, val progress: Double
)
data class ContributeBody(val amountCents: Long)
data class NotificationDto(val id: String, val title: String, val message: String, val createdAt: String, val isRead: Boolean)

data class ForecastBody(val principalCents: Long, val monthlyContributionCents: Long, val monthsAhead: Int, val inflationPct: Double = 5.0)
data class ForecastDto(
    val monthsAhead: Int, val totalContributedCents: Long, val pessimisticValueCents: Long, val projectedValueCents: Long,
    val optimisticValueCents: Long, val expectedAnnualReturnPct: Double, val pessimisticAnnualReturnPct: Double,
    val optimisticAnnualReturnPct: Double, val inflationAdjustedValueCents: Long, val assumedInflationPct: Double,
    val assumptions: List<String>, val disclaimer: String
)

data class AgentDto(
    val id: String, val name: String, val agentNumber: String, val latitude: Double, val longitude: Double,
    val distanceKm: Double, val illustrative: Boolean
)
data class InsightDto(val id: String, val message: String)
