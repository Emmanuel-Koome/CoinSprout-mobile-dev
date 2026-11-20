package com.coinsprout.app.util

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.coinsprout.app.R

object NotificationHelper {
    private const val CHANNEL_ID = "coinsprout_savings"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Savings & payment alerts", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Round-ups recorded and M-Pesa payment prompts." }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** No-op (rather than a crash) when the user has switched notifications off. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, message: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
