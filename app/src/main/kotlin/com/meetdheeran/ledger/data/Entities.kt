package com.meetdheeran.ledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** Money moving out of an account, or into it. */
enum class Direction { DEBIT, CREDIT }

enum class Category(val label: String) {
    FOOD("Food & drink"),
    GROCERIES("Groceries"),
    TRANSPORT("Transport"),
    SHOPPING("Shopping"),
    BILLS("Bills & utilities"),
    CASH("Cash"),
    TRANSFER("Transfers"),
    HEALTH("Health"),
    ENTERTAINMENT("Entertainment"),
    INCOME("Income"),
    OTHER("Other")
}

/**
 * A card, created the first time one is seen in a message rather than set up by
 * hand. Identified by its last four digits within a bank, because that is all
 * the SMS ever reveals. [label] is the name the user can give it later.
 */
@Entity(
    tableName = "cards",
    indices = [Index(value = ["bank", "last4"], unique = true)]
)
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bank: String,
    val last4: String,
    val label: String? = null,
    val firstSeen: Long
) {
    /** What to show in the UI: the user's name for it, else the digits. */
    val displayName: String get() = label ?: "$bank ••$last4"
}

/**
 * One parsed transaction.
 *
 * Amounts are held in minor units (fils for AED) as a Long. Money in a
 * floating-point type accumulates rounding error the moment you start summing
 * it, which for a spending total is the one thing that must not happen.
 *
 * [sourceHash] is derived from the message itself and is unique, so importing
 * the same SMS twice - which happens every time a backfill re-runs - is a
 * no-op rather than a duplicate.
 */
@Entity(
    tableName = "txns",
    indices = [
        Index(value = ["sourceHash"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["cardLast4"])
    ]
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceHash: String,
    val timestamp: Long,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val merchant: String,
    val cardLast4: String?,
    val bank: String,
    val category: Category,
    val balanceMinor: Long? = null,
    val body: String
)

/**
 * A user's correction, remembered. Re-categorising "CARREFOUR" once means every
 * future Carrefour transaction lands in the same place.
 */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val category: Category
)

/**
 * A bank message the rules could not read. Kept and shown rather than dropped:
 * a spending total that silently omits what it did not understand is worse than
 * one that admits the gap.
 */
@Entity(
    tableName = "unparsed",
    indices = [Index(value = ["sourceHash"], unique = true)]
)
data class UnparsedSms(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceHash: String,
    val timestamp: Long,
    val sender: String,
    val body: String
)

class Converters {
    @TypeConverter fun fromDirection(d: Direction): String = d.name
    @TypeConverter fun toDirection(s: String): Direction =
        runCatching { Direction.valueOf(s) }.getOrDefault(Direction.DEBIT)

    @TypeConverter fun fromCategory(c: Category): String = c.name
    @TypeConverter fun toCategory(s: String): Category =
        runCatching { Category.valueOf(s) }.getOrDefault(Category.OTHER)
}
