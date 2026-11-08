package com.coinsprout.app.data.models

data class NotificationModel(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val isRead: Boolean = false
)
