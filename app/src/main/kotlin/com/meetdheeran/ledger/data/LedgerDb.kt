package com.meetdheeran.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Card::class, Txn::class, MerchantRule::class, UnparsedSms::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LedgerDb : RoomDatabase() {

    abstract fun dao(): LedgerDao

    companion object {
        @Volatile private var instance: LedgerDb? = null

        fun get(context: Context): LedgerDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDb::class.java,
                    "ledger.db"
                ).build().also { instance = it }
            }
    }
}
