package com.coinsprout.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coinsprout.app.BuildConfig
import com.coinsprout.app.data.remote.ApiResult
import com.coinsprout.app.di.ServiceLocator
import com.coinsprout.app.sync.SyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStep { PHONE, OTP }

data class AuthUiState(
    val phone: String = "",
    val otp: String = "",
    val step: AuthStep = AuthStep.PHONE,
    val loading: Boolean = false,
    val error: String? = null,
    val devOtpHint: String? = null,      // only ever populated by a dev backend, and only shown in debug builds
    val resendInSeconds: Int = 0
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.repository(app)
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()
    private var ticker: Job? = null

    fun onPhoneChange(value: String) = _ui.update { it.copy(phone = value.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16), error = null) }
    fun onOtpChange(value: String) = _ui.update { it.copy(otp = value.filter(Char::isDigit).take(6), error = null) }
    fun backToPhone() = _ui.update { it.copy(step = AuthStep.PHONE, otp = "", error = null) }

    fun isPhoneValid(): Boolean = PHONE.matches(_ui.value.phone.replace(" ", ""))

    fun requestOtp() {
        if (!isPhoneValid()) { _ui.update { it.copy(error = "Enter a valid Kenyan mobile number, e.g. 0712 345 678") }; return }
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.requestOtp(_ui.value.phone)) {
                is ApiResult.Ok -> {
                    _ui.update { it.copy(loading = false, step = AuthStep.OTP, devOtpHint = if (BuildConfig.DEBUG) r.value.devOtp else null) }
                    startResendTimer()
                }
                is ApiResult.Err -> _ui.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    /** [onSuccess] receives true when the user already has a risk profile (skip the quiz). */
    fun verify(onSuccess: (hasProfile: Boolean) -> Unit) {
        val s = _ui.value
        if (s.otp.length != 6) { _ui.update { it.copy(error = "Enter the 6-digit code from the SMS") }; return }
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.verifyOtp(s.phone, s.otp)) {
                is ApiResult.Ok -> {
                    repo.pullPortfolio()
                    SyncWorker.enqueueNow(getApplication())
                    val hasProfile = repo.portfolio.value.recommendation != null
                    _ui.update { AuthUiState() }
                    onSuccess(hasProfile)
                }
                is ApiResult.Err -> _ui.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    private fun startResendTimer() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            for (left in 30 downTo 0) { _ui.update { it.copy(resendInSeconds = left) }; delay(1_000) }
        }
    }

    private companion object {
        val PHONE = Regex("""^(?:\+?254|0)?[17]\d{8}$""")
    }
}
