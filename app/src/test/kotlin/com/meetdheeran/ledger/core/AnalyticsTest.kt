package com.meetdheeran.ledger.core

import com.meetdheeran.ledger.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class AnalyticsTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val month = YearMonth.of(2026, 9)
    private fun txn(id: Long = 1, date: String = "2026-09-12", amount: Long = 10000,
        kind: TransactionKind = TransactionKind.PURCHASE, currency: String = "AED",
        bank: String = "ADCB", last4: String = "1234", category: Category = Category.GROCERIES,
        merchant: String = "CARREFOUR") = Txn(id = id, sourceHash = "$id", timestamp =
        LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(), amountMinor = amount,
        currency = currency, direction = kind.direction ?: Direction.DEBIT, merchant = merchant,
        cardLast4 = last4, bank = bank, category = category, body = "Synthetic test", kind = kind)

    @Test fun `summary excludes transfers refunds repayments review and foreign currency`() {
        val rows = TransactionKind.entries.mapIndexed { i, kind -> txn(i.toLong(), kind = kind) } +
            txn(100, currency = "USD") + txn(101, date = "2026-08-31")
        val summary = Analytics.summary(rows, month, LocalDate.of(2026, 9, 20), zone)
        assertEquals(30000L, summary.spending)
        assertEquals(20000L, summary.income)
        assertEquals(3, summary.purchases)
        assertEquals(1500L, summary.dailyAverage)
        assertEquals(summary.spending, summary.categories.sumOf { it.second })
        assertEquals(summary.spending, summary.merchants.sumOf { it.second })
        assertEquals(summary.spending, summary.daily.sum())
    }

    @Test fun `month boundaries respect local midnight and year rollover`() {
        val jan = YearMonth.of(2027, 1)
        assertTrue(Analytics.inMonth(txn(date = "2027-01-01"), jan, zone))
        assertFalse(Analytics.inMonth(txn(date = "2026-12-31"), jan, zone))
        assertFalse(Analytics.inMonth(txn(date = "2027-02-01"), jan, zone))
    }

    @Test fun `card drilldown includes bank identity not just suffix`() {
        val rows = listOf(txn(1, bank = "ADCB"), txn(2, bank = "FAB"), txn(3, last4 = "9999"))
        assertEquals(listOf(1L), Analytics.filter(rows, month, ActivityQuery(card = CardKey("ADCB", "1234")), zone).map { it.id })
    }

    @Test fun `search combines with type category currency and month filters`() {
        val rows = listOf(txn(1), txn(2, kind = TransactionKind.TRANSFER_OUT), txn(3, currency = "USD"),
            txn(4, category = Category.OTHER), txn(5, date = "2026-08-31"), txn(6, merchant = "OTHER"))
        val query = ActivityQuery(search = " carrefour ", group = ActivityGroup.SPENDING, category = Category.GROCERIES, currency = "AED")
        assertEquals(listOf(1L), Analytics.filter(rows, month, query, zone).map { it.id })
    }

    @Test fun `all history review filter finds older uncertain messages`() {
        val rows = listOf(txn(1, date = "2026-07-01", kind = TransactionKind.REVIEW), txn(2))
        assertEquals(listOf(1L), Analytics.filter(rows, month, ActivityQuery(group = ActivityGroup.REVIEW, allHistory = true), zone).map { it.id })
    }

    @Test fun `sort order can be reversed without changing results`() {
        val rows = listOf(txn(1, date = "2026-09-02"), txn(2, date = "2026-09-20"))
        assertEquals(listOf(2L, 1L), Analytics.filter(rows, month, ActivityQuery(), zone).map { it.id })
        assertEquals(listOf(1L, 2L), Analytics.filter(rows, month, ActivityQuery(oldestFirst = true), zone).map { it.id })
    }

    @Test fun `past month daily average uses every calendar day`() {
        val summary = Analytics.summary(listOf(txn(amount = 30000)), month, LocalDate.of(2026, 10, 2), zone)
        assertEquals(1000L, summary.dailyAverage)
        assertEquals(30, summary.daily.size)
        assertEquals(30000L, summary.daily[11])
    }

    @Test fun `empty and leap months are safe`() {
        val summary = Analytics.summary(emptyList(), YearMonth.of(2024, 2), LocalDate.of(2026, 9, 1), zone)
        assertEquals(0L, summary.spending)
        assertEquals(0L, summary.dailyAverage)
        assertEquals(29, summary.daily.size)
    }

    @Test fun `budget parsing preserves fils and rejects invalid or oversized amounts`() {
        assertEquals(123456L, Analytics.parseBudget("1,234.56"))
        assertEquals(500000L, Analytics.parseBudget("5000"))
        assertEquals(0L, Analytics.parseBudget("0"))
        listOf("", "-1", "1.234", "abc", "1000000001", "NaN", "500,50", "1,23", "1e3").forEach { assertNull(it, Analytics.parseBudget(it)) }
    }
}
