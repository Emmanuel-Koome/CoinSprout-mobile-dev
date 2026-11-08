package com.coinsprout.app.data.models

data class GoalModel(
    val id: String,
    val title: String,
    val targetAmount: Double,
    val savedAmount: Double,
    val emoji: String,
    val deadline: String
)
