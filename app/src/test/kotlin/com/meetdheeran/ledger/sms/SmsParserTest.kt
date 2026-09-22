package com.meetdheeran.ledger.sms

import com.meetdheeran.ledger.data.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sample messages are written in the shape UAE banks use. They are invented,
 * not copied from a real inbox - the point is the grammar, not the values.
 */
class SmsParserTest {

    private fun txn(body: String): SmsParser.Parsed {
        val outcome = SmsParser.classify(body)
        assertTrue("expected a transaction, got $outcome", outcome is SmsParser.Outcome.Transaction)
        return (outcome as SmsParser.Outcome.Transaction).parsed
    }

    // ---- the basic shapes ---------------------------------------------------

    @Test
    fun `card purchase with trailing balance`() {
        val p = txn(
            "Your Card ending 1234 has been used for AED 150.00 at CARREFOUR HYPERMARKET " +
                "on 12/09/2026. Available Balance: AED 5,230.75"
        )
        assertEquals(15_000L, p.amountMinor)
        assertEquals("AED", p.currency)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("1234", p.last4)
        assertEquals("CARREFOUR HYPERMARKET", p.merchant)
        // The balance must not be mistaken for the amount, and is kept separately.
        assertEquals(523_075L, p.balanceMinor)
    }

    @Test
    fun `masked card with X prefix`() {
        val p = txn("Dear Customer, AED 99.50 has been debited from your ADCB Card XXXX4471 at TALABAT on 13-09-2026.")
        assertEquals(9_950L, p.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("4471", p.last4)
        assertEquals("TALABAT", p.merchant)
    }

    @Test
    fun `thousands separator and abbreviated balance`() {
        val p = txn("Purchase of AED 1,250.00 at IKEA DUBAI with FAB Card ending 8890 on 14/09/2026. Avl Bal AED 12,004.20")
        assertEquals(125_000L, p.amountMinor)
        assertEquals("8890", p.last4)
        assertEquals("IKEA DUBAI", p.merchant)
        assertEquals(1_200_420L, p.balanceMinor)
    }

    @Test
    fun `salary credit`() {
        val p = txn(
            "Your account XXXX1234 has been credited with AED 8,000.00 - SALARY on 01/09/2026. " +
                "Available balance AED 13,230.75"
        )
        assertEquals(800_000L, p.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
    }

    @Test
    fun `atm withdrawal falls back to a sensible merchant`() {
        val p = txn("AED 500.00 withdrawn from ADCB ATM using Card ending 4471 on 16/09/2026. Avl Bal AED 2,100.00")
        assertEquals(50_000L, p.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("ATM", p.merchant)
    }

    // ---- the traps ----------------------------------------------------------

    @Test
    fun `the words credit card do not make a purchase into income`() {
        val p = txn("Your Emirates NBD Credit Card ending 1234 has been used for AED 75.00 at STARBUCKS on 12/09/2026")
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(7_500L, p.amountMinor)
    }

    @Test
    fun `an OTP quoting an amount is not a transaction`() {
        // Without this the same payment is counted twice: once from the OTP and
        // once from the receipt that follows it.
        assertEquals(
            SmsParser.Outcome.Ignore,
            SmsParser.classify(
                "123456 is your OTP for a transaction of AED 500.00 on your Card ending 1234. Do not share."
            )
        )
    }

    @Test
    fun `a promotion quoting a price is not a transaction`() {
        assertEquals(
            SmsParser.Outcome.Ignore,
            SmsParser.classify("Enjoy 20% discount at NOON with your Card. Offer valid till 30/09. Terms and conditions apply.")
        )
    }

    @Test
    fun `a balance enquiry is not a transaction`() {
        assertEquals(
            SmsParser.Outcome.Ignore,
            SmsParser.classify("Your account available balance is AED 5,230.75")
        )
    }

    @Test
    fun `money moved but unreadable goes to the bucket rather than being dropped`() {
        // Direction is clear, the amount is not quoted in any currency we know.
        val outcome = SmsParser.classify("Your card ending 1234 has been debited. Check the app for details.")
        assertEquals(SmsParser.Outcome.Unreadable, outcome)
    }

    @Test
    fun `merchant is not picked up as your account`() {
        val p = txn("AED 200.00 transferred to your account ending 9999 on 12/09/2026")
        // "your account" must be rejected as a merchant name.
        assertTrue("merchant should not start with 'your', was ${p.merchant}", p.merchant != "your account ending 9999")
    }

    @Test
    fun `foreign currency is carried through`() {
        val p = txn("Your Card ending 1234 has been used for USD 19.99 at APPLE.COM on 12/09/2026")
        assertEquals("USD", p.currency)
        assertEquals(1_999L, p.amountMinor)
    }

    @Test
    fun `no card digits still parses`() {
        val p = txn("AED 35.00 paid to SALIK on 12/09/2026")
        assertEquals(3_500L, p.amountMinor)
        assertNull(p.last4)
        assertEquals("SALIK", p.merchant)
    }

    // ---- identity -----------------------------------------------------------

    @Test
    fun `the same message always hashes the same, a different one does not`() {
        val a = SmsParser.sourceHash(1_000L, "ADCB", "AED 10.00 debited at X")
        val b = SmsParser.sourceHash(1_000L, "adcb ", "AED 10.00 debited at X ")
        val c = SmsParser.sourceHash(1_001L, "ADCB", "AED 10.00 debited at X")
        assertEquals("sender case and surrounding space must not change identity", a, b)
        assertNotNull(c)
        assertTrue("a different timestamp is a different message", a != c)
    }
}
