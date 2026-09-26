package com.meetdheeran.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Card::class, Txn::class, MerchantRule::class, UnparsedSms::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LedgerDb : RoomDatabase() {

    abstract fun dao(): LedgerDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing rows are excluded from spending until reparsed on unlock.
                db.execSQL("ALTER TABLE txns ADD COLUMN kind TEXT NOT NULL DEFAULT 'REVIEW'")
                db.execSQL("ALTER TABLE txns ADD COLUMN classificationOverridden INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE txns ADD COLUMN parserVersion INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: LedgerDb? = null

        fun get(context: Context): LedgerDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDb::class.java,
                    "ledger.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
