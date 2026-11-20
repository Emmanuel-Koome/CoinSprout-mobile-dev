package com.coinsprout.app.data.prefs

import android.content.Context
import com.coinsprout.app.data.models.PortfolioSnapshot
import com.coinsprout.app.data.models.UserSettingsModel
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tokens, settings and the last portfolio snapshot, in private SharedPreferences.
 * SECURITY NOTE: tokens are not encrypted at rest; app-private storage + allowBackup=false is acceptable for a
 * prototype, but a production build should wrap them with an Android Keystore key.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("coinsprout_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _loggedIn = MutableStateFlow(prefs.getString(KEY_REFRESH, null) != null)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettingsModel> = _settings.asStateFlow()

    private val _portfolio = MutableStateFlow(loadPortfolio())
    val portfolio: StateFlow<PortfolioSnapshot> = _portfolio.asStateFlow()

    val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)
    val isLoggedIn: Boolean get() = refreshToken != null

    fun saveTokens(access: String, refresh: String) {
        prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply()
        _loggedIn.value = true
    }

    fun clearTokens() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).apply()
        _loggedIn.value = false
    }

    /** Wipes everything (logout / account deletion). */
    fun clearAll() {
        prefs.edit().clear().apply()
        _loggedIn.value = false
        _settings.value = UserSettingsModel()
        _portfolio.value = PortfolioSnapshot()
    }

    var settingsDirty: Boolean
        get() = prefs.getBoolean(KEY_SETTINGS_DIRTY, false)
        set(value) { prefs.edit().putBoolean(KEY_SETTINGS_DIRTY, value).apply() }

    fun saveSettings(s: UserSettingsModel) {
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(s)).apply()
        _settings.value = s
    }

    fun savePortfolio(p: PortfolioSnapshot) {
        prefs.edit().putString(KEY_PORTFOLIO, gson.toJson(p)).apply()
        _portfolio.value = p
    }

    private fun loadSettings(): UserSettingsModel =
        runCatching { gson.fromJson(prefs.getString(KEY_SETTINGS, null), UserSettingsModel::class.java) }.getOrNull() ?: UserSettingsModel()

    private fun loadPortfolio(): PortfolioSnapshot =
        runCatching { gson.fromJson(prefs.getString(KEY_PORTFOLIO, null), PortfolioSnapshot::class.java) }.getOrNull() ?: PortfolioSnapshot()

    private companion object {
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEY_SETTINGS = "settings"
        const val KEY_SETTINGS_DIRTY = "settings_dirty"
        const val KEY_PORTFOLIO = "portfolio"
    }
}
