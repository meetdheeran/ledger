package com.meetdheeran.ledger.core

import com.meetdheeran.ledger.data.*
import com.meetdheeran.ledger.sms.SmsParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Locks a whole synthetic month end to end: SMS text → classification → dashboard totals.
 * A rule change that silently moves one message between types fails here even when every
 * single-message test still passes. Fixture: test/resources/fixtures/golden-month.txt.
 */
class GoldenMonthTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val month = YearMonth.of(2026, 9)

    private data class Line(val date: String, val bank: String, val expected: String, val body: String)

    private val lines: List<Line> = javaClass.classLoader!!.getResource("fixtures/golden-month.txt")!!
        .readText(Charsets.UTF_8).lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { raw -> raw.split(" | ", limit = 4).let { Line(it[0], it[1], it[2], it[3]) } }

    private fun outcomeName(o: SmsParser.Outcome): String = when (o) {
        is SmsParser.Outcome.Transaction -> o.parsed.kind.name
        SmsParser.Outcome.Ignore -> "IGNORE"
        SmsParser.Outcome.Unreadable -> "UNREADABLE"
    }

    private fun txns(): List<Txn> = lines.mapIndexedNotNull { i, l ->
        val p = (SmsParser.classify(l.body) as? SmsParser.Outcome.Transaction)?.parsed ?: return@mapIndexedNotNull null
        Txn(id = i.toLong(), sourceHash = "$i",
            timestamp = LocalDate.parse(l.date).atStartOfDay(zone).toInstant().toEpochMilli(),
            amountMinor = p.amountMinor, currency = p.currency, direction = p.direction,
            merchant = p.merchant ?: "", cardLast4 = p.last4, bank = l.bank,
            category = Category.OTHER, body = l.body, kind = p.kind)
    }

    @Test fun `every fixture message keeps its expected outcome`() {
        val drift = lines.mapNotNull { l ->
            val actual = outcomeName(SmsParser.classify(l.body))
            if (actual == l.expected) null else "${l.date} ${l.bank}: expected ${l.expected}, got $actual — ${l.body}"
        }
        assertTrue("Classification drift:\n" + drift.joinToString("\n"), drift.isEmpty())
    }

    @Test fun `september totals are locked`() {
        val s = Analytics.summary(txns(), month, LocalDate.of(2026, 9, 30), zone)
        assertEquals("spending AED", 125000L, s.spending)
        assertEquals("income AED", 1202000L, s.income)
        assertEquals("spending transactions", 8, s.purchases)
        assertEquals("needs review", 2, s.transactions.count { it.kind == TransactionKind.REVIEW })
    }

    @Test fun `per bank spending is locked`() {
        val s = Analytics.summary(txns(), month, LocalDate.of(2026, 9, 30), zone)
        val byBank = s.transactions.filter(Analytics::spending).groupBy { it.bank }
            .mapValues { (_, v) -> v.sumOf { it.amountMinor } }
        assertEquals(mapOf("ENBD" to 55000L, "ADCB" to 52500L, "FAB" to 17500L), byBank)
    }

    @Test fun `foreign currency and previous month never reach the AED total`() {
        val all = txns()
        assertTrue(all.any { it.currency == "USD" && it.kind == TransactionKind.PURCHASE })
        assertTrue(all.any { !Analytics.inMonth(it, month, zone) })
    }
}
