package com.meetdheeran.ledger.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Picks up bank messages as they arrive so the record stays current without the
 * app being opened.
 *
 * A new card appearing here triggers another 30 day backfill: the card's older
 * messages may already be sitting in the inbox from before it was first
 * recognised, and importing is idempotent, so the cheap correct move is to scan
 * again rather than reason about which messages were missed.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Multipart messages arrive as several parts of one logical SMS; the
        // bodies must be joined before parsing or a long message is truncated
        // exactly where the amount tends to sit.
        val sender = messages.first().displayOriginatingAddress.orEmpty()
        val timestamp = messages.first().timestampMillis
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newCard = SmsImporter.ingestLive(app, timestamp, sender, body)
                if (newCard) SmsImporter.backfill(app)
            } finally {
                pending.finish()
            }
        }
    }
}
