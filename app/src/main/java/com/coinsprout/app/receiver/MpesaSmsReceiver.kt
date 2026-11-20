package com.coinsprout.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.coinsprout.app.BuildConfig
import com.coinsprout.app.di.ServiceLocator
import com.coinsprout.app.domain.MpesaSmsParser
import com.coinsprout.app.domain.Money
import com.coinsprout.app.sync.SyncWorker
import com.coinsprout.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Captures M-Pesa confirmations. Only compiled into behaviour for the "demo" flavour; the "play" flavour removes
 * this receiver from the manifest entirely (Google Play SMS-permission policy).
 *
 * Safeguards: sender must be exactly "MPESA"; the body must look like a real confirmation (see [MpesaSmsParser]);
 * the work is done off the main thread via goAsync(); nothing is written if the user switched capture off or is
 * signed out; duplicates are ignored by receipt code.
 */
class MpesaSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.SMS_AUTO_DETECT || intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val session = ServiceLocator.session(context)
        if (!session.isLoggedIn || !session.settings.value.smsListenerEnabled) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return
        val sender = parts[0].originatingAddress ?: return
        if (!sender.equals("MPESA", ignoreCase = true)) return
        val parsed = MpesaSmsParser.parse(parts.joinToString("") { it.messageBody.orEmpty() }, System.currentTimeMillis()) ?: return

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val outcome = ServiceLocator.repository(app).recordPurchase(parsed)
                if (!outcome.duplicate) {
                    val text = if (outcome.roundUpCents > 0) "Round-up of ${Money.kes(outcome.roundUpCents)} recorded from ${parsed.merchant}. It is invested when you approve the M-Pesa prompt."
                    else "Purchase at ${parsed.merchant} logged."
                    NotificationHelper.show(app, "CoinSprout", text)
                    SyncWorker.enqueueNow(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
