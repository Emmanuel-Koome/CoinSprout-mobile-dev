package com.coinsprout.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Non-2xx responses throw retrofit2.HttpException; see [apiCall]. */
interface ApiService {
    @POST("api/v1/auth/request-otp") suspend fun requestOtp(@Body body: OtpRequestBody): OtpRequestedDto
    @POST("api/v1/auth/verify-otp") suspend fun verifyOtp(@Body body: OtpVerifyBody): TokenPairDto
    @POST("api/v1/auth/refresh") suspend fun refresh(@Body body: RefreshBody): TokenPairDto
    @POST("api/v1/auth/logout") suspend fun logout(@Body body: RefreshBody)

    @DELETE("api/v1/me") suspend fun deleteAccount()
    @GET("api/v1/me/settings") suspend fun getSettings(): SettingsDto
    @PUT("api/v1/me/settings") suspend fun putSettings(@Body body: SettingsDto): SettingsDto
    @PUT("api/v1/me/risk-profile") suspend fun putRiskProfile(@Body body: RiskProfileBody): RecommendationDto

    @GET("api/v1/cbk-rates") suspend fun getRates(): List<RateDto>

    @POST("api/v1/transactions") suspend fun postTransaction(@Body body: TransactionBody): TransactionCreatedDto
    @GET("api/v1/transactions") suspend fun getTransactions(@Query("limit") limit: Int = 100): TransactionPageDto

    @GET("api/v1/portfolio") suspend fun getPortfolio(): PortfolioDto
    @POST("api/v1/deposits") suspend fun deposit(@Body body: DepositBody, @Header("Idempotency-Key") key: String): StkDto
    @POST("api/v1/stk/sweep") suspend fun sweep(@Header("Idempotency-Key") key: String): StkDto
    @GET("api/v1/stk/{id}") suspend fun getStk(@Path("id") id: String): StkDto

    @GET("api/v1/goals") suspend fun getGoals(): List<GoalDto>
    @POST("api/v1/goals") suspend fun createGoal(@Body body: GoalBody): GoalDto
    @POST("api/v1/goals/{id}/contribute") suspend fun contribute(@Path("id") id: String, @Body body: ContributeBody): GoalDto
    @DELETE("api/v1/goals/{id}") suspend fun deleteGoal(@Path("id") id: String)

    @GET("api/v1/notifications") suspend fun getNotifications(): List<NotificationDto>
    @POST("api/v1/notifications/read-all") suspend fun readAllNotifications()

    @POST("api/v1/ai/forecast-growth") suspend fun forecast(@Body body: ForecastBody): ForecastDto
    @GET("api/v1/ai/spending-insights") suspend fun insights(): List<InsightDto>
    /** Dev backend only (404 in production). Used by the debug-build "simulate approval" button. */
    @POST("api/v1/dev/payments/{id}/simulate") suspend fun devSimulate(@Path("id") id: String, @Query("outcome") outcome: String)

    @GET("api/v1/agents/nearby") suspend fun nearbyAgents(@Query("lat") lat: Double, @Query("lng") lng: Double): List<AgentDto>
}
