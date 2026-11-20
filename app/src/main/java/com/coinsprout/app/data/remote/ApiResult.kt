package com.coinsprout.app.data.remote

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

sealed class ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>()

    /** [offline] = no connectivity / server unreachable (retry later); otherwise the server rejected the call. */
    data class Err(val message: String, val code: Int? = null, val offline: Boolean = false) : ApiResult<Nothing>()
}

suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ApiResult.Err(extractMessage(e), e.code())
} catch (e: IOException) {
    ApiResult.Err("No connection. Check your internet and try again.", null, offline = true)
} catch (e: Exception) {
    ApiResult.Err("Something went wrong. Please try again.")
}

/** FastAPI errors are {"detail": "text"} or {"detail": [{"msg": "..."}]}. */
private fun extractMessage(e: HttpException): String = try {
    val detail = JsonParser.parseString(e.response()?.errorBody()?.string() ?: "").asJsonObject.get("detail")
    when {
        detail == null -> fallback(e.code())
        detail.isJsonPrimitive -> detail.asString
        detail.isJsonArray && detail.asJsonArray.size() > 0 ->
            detail.asJsonArray[0].asJsonObject.get("msg")?.asString ?: fallback(e.code())
        else -> fallback(e.code())
    }
} catch (_: Exception) {
    fallback(e.code())
}

private fun fallback(code: Int) = when (code) {
    401 -> "Please sign in again."
    429 -> "Too many attempts. Please wait a moment."
    in 500..599 -> "Our servers are having trouble. Please try again shortly."
    else -> "Request failed ($code)."
}
