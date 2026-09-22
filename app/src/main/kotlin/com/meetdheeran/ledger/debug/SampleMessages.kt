package com.meetdheeran.ledger.debug

/**
 * Invented bank messages, written in the shape UAE banks use, for exercising
 * the whole pipeline on a phone that has no SIM and therefore no real bank SMS.
 *
 * Reachable only from debug builds. Nothing here is real data - the values are
 * made up, and the point is the grammar.
 *
 * The set is chosen to cover what actually goes wrong rather than just the
 * happy path: a credit card purchase, an OTP quoting an amount, a promotion
 * quoting a price, a foreign currency, an ATM withdrawal, a salary credit, and
 * one message that deliberately cannot be read so the Inbox tab has something
 * in it.
 */
data class SampleSms(val sender: String, val daysAgo: Int, val body: String)

object SampleMessages {

    val all = listOf(
        SampleSms("EmiratesNBD", 1,
            "Your Card ending 4471 has been used for AED 68.50 at CARREFOUR MARKET DUBAI on 21/09/2026. Available Balance: AED 5,230.75"),

        SampleSms("EmiratesNBD", 2,
            "Your Card ending 4471 has been used for AED 22.00 at STARBUCKS MIRDIF on 20/09/2026. Available Balance: AED 5,299.25"),

        SampleSms("ADCB", 3,
            "Dear Customer, AED 145.75 has been debited from your ADCB Card XXXX8890 at TALABAT on 19-09-2026."),

        SampleSms("ADCB", 4,
            "Dear Customer, AED 500.00 withdrawn from ADCB ATM using Card ending 8890 on 18-09-2026. Avl Bal AED 2,100.00"),

        SampleSms("FAB", 5,
            "Purchase of AED 1,250.00 at IKEA DUBAI FESTIVAL with FAB Card ending 3321 on 17/09/2026. Avl Bal AED 12,004.20"),

        SampleSms("Mashreq", 6,
            "AED 45.00 spent on your Mashreq Debit Card ending 7712 at ADNOC DISTRIBUTION on 16/09/2026"),

        // The trap: "Credit Card" must not turn a purchase into income.
        SampleSms("EmiratesNBD", 7,
            "Your Emirates NBD Credit Card ending 4471 has been used for AED 310.00 at NOON.COM on 15/09/2026"),

        // Must be ignored. It quotes the same amount as the purchase above and
        // would double-count it.
        SampleSms("EmiratesNBD", 7,
            "483920 is your OTP for a transaction of AED 310.00 on your Card ending 4471. Do not share this with anyone."),

        // Must be ignored: a price in an advert is not a transaction.
        SampleSms("ADCB", 9,
            "Enjoy 20% discount at NOON with your ADCB Card this weekend. Offer valid till 30/09. Terms and conditions apply."),

        SampleSms("FAB", 10,
            "Your account XXXX3321 has been credited with AED 8,000.00 - SALARY on 12/09/2026. Available balance AED 13,230.75"),

        SampleSms("Mashreq", 12,
            "AED 35.00 paid to SALIK on 10/09/2026"),

        SampleSms("EmiratesNBD", 14,
            "Your Card ending 4471 has been used for USD 19.99 at APPLE.COM/BILL on 08/09/2026"),

        // Deliberately unreadable: money clearly moved, but no amount is quoted.
        // Belongs in the Inbox tab, not silently dropped.
        SampleSms("RAKBANK", 16,
            "Your RAKBANK card ending 9021 has been debited. Please check the RAKBANK app for details."),

        SampleSms("ADCB", 18,
            "Dear Customer, AED 89.00 has been debited from your ADCB Card XXXX8890 at LULU HYPERMARKET on 04-09-2026."),

        SampleSms("EmiratesNBD", 20,
            "Your Card ending 4471 has been used for AED 55.00 at CAREEM on 02/09/2026. Available Balance: AED 5,410.00"),

        SampleSms("FAB", 24,
            "AED 420.00 paid to DEWA from your FAB account ending 3321 on 29/08/2026")
    )
}
