package com.meetdheeran.ledger.sms

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BankRulesTest {
    private val rules = Json { ignoreUnknownKeys = true }.decodeFromString<RulesFile>(
        File("src/main/assets/rules.json").readText())

    @Test fun `every listed bank sender resolves and can classify a card purchase`() {
        rules.banks.forEach { bank -> bank.senders.forEach { sender ->
            assertEquals(sender, bank.name, Rules.bankFor(rules, sender, ""))
            val result = SmsParser.classify("Your credit card ending 1234 was used for AED 50.00 at LULU on 25/09/2026")
            assertTrue(result is SmsParser.Outcome.Transaction)
            assertEquals(com.meetdheeran.ledger.data.TransactionKind.PURCHASE,
                (result as SmsParser.Outcome.Transaction).parsed.kind)
        } }
    }

    @Test fun `bank names inside unrelated senders do not match`() {
        listOf("Deliveroo", "Fabulous", "LivingSocial", "NeoCinema", "+971501234567").forEach {
            assertNull(it, Rules.bankFor(rules, it, "Your order is ready"))
        }
    }

    @Test fun `supported suffixes and punctuation are accepted`() {
        assertEquals("Emirates NBD", Rules.bankFor(rules, "ENBD-Alert", ""))
        assertEquals("ADCB", Rules.bankFor(rules, "ADCB SMS", ""))
        assertEquals("Wio", Rules.bankFor(rules, "WioBank", ""))
    }

    @Test fun `custom sender is exact and is not inferred from message content`() {
        val custom = RulesFile(banks = listOf(BankRule("My UAE Bank", listOf("UAE-Sender"), exactSender = true)))
        assertEquals("My UAE Bank", Rules.bankFor(custom, "UAE-Sender", ""))
        assertNull(Rules.bankFor(custom, "UAE-Sender-Alerts", ""))
        assertNull(Rules.bankFor(custom, "+971501234567", "My UAE Bank"))
    }
}
