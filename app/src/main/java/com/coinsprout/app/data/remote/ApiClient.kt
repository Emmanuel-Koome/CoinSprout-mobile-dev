package com.coinsprout.app.data.remote

import com.coinsprout.app.BuildConfig
import com.coinsprout.app.data.prefs.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(session: SessionStore) {

    private val plainClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun retrofit(client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /** Used only to refresh tokens (no interceptor, so it can never recurse). */
    private val refreshApi: ApiService = retrofit(plainClient).create(ApiService::class.java)

    private val authedClient = plainClient.newBuilder()
        .addInterceptor { chain ->
            val token = session.accessToken
            val request = if (token != null) chain.request().newBuilder().header("Authorization", "Bearer $token").build() else chain.request()
            chain.proceed(request)
        }
        .authenticator(TokenAuthenticator(session, refreshApi))
        .build()

    val api: ApiService = retrofit(authedClient).create(ApiService::class.java)
}

/** On a 401, rotate the refresh token once and retry. If the refresh itself is rejected, sign the user out. */
private class TokenAuthenticator(private val session: SessionStore, private val refreshApi: ApiService) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains("/auth/")) return null
        if (attempts(response) >= 2) return null
        synchronized(this) {
            val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
            val current = session.accessToken
            // Another request already refreshed while we were waiting for the lock.
            if (current != null && current != sent) return withToken(response.request, current)
            val refresh = session.refreshToken ?: return null
            return try {
                val pair = runBlocking { refreshApi.refresh(RefreshBody(refresh)) }
                session.saveTokens(pair.accessToken, pair.refreshToken)
                withToken(response.request, pair.accessToken)
            } catch (e: HttpException) {
                session.clearTokens()   // refresh token rejected or reused -> must sign in again
                null
            } catch (e: Exception) {
                null                    // transient network error: keep the session, fail this one request
            }
        }
    }

    private fun withToken(request: Request, token: String) = request.newBuilder().header("Authorization", "Bearer $token").build()

    private fun attempts(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }
}
