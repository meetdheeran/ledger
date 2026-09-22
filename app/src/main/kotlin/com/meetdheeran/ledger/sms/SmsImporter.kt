package com.meetdheeran.ledger.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.meetdheeran.ledger.data.Card
import com.meetdheeran.ledger.data.Category
import com.meetdheeran.ledger.data.Direction
import com.meetdheeran.ledger.data.LedgerDb
import com.meetdheeran.ledger.data.Txn
import com.meetdheeran.ledger.data.UnparsedSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the bank messages already on the phone and turns them into the record.
 *
 * Both entry points - the 30 day backfill and a single message arriving live -
 * funnel through [ingest], so a message is treated identically however it is
 * seen, and the unique source hash means seeing it twice changes nothing.
 */
object SmsImporter {

    /** Agreed with Dheer: thirty days of history on first run, and again whenever a new card shows up. */
    const val BACKFILL_DAYS = 30

    data class Result(
        val scanned: Int = 0,
        val transactions: Int = 0,
        val unreadable: Int = 0,
        val newCards: Int = 0
    )

    fun hasReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Scans the last [days] days of the SMS inbox. Safe to run repeatedly: the
     * source hash makes re-importing a no-op, which is what lets a newly seen
     * card trigger another pass without duplicating anything.
     */
    suspend fun backfill(context: Context, days: Int = BACKFILL_DAYS): Result =
        withContext(Dispatchers.IO) {
            if (!hasReadPermission(context)) return@withContext Result()

            val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
            val db = LedgerDb.get(context)
            val rules = Rules.load(context)
            val userRules = db.dao().allMerchantRules().associate { it.merchantKey to it.category }

            var scanned = 0
            var txns = 0
            var unreadable = 0
            var newCards = 0

            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(cutoff.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val iAddr = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val iBody = c.getColumnIndex(Telephony.Sms.BODY)
                val iDate = c.getColumnIndex(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    val sender = if (iAddr >= 0) c.getString(iAddr).orEmpty() else ""
                    val body = if (iBody >= 0) c.getString(iBody).orEmpty() else ""
                    val date = if (iDate >= 0) c.getLong(iDate) else continue
                    if (body.isBlank()) continue
                    scanned++

                    when (val r = ingest(context, date, sender, body, rules, userRules)) {
                        is Ingested.Transaction -> { txns++; if (r.newCard) newCards++ }
                        Ingested.Unreadable -> unreadable++
                        Ingested.Skipped -> Unit
                    }
                }
            }

            Result(scanned, txns, unreadable, newCards)
        }

    /** One message, from the live receiver. Returns true when it revealed a card we had not seen. */
    suspend fun ingestLive(context: Context, timestamp: Long, sender: String, body: String): Boolean =
        withContext(Dispatchers.IO) {
            val db = LedgerDb.get(context)
            val rules = Rules.load(context)
            val userRules = db.dao().allMerchantRules().associate { it.merchantKey to it.category }
            val r = ingest(context, timestamp, sender, body, rules, userRules)
            r is Ingested.Transaction && r.newCard
        }

    /** A message from somewhere other than the SMS provider. */
    data class Message(val timestamp: Long, val sender: String, val body: String)

    /**
     * Imports messages handed in directly rather than read from the inbox.
     * Used by the debug sample loader, so the parser, the card detection and the
     * dashboard can all be exercised on a phone with no SIM in it.
     */
    suspend fun ingestAll(context: Context, messages: List<Message>): Result =
        withContext(Dispatchers.IO) {
            val db = LedgerDb.get(context)
            val rules = Rules.load(context)
            val userRules = db.dao().allMerchantRules().associate { it.merchantKey to it.category }

            var txns = 0
            var unreadable = 0
            var newCards = 0
            messages.forEach { m ->
                when (val r = ingest(context, m.timestamp, m.sender, m.body, rules, userRules)) {
                    is Ingested.Transaction -> { txns++; if (r.newCard) newCards++ }
                    Ingested.Unreadable -> unreadable++
                    Ingested.Skipped -> Unit
                }
            }
            Result(messages.size, txns, unreadable, newCards)
        }

    private sealed interface Ingested {
        data class Transaction(val newCard: Boolean) : Ingested
        data object Unreadable : Ingested
        data object Skipped : Ingested
    }

    private suspend fun ingest(
        context: Context,
        timestamp: Long,
        sender: String,
        body: String,
        rules: RulesFile,
        userRules: Map<String, Category>
    ): Ingested {
        // Bank messages only, as agreed. Anything from a sender we cannot tie
        // to a bank is not this app's business and is not stored anywhere.
        val bank = Rules.bankFor(rules, sender, body) ?: return Ingested.Skipped

        val dao = LedgerDb.get(context).dao()
        val hash = SmsParser.sourceHash(timestamp, sender, body)

        return when (val outcome = SmsParser.classify(body)) {
            SmsParser.Outcome.Ignore -> Ingested.Skipped

            SmsParser.Outcome.Unreadable -> {
                dao.insertUnparsed(
                    UnparsedSms(sourceHash = hash, timestamp = timestamp, sender = sender, body = body)
                )
                Ingested.Unreadable
            }

            is SmsParser.Outcome.Transaction -> {
                val p = outcome.parsed
                val merchant = p.merchant ?: bank

                var newCard = false
                p.last4?.let { last4 ->
                    val rowId = dao.insertCard(
                        Card(bank = bank, last4 = last4, firstSeen = timestamp)
                    )
                    // IGNORE returns -1 when the card already existed.
                    if (rowId != -1L) newCard = true
                }

                dao.insertTxn(
                    Txn(
                        sourceHash = hash,
                        timestamp = timestamp,
                        amountMinor = p.amountMinor,
                        currency = p.currency,
                        direction = p.direction,
                        merchant = merchant,
                        cardLast4 = p.last4,
                        bank = bank,
                        category = categoryFor(rules, userRules, merchant, p.direction),
                        balanceMinor = p.balanceMinor,
                        body = body
                    )
                )
                Ingested.Transaction(newCard)
            }
        }
    }

    /**
     * A correction the user has already made wins over the bundled rules -
     * re-categorising a merchant once should not be undone by the next import.
     */
    private fun categoryFor(
        rules: RulesFile,
        userRules: Map<String, Category>,
        merchant: String,
        direction: Direction
    ): Category {
        userRules[merchant.uppercase()]?.let { return it }
        val guess = Rules.categoryFor(rules, merchant)
        if (guess != Category.OTHER) return guess
        return if (direction == Direction.CREDIT) Category.INCOME else Category.OTHER
    }
}
