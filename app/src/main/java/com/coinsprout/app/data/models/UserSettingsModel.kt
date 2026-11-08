package com.coinsprout.app.data.models

data class UserSettingsModel(
    val roundUpIncrement: Int = 50, // 10, 50, or 100 KES
    val stkThreshold: Double = 100.0, // STK trigger point
    val isSmsListenerEnabled: Boolean = true,
    val isAutoStkEnabled: Boolean = true,
    val smsPermissionGranted: Boolean = true,
    val notificationsPermissionGranted: Boolean = true
)
