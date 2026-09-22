package com.meetdheeran.ledger.sms

import com.meetdheeran.ledger.data.Direction
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

/**
 * Turns a bank SMS into a transaction, or says honestly that it could not.
 *
 * The grammar here is deliberately general rather than one regex per bank:
 * transaction messages across UAE banks all say the same things in a slightly
 * different order - a currency and an amount, a verb that gives the direction,
 * a masked card, a merchant, sometimes a balance. Bank-specific data lives in
 * `assets/rules.json`.
 */
object SmsParser {

    data class Parsed(
        val amountMinor: Long,
        val currency: String,
        val direction: Direction,
        val merchant: String?,
        val last4: String?,
        val balanceMinor: Long?
    )

    sealed interface Outcome {
        /** A transaction we understood. */
        data class Transaction(val parsed: Parsed) : Outcome
        /** Not a transaction at all - an OTP, a promotion, a balance enquiry. Dropped. */
        data object Ignore : Outcome
        /** Looks like money moved, but the rules could not read it. Goes to the bucket. */
        data object Unreadable : Outcome
    }

    private const val CURRENCIES =
        "AED|USD|EUR|GBP|INR|SAR|QAR|KWD|OMR|BHD|CAD|AUD|CHF|JPY|PKR|EGP|PHP|LKR"

    /** "AED 1,234.56" - the common form. */
    private val AMOUNT_CODE_FIRST = Regex(
        """\b($CURRENCIES)\s*\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,3})?)""",
        RegexOption.IGNORE_CASE
    )

    /** "1,234.56 AED" - used by a few banks and by foreign-currency lines. */
    private val AMOUNT_CODE_LAST = Regex(
        """([0-9][0-9,]*(?:\.[0-9]{1,3})?)\s*($CURRENCIES)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Hard reject. An OTP for a purchase quotes the amount, so without this
     * check every confirmed payment would be counted twice - once from the OTP
     * and once from the receipt.
     */
    private val OTP = Regex(
        """(?i)\b(otp|o\.t\.p|one[- ]time (?:password|pin|code)|verification code|secure code|authentication code)\b"""
    )

    private val PROMO = Regex(
        """(?i)\b(offer|discount|apply now|click|unsubscribe|promo|congratulations|you have won|prize|eligible for|pre[- ]?approved|upgrade now|interest rate|loan offer|terms and conditions apply)\b"""
    )

    private val DEBIT_WORDS = listOf(
        "debited", "debit of", "spent", "purchase of", "purchase at", "used for",
        "withdrawn", "withdrawal", "paid to", "payment of", "charged", "deducted",
        "sent to", "transferred to", "pos txn", "pos transaction", "has been used"
    )

    private val CREDIT_WORDS = listOf(
        "credited", "credit of", "received", "deposit of", "deposited", "refund",
        "salary", "reversed", "added to", "transferred from", "cashback", "has been reversed"
    )

    private val LAST4_PATTERNS = listOf(
        Regex("""(?i)ending\s+(?:with\s+|in\s+)?[*xX#\s]*(\d{4})"""),
        Regex("""(?i)(?:card|account|a/c|acct)\s*(?:no\.?|number|#)?\s*[:\s]*[*xX#]{2,}\s*(\d{4})"""),
        Regex("""[*xX#]{3,}\s*(\d{4})"""),
        Regex("""(?i)(?:card|a/c|acct)\s*[:\s]\s*(\d{4})\b""")
    )

    // A dot is allowed inside a merchant name and only ends it when a space or
    // the end of the message follows. Online merchants here are mostly domains
    // - NOON.COM, APPLE.COM/BILL, AMAZON.AE - and cutting at the first dot both
    // mangles the name and loses the category match that depends on it.
    private val MERCHANT_AT = Regex(
        """\bat\s+([^,;:\n]{2,60}?)(?=\s+(?:on|dated|avl|avbl|available|bal|balance|ref|txn|trx|using|with|card)\b|[,;:\n]|\.(?:\s|\z)|\z)""",
        RegexOption.IGNORE_CASE
    )

    private val MERCHANT_TO = Regex(
        """\b(?:to|towards)\s+([^,;:\n]{2,60}?)(?=\s+(?:on|dated|avl|avbl|available|bal|balance|ref|txn|trx|using|with|card|from)\b|[,;:\n]|\.(?:\s|\z)|\z)""",
        RegexOption.IGNORE_CASE
    )

    /** An amount found in the text, with where it was found. */
    private data class Found(val value: BigDecimal, val currency: String, val at: Int)

    fun classify(body: String): Outcome {
        if (OTP.containsMatchIn(body)) return Outcome.Ignore

        val amounts = findAmounts(body)
        val txnAmount = amounts.firstOrNull { !isBalanceContext(body, it.at) }
        val balance = amounts.firstOrNull { isBalanceContext(body, it.at) }
        val direction = directionOf(body)

        if (direction == null && txnAmount == null) return Outcome.Ignore
        if (direction == null) {
            // Money is mentioned but nothing says it moved: a balance alert, or
            // marketing quoting a price.
            return if (PROMO.containsMatchIn(body)) Outcome.Ignore else Outcome.Unreadable
        }
        if (txnAmount == null) return Outcome.Unreadable

        val minor = txnAmount.value.toMinor()
        if (minor <= 0L || minor > 1_000_000_000_00L) return Outcome.Unreadable

        return Outcome.Transaction(
            Parsed(
                amountMinor = minor,
                currency = txnAmount.currency,
                direction = direction,
                merchant = merchantOf(body, direction),
                last4 = last4Of(body),
                balanceMinor = balance?.value?.toMinor()
            )
        )
    }

    // ---- pieces -------------------------------------------------------------

    private fun findAmounts(body: String): List<Found> {
        val out = mutableListOf<Found>()
        AMOUNT_CODE_FIRST.findAll(body).forEach { m ->
            parseDecimal(m.groupValues[2])?.let {
                out += Found(it, m.groupValues[1].uppercase(), m.range.first)
            }
        }
        AMOUNT_CODE_LAST.findAll(body).forEach { m ->
            // Skip anything the first pattern already covered, so "AED 100 AED"
            // style overlaps are not counted twice.
            if (out.none { kotlin.math.abs(it.at - m.range.first) < 24 }) {
                parseDecimal(m.groupValues[1])?.let {
                    out += Found(it, m.groupValues[2].uppercase(), m.range.first)
                }
            }
        }
        return out.sortedBy { it.at }
    }

    /**
     * True when the amount at [at] is a balance or a credit limit rather than
     * the transaction. Decided by the words immediately before it, which is
     * where every bank puts "available balance".
     */
    private fun isBalanceContext(body: String, at: Int): Boolean {
        val from = (at - 34).coerceAtLeast(0)
        val prefix = body.substring(from, at).lowercase()
        return prefix.contains("bal") || prefix.contains("limit") || prefix.contains("outstanding")
    }

    /**
     * Debit or credit. "Credit card" and "debit card" are stripped first: the
     * word "credit" in a card's name has nothing to do with which way the money
     * went, and treating it as a signal turns every card purchase into income.
     */
    private fun directionOf(body: String): Direction? {
        val b = body.lowercase()
            .replace("credit card", " ")
            .replace("creditcard", " ")
            .replace("credit limit", " ")
            .replace("debit card", " ")
            .replace("debitcard", " ")

        val debitAt = DEBIT_WORDS.mapNotNull { w -> b.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val creditAt = CREDIT_WORDS.mapNotNull { w -> b.indexOf(w).takeIf { it >= 0 } }.minOrNull()

        return when {
            debitAt != null && creditAt != null -> if (debitAt <= creditAt) Direction.DEBIT else Direction.CREDIT
            debitAt != null -> Direction.DEBIT
            creditAt != null -> Direction.CREDIT
            else -> null
        }
    }

    private fun last4Of(body: String): String? {
        for (p in LAST4_PATTERNS) {
            p.find(body)?.groupValues?.getOrNull(1)?.let { if (it.length == 4) return it }
        }
        return null
    }

    private fun merchantOf(body: String, direction: Direction): String? {
        val candidates = listOfNotNull(
            MERCHANT_AT.find(body)?.groupValues?.getOrNull(1),
            MERCHANT_TO.find(body)?.groupValues?.getOrNull(1)
        )
        val cleaned = candidates
            .map { tidy(it) }
            .firstOrNull { it.isNotBlank() && !isPronounish(it) }
        if (cleaned != null) return cleaned

        val b = body.lowercase()
        return when {
            b.contains("atm") || b.contains("cash withdrawal") -> "ATM"
            b.contains("salary") || b.contains("payroll") -> "Salary"
            direction == Direction.CREDIT -> "Credit"
            else -> null
        }
    }

    private fun tidy(s: String): String =
        s.trim()
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '*', ':', '.', ',')

    /** Rejects "your account", "the following" and similar non-merchants. */
    private fun isPronounish(s: String): Boolean {
        val first = s.substringBefore(' ').lowercase()
        return first in setOf("your", "you", "the", "this", "a", "an", "our", "my", "account", "card")
    }

    private fun parseDecimal(raw: String): BigDecimal? =
        runCatching { BigDecimal(raw.replace(",", "")) }.getOrNull()

    private fun BigDecimal.toMinor(): Long =
        movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

    /**
     * A stable identity for a message, so the same SMS seen by the backfill and
     * by the live receiver is stored once. Derived from the message itself
     * rather than the provider's row id, because the live receiver never sees
     * a row id.
     */
    fun sourceHash(timestamp: Long, sender: String, body: String): String {
        val material = "$timestamp|${sender.trim().uppercase()}|${body.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
