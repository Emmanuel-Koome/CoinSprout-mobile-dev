package com.coinsprout.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.coinsprout.app.data.repository.CoinSproutRepository
import com.coinsprout.app.util.NotificationHelper
import java.util.regex.Pattern

class MpesaSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.messageBody ?: continue
                val sender = sms.originatingAddress ?: ""

                // Match M-Pesa standard SMS patterns: e.g. "Confirmed. Ksh85.00 paid to Mandazi Vendor on..."
                // or "Ksh230.00 sent to..."
                if (body.contains("M-PESA") || body.contains("Ksh") || sender.contains("MPESA", ignoreCase = true)) {
                    parseAndProcessMpesaSms(context, body)
                }
            }
        }
    }

    private fun parseAndProcessMpesaSms(context: Context, smsBody: String) {
        val amountPattern = Pattern.compile("(?:Ksh|KES)\\s*([0-9]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
        val merchantPattern = Pattern.compile("paid to\\s+([A-Za-z0-9\\s]+?)(?:\\s+on|\\s+new|\\.)", Pattern.CASE_INSENSITIVE)

        val amountMatcher = amountPattern.matcher(smsBody)
        val merchantMatcher = merchantPattern.matcher(smsBody)

        if (amountMatcher.find()) {
            val amountStr = amountMatcher.group(1) ?: return
            val amount = amountStr.toDoubleOrNull() ?: return

            val merchant = if (merchantMatcher.find()) {
                merchantMatcher.group(1)?.trim() ?: "M-Pesa Merchant"
            } else {
                "M-Pesa Merchant"
            }

            val repository = CoinSproutRepository.instance
            val (roundUp, thresholdHit) = repository.addParsedSmsTransaction(merchant, amount)

            val notifTitle = if (thresholdHit) "🌱 STK Push Triggered!" else "🌱 +KES ${roundUp.toInt()} Saved"
            val notifMessage = if (thresholdHit) {
                "Pending round-ups reached threshold. Tap to authorize STK push payment."
            } else {
                "Saved KES ${roundUp.toInt()} from $merchant ($amount KES purchase)"
            }

            NotificationHelper.showNotification(context, notifTitle, notifMessage)
        }
    }
}
