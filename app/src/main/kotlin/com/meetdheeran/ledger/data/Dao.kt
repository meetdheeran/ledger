package com.meetdheeran.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Spend grouped by card, for the dashboard breakdown. */
data class CardTotal(
    val bank: String,
    val cardLast4: String?,
    val totalMinor: Long,
    val txnCount: Int
)

/** Spend grouped by category. */
data class CategoryTotal(
    val category: Category,
    val totalMinor: Long
)

@Dao
interface LedgerDao {
    @Update
    suspend fun updateTxn(txn: Txn)

    @Query("SELECT * FROM txns WHERE sourceHash = :hash LIMIT 1")
    suspend fun findTxn(hash: String): Txn?

    @Query("SELECT * FROM txns WHERE parserVersion < :version")
    suspend fun outdatedTxns(version: Int): List<Txn>

    @Query("SELECT * FROM unparsed")
    suspend fun allUnparsed(): List<UnparsedSms>

    @Query("DELETE FROM txns WHERE sourceHash = :hash")
    suspend fun deleteTxnByHash(hash: String)

    @Query("DELETE FROM unparsed WHERE sourceHash = :hash")
    suspend fun deleteUnparsedByHash(hash: String)

    @Query("DELETE FROM cards WHERE NOT EXISTS (SELECT 1 FROM txns WHERE txns.bank = cards.bank AND txns.cardLast4 = cards.last4)")
    suspend fun removeUnreferencedCards()

    @Query("UPDATE txns SET kind = :kind, direction = :direction, classificationOverridden = 1 WHERE id = :id")
    suspend fun setTxnKind(id: Long, kind: TransactionKind, direction: Direction)

    @Query("SELECT COUNT(*) FROM txns WHERE kind = 'REVIEW'")
    fun reviewCount(): Flow<Int>

    // ---- writes -------------------------------------------------------------
    // IGNORE everywhere: both the backfill and the live receiver can present the
    // same message, and re-running a backfill must not double-count.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTxn(txn: Txn): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUnparsed(sms: UnparsedSms): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCard(card: Card): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMerchantRule(rule: MerchantRule)

    @Query("UPDATE cards SET label = :label WHERE id = :cardId")
    suspend fun renameCard(cardId: Long, label: String?)

    @Query("UPDATE txns SET category = :category WHERE id = :txnId")
    suspend fun setTxnCategory(txnId: Long, category: Category)

    /** Applies a corrected category to every past transaction of that merchant. */
    @Query("UPDATE txns SET category = :category WHERE UPPER(merchant) = :merchantKey")
    suspend fun recategoriseMerchant(merchantKey: String, category: Category)

    @Query("DELETE FROM unparsed WHERE id = :id")
    suspend fun deleteUnparsed(id: Long)

    // ---- cards --------------------------------------------------------------

    @Query("SELECT * FROM cards ORDER BY firstSeen ASC")
    fun cards(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE bank = :bank AND last4 = :last4 LIMIT 1")
    suspend fun findCard(bank: String, last4: String): Card?

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun cardCount(): Int

    // ---- transactions -------------------------------------------------------

    @Query("SELECT * FROM txns ORDER BY timestamp DESC")
    fun recentTxns(): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE cardLast4 = :last4 ORDER BY timestamp DESC")
    fun txnsForCard(last4: String): Flow<List<Txn>>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM txns
        WHERE kind IN ('PURCHASE', 'BILL_PAYMENT', 'FEE') AND currency = 'AED'
        AND timestamp >= :from AND timestamp <= :to
        """
    )
    fun spendBetween(from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM txns
        WHERE kind IN ('SALARY', 'OTHER_INCOME') AND currency = 'AED'
        AND timestamp >= :from AND timestamp <= :to
        """
    )
    fun incomeBetween(from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT bank AS bank, cardLast4 AS cardLast4,
               COALESCE(SUM(amountMinor), 0) AS totalMinor,
               COUNT(*) AS txnCount
        FROM txns
        WHERE kind IN ('PURCHASE', 'BILL_PAYMENT', 'FEE') AND currency = 'AED'
        AND timestamp >= :from AND timestamp <= :to
        GROUP BY bank, cardLast4
        ORDER BY totalMinor DESC
        """
    )
    fun spendByCard(from: Long, to: Long): Flow<List<CardTotal>>

    @Query(
        """
        SELECT category AS category, COALESCE(SUM(amountMinor), 0) AS totalMinor
        FROM txns
        WHERE kind IN ('PURCHASE', 'BILL_PAYMENT', 'FEE') AND currency = 'AED'
        AND timestamp >= :from AND timestamp <= :to
        GROUP BY category
        ORDER BY totalMinor DESC
        """
    )
    fun spendByCategory(from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM txns WHERE kind = :kind AND currency = 'AED' AND timestamp >= :from AND timestamp <= :to")
    fun totalByKind(kind: TransactionKind, from: Long, to: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM txns WHERE currency != 'AED' AND timestamp >= :from AND timestamp <= :to")
    fun foreignCurrencyCount(from: Long, to: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM txns")
    suspend fun txnCount(): Int

    // ---- merchant rules -----------------------------------------------------

    @Query("SELECT * FROM merchant_rules")
    suspend fun allMerchantRules(): List<MerchantRule>

    // ---- unparsed -----------------------------------------------------------

    @Query("SELECT * FROM unparsed ORDER BY timestamp DESC")
    fun unparsed(): Flow<List<UnparsedSms>>

    @Query("SELECT COUNT(*) FROM unparsed")
    fun unparsedCount(): Flow<Int>
}
