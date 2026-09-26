package com.meetdheeran.ledger.sms

import com.meetdheeran.ledger.data.Direction
import com.meetdheeran.ledger.data.TransactionKind
import org.junit.Assert.*
import org.junit.Test

/** Synthetic examples; no private inbox data or claim of bank-certified templates. */
class TransactionClassificationTest {
    private fun parsed(body: String): SmsParser.Parsed {
        val result = SmsParser.classify(body)
        assertTrue("Expected transaction for: $body, got $result", result is SmsParser.Outcome.Transaction)
        return (result as SmsParser.Outcome.Transaction).parsed
    }

    private fun kind(expected: TransactionKind, body: String) {
        val result = parsed(body)
        assertEquals(body, expected, result.kind)
        expected.direction?.let { assertEquals(body, it, result.direction) }
    }

    @Test fun `purchases across card names remain spending`() {
        listOf("Emirates NBD", "ADCB", "FAB", "Mashreq", "RAKBANK", "DIB", "ADIB", "CBD", "HSBC", "Wio", "Liv").forEach { bank ->
            kind(TransactionKind.PURCHASE, "Your $bank Credit Card ending 1234 has been used for AED 150.00 at CARREFOUR on 25/09/2026. Available credit AED 9,000.00")
        }
        kind(TransactionKind.PURCHASE, "Purchase of AED 45.00 at TALABAT with your debit card ending 4321")
        kind(TransactionKind.PURCHASE, "AED 75.00 debited from Card XXXX1234 at NOON.COM.")
    }

    @Test fun `salary is income even when debit card is mentioned`() {
        kind(TransactionKind.SALARY, "Your account XXXX1234 linked to debit card XXXX9999 has been credited with AED 18000.00 as SALARY")
        kind(TransactionKind.SALARY, "WPS payment of AED 8000.00 received in your account")
        kind(TransactionKind.SALARY, "Salary of AED 12000.00 credited to your account")
    }

    @Test fun `incoming and outgoing transfers are never spending`() {
        kind(TransactionKind.TRANSFER_OUT, "AED 2000.00 debited from your account XXXX1234 for transfer to beneficiary. Available balance AED 5000.00")
        kind(TransactionKind.TRANSFER_OUT, "AED 500.00 sent to Ali through Aani")
        kind(TransactionKind.TRANSFER_OUT, "AED 400.00 transferred to beneficiary. Their account has been credited.")
        kind(TransactionKind.TRANSFER_IN, "AED 300.00 transferred to your account ending 1234")
        kind(TransactionKind.TRANSFER_IN, "Inward remittance of AED 2500.00 credited to your account")
        kind(TransactionKind.TRANSFER_IN, "You received AED 100.00 from Ahmed via Aani")
        kind(TransactionKind.TRANSFER_IN, "AED 750.00 has been credited to your account XXXX1234")
    }

    @Test fun `repayment on either side is not a new expense`() {
        kind(TransactionKind.CARD_REPAYMENT, "AED 2500.00 debited from your account for credit card payment")
        kind(TransactionKind.CARD_REPAYMENT, "Thank you. Payment of AED 2500.00 received towards your credit card ending 1234")
        kind(TransactionKind.CARD_REPAYMENT, "Credit card ending 1234 payment of AED 200.00 has been received")
        kind(TransactionKind.CARD_REPAYMENT, "AED 1000.00 auto-debit processed for your credit card ending 1234")
    }

    @Test fun `refund takes priority over original debit wording`() {
        kind(TransactionKind.REFUND, "Purchase of AED 150.00 at CARREFOUR on card ending 1234 has been reversed")
        kind(TransactionKind.REFUND, "AED 50.00 refund credited to your card XXXX1234 from NOON")
        kind(TransactionKind.REFUND, "Cashback of AED 25.00 credited to your credit card")
        kind(TransactionKind.REVIEW, "Incoming transfer of AED 500.00 has been reversed")
    }

    @Test fun `cash fees and bills have separate purposes`() {
        kind(TransactionKind.CASH_WITHDRAWAL, "AED 500.00 withdrawn from ADCB ATM using Card ending 1234")
        kind(TransactionKind.FEE, "Annual fee of AED 300.00 charged to your card ending 1234")
        kind(TransactionKind.BILL_PAYMENT, "AED 420.00 paid to DEWA from your account ending 5678")
        kind(TransactionKind.OTHER_INCOME, "Interest of AED 20.00 credited to your account")
    }

    @Test fun `ambiguous debits do not inflate spending`() {
        kind(TransactionKind.REVIEW, "Your account has been debited with AED 500.00")
        kind(TransactionKind.REVIEW, "Payment of AED 500.00 processed")
        kind(TransactionKind.REVIEW, "AED 500.00 credited to your credit card")
        kind(TransactionKind.REVIEW, "Transfer of AED 500.00 processed")
        kind(TransactionKind.REVIEW, "AED 500.00 paid to Ahmed from your account")
    }

    @Test fun `non completed events and reminders are ignored`() {
        listOf(
            "Card purchase of AED 500.00 at IKEA was declined",
            "Payment of AED 500.00 failed",
            "AED 500.00 will be debited for your credit card payment",
            "Minimum amount due AED 50.00. Total amount due AED 900.00",
            "Payment of AED 500.00 is pending",
            "Transfer of AED 500.00 initiated",
            "OTP 123456 for purchase of AED 500.00 at IKEA"
        ).forEach { assertEquals(it, SmsParser.Outcome.Ignore, SmsParser.classify(it)) }
    }

    @Test fun `accounts do not turn into cards`() {
        assertNull(parsed("Salary of AED 8000.00 credited to your account XXXX1234").last4)
        assertNull(parsed("AED 200.00 transferred to your account ending 9876").last4)
        assertEquals("4321", parsed("AED 500.00 debited from account XXXX9876 for credit card payment to card XXXX4321").last4)
    }

    @Test fun `multiple ambiguous amounts require review`() {
        kind(TransactionKind.REVIEW, "Purchase of USD 20.00 at NOON with card XXXX1234 equivalent AED 74.00")
        kind(TransactionKind.REVIEW, "Transfer of AED 500.00 debited plus fee AED 5.00")
    }

    @Test fun `ordinary merchant names containing balance fragment do not lose amount`() {
        val p = parsed("Purchase at GLOBAL for AED 100.00 with card XXXX1234. Balance AED 900.00")
        assertEquals(10000L, p.amountMinor)
        assertEquals(TransactionKind.PURCHASE, p.kind)
    }

    @Test fun `spending policy excludes money movement and refunds`() {
        assertEquals(setOf(TransactionKind.PURCHASE, TransactionKind.BILL_PAYMENT, TransactionKind.FEE),
            TransactionKind.entries.filter { it.countsAsSpending }.toSet())
    }

    @Test fun `currency variants are recognised but unsupported Arabic wording goes to inbox`() {
        assertEquals(10000L, parsed("Purchase of Dhs. 100.00 at LULU with card XXXX1234").amountMinor)
        assertEquals(10000L, parsed("Purchase of AED ١٠٠٫٠٠ at LULU with card XXXX1234").amountMinor)
        assertEquals(SmsParser.Outcome.Unreadable, SmsParser.classify("تم خصم ١٠٠٫٠٠ درهم من حسابك"))
    }

    @Test fun `merchant names do not override the purpose of a purchase`() {
        kind(TransactionKind.PURCHASE, "Purchase of AED 50.00 at CASHBACK CAFE on 25/09/2026 with card XXXX1234")
        kind(TransactionKind.PURCHASE, "Purchase of AED 50.00 at TRANSFER SHOP on 25/09/2026 with card XXXX1234")
    }
}
