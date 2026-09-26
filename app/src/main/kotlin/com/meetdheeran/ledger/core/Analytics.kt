package com.meetdheeran.ledger.core

import com.meetdheeran.ledger.data.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CardKey(val bank: String, val last4: String)

enum class ActivityGroup(val label: String) {
    ALL("All"), SPENDING("Spending"), INCOME("Income"), TRANSFERS("Transfers"),
    REFUNDS("Refunds"), CASH("Cash"), REVIEW("Needs review");

    fun includes(t: Txn): Boolean = when (this) {
        ALL -> true
        SPENDING -> t.kind.countsAsSpending
        INCOME -> t.kind in setOf(TransactionKind.SALARY, TransactionKind.OTHER_INCOME)
        TRANSFERS -> t.kind in setOf(TransactionKind.TRANSFER_IN, TransactionKind.TRANSFER_OUT, TransactionKind.CARD_REPAYMENT)
        REFUNDS -> t.kind == TransactionKind.REFUND
        CASH -> t.kind == TransactionKind.CASH_WITHDRAWAL
        REVIEW -> t.kind == TransactionKind.REVIEW
    }
}

data class ActivityQuery(
    val search: String = "",
    val group: ActivityGroup = ActivityGroup.ALL,
    val card: CardKey? = null,
    val category: Category? = null,
    val currency: String? = null,
    val allHistory: Boolean = false,
    val oldestFirst: Boolean = false
)

data class MonthSummary(
    val transactions: List<Txn>,
    val spending: Long,
    val income: Long,
    val purchases: Int,
    val dailyAverage: Long,
    val categories: List<Pair<Category, Long>>,
    val merchants: List<Pair<String, Long>>,
    val daily: List<Long>
)

/** All analytics share the same spending and currency policy as the dashboard. */
object Analytics {
    fun date(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    fun inMonth(t: Txn, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        YearMonth.from(date(t.timestamp, zone)) == month

    fun spending(t: Txn): Boolean = t.currency == "AED" && t.kind.countsAsSpending

    fun summary(all: List<Txn>, month: YearMonth, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): MonthSummary {
        val rows = all.filter { inMonth(it, month, zone) }
        val expenses = rows.filter(::spending)
        val spent = expenses.sumOf { it.amountMinor }
        val days = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
        return MonthSummary(rows, spent,
            rows.filter { it.currency == "AED" && ActivityGroup.INCOME.includes(it) }.sumOf { it.amountMinor },
            expenses.size, spent / days.coerceAtLeast(1),
            expenses.groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.amountMinor } }.sortedByDescending { it.second },
            expenses.groupBy { it.merchant }.map { (k, v) -> k to v.sumOf { it.amountMinor } }.sortedByDescending { it.second },
            (1..month.lengthOfMonth()).map { day -> expenses.filter { date(it.timestamp, zone).dayOfMonth == day }.sumOf { it.amountMinor } })
    }

    fun filter(all: List<Txn>, month: YearMonth, q: ActivityQuery, zone: ZoneId = ZoneId.systemDefault()): List<Txn> {
        val needle = q.search.trim()
        val rows = all.filter { t ->
            (q.allHistory || inMonth(t, month, zone)) && q.group.includes(t) &&
                (q.card == null || (t.bank == q.card.bank && t.cardLast4 == q.card.last4)) &&
                (q.category == null || t.category == q.category) && (q.currency == null || t.currency == q.currency) &&
                (needle.isEmpty() || listOf(t.merchant, t.bank, t.cardLast4.orEmpty(), t.kind.label, t.category.label,
                    BigDecimal.valueOf(t.amountMinor, 2).toPlainString()).any { it.contains(needle, ignoreCase = true) })
        }
        return if (q.oldestFirst) rows.sortedBy { it.timestamp } else rows.sortedByDescending { it.timestamp }
    }

    /** Reject partial/negative amounts rather than silently rounding a user's budget. */
    fun parseBudget(raw: String): Long? = runCatching {
        val input = raw.trim()
        require(Regex("""(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d{1,2})?""").matches(input))
        val value = BigDecimal(input.replace(",", ""))
        require(value >= BigDecimal.ZERO && value <= BigDecimal("1000000000"))
        value.movePointRight(2).longValueExact()
    }.getOrNull()
}
