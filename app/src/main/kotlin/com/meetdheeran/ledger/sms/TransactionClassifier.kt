package com.meetdheeran.ledger.sms

import com.meetdheeran.ledger.data.Direction
import com.meetdheeran.ledger.data.TransactionKind

/** Conservative, ordered evidence rules. A debit alone never proves spending. */
internal object TransactionClassifier {
    data class Decision(val kind: TransactionKind, val direction: Direction)

    private fun String.has(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(this)

    fun isNonPosting(body: String): Boolean = body.has(
        """\b(declined|unsuccessful|failed|not processed|not successful|insufficient funds|payment reminder|minimum (?:amount )?due|payment (?:is )?due|total (?:amount )?due|statement (?:is |has been )?(?:ready|generated)|will be (?:debited|credited|charged)|scheduled|pending|being processed|initiated|request (?:received|submitted))\b"""
    ) && !body.has("""\b(refunded|reversed)\b""")

    fun classify(body: String, merchant: String?): Decision? {
        // Availability of credit is not incoming money. Ignore the trailing balance clause.
        val semanticBody = if (merchant == null) body else body.replace(
            Regex("""(?i)\bat\s+${Regex.escape(merchant)}"""), "at merchant")
        val b = semanticBody.lowercase().split(Regex("""\b(?:available|avl|avbl)\s+(?:balance|bal|credit|limit|lmt|cr\.?\s*lmt)\b|\boutstanding balance\b"""), limit = 2)[0]
        val card = b.has("""\b(?:credit\s*card|debit\s*card|card)\b""")
        val debit = b.has("""\b(debited|debit of|spent|purchase|withdrawn|withdrawal|paid|charged|deducted|sent|transferred to|used for|has been used|pos txn|pos transaction)\b""")
        val credit = b.has("""\b(credited|credit of|received|deposited|deposit of|added to|transferred from)\b""")
        val incoming = b.has("""\b(?:received from|transferred (?:in)?to your (?:account|a/c)|inward (?:transfer|remittance)|incoming (?:transfer|payment)|credited (?:in)?to your (?:account|a/c))\b""")
        val outgoing = b.has("""\b(?:sent to|transferred to (?:a |the )?beneficiary|transferred from your|outward (?:transfer|remittance)|outgoing (?:transfer|payment)|debited from your (?:account|a/c))\b""")
        val direction = when {
            outgoing -> Direction.DEBIT
            incoming -> Direction.CREDIT
            debit && !credit -> Direction.DEBIT
            credit && !debit -> Direction.CREDIT
            else -> null
        }
        fun decision(kind: TransactionKind, fallback: Direction = Direction.DEBIT) =
            Decision(kind, kind.direction ?: direction ?: fallback)

        // These reverse or settle a previous expense; they are not a new purchase.
        if (b.has("""\b(refund(?:ed)?|reversed|reversal|cashback|cash back)\b""")) {
            // A reversed incoming transfer is not a purchase refund.
            if (b.has("""\b(?:transfer|remittance|salary|deposit)\b""")) return decision(TransactionKind.REVIEW)
            return decision(TransactionKind.REFUND)
        }
        val repayment = (card && b.has("""\b(?:credit\s*card (?:bill )?(?:payment|repayment)|card repayment|payment towards|payment to (?:your )?(?:credit )?card|paid towards (?:your )?(?:credit )?card)\b""")) ||
            (card && b.has("""\bpayment\b""") && b.has("""\b(?:received|credited|thank you|thankyou)\b""")) ||
            (card && b.has("""\b(?:autopay|auto debit|auto-debit|direct debit)\b"""))
        if (repayment) return decision(TransactionKind.CARD_REPAYMENT, Direction.CREDIT)

        if (b.has("""\b(?:salary|payroll|wps)\b""") && (direction == Direction.CREDIT || (!debit && !b.has("""\bsalary account\b"""))))
            return decision(TransactionKind.SALARY)

        val transfer = b.has("""\b(?:transfer(?:red)?|remittance|remitted|sent to|received from|ipp|aani|beneficiary)\b""")
        if (transfer) return when (direction) {
            Direction.CREDIT -> decision(TransactionKind.TRANSFER_IN)
            Direction.DEBIT -> decision(TransactionKind.TRANSFER_OUT)
            null -> decision(TransactionKind.REVIEW)
        }
        if (b.has("""\b(?:cash withdrawal|withdrawn|withdrawal|cash advance)\b""")) return decision(TransactionKind.CASH_WITHDRAWAL)
        if (b.has("""\b(?:fee|fees|finance charge|interest charged|interest has been charged)\b""") && debit && !credit)
            return decision(TransactionKind.FEE)

        if (direction == Direction.CREDIT) {
            if (card) return decision(TransactionKind.REVIEW)
            if (b.has("""\b(?:interest|dividend|profit payment)\b""")) return decision(TransactionKind.OTHER_INCOME)
            // Unspecified money arriving is not assumed to be earned income.
            return decision(TransactionKind.TRANSFER_IN)
        }
        if (debit && credit && direction == null) return decision(TransactionKind.REVIEW)
        val knownBiller = merchant?.has("""\b(?:DEWA|SEWA|ADDC|AADC|ETISALAT|DU|EMPOWER|TABREED|SALIK|RTA)\b""") == true
        if (!card && merchant != null && (b.has("""\b(?:bill payment|utility payment)\b""") ||
                (knownBiller && b.has("""\b(?:paid to|payment to|payment towards)\b""")) ||
                // "AED 150 debited from your account towards DEWA": a known biller is enough.
                (knownBiller && debit && b.has("""\btowards\b"""))))
            return decision(TransactionKind.BILL_PAYMENT)
        if (b.has("""\b(?:purchase|pos txn|pos transaction|spent)\b""") ||
            (card && debit && merchant != null && b.has("""\bat\b"""))) return decision(TransactionKind.PURCHASE)

        if (debit || credit || b.has("""\b(?:payment|salary|transaction|txn|trx)\b""")) return decision(TransactionKind.REVIEW)
        return null
    }
}
