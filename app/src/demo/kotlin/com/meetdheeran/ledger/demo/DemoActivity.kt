package com.meetdheeran.ledger.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.meetdheeran.ledger.MainViewModel
import com.meetdheeran.ledger.core.Prefs
import com.meetdheeran.ledger.data.LedgerDb
import com.meetdheeran.ledger.sms.SmsImporter
import com.meetdheeran.ledger.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Separate APK and data sandbox. This variant cannot read or receive real SMS. */
class DemoActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch {
            // The samples run to the 25th, so anchor them to a month that has
            // already reached it - otherwise early in a month most would be dated
            // in the future. Reseed whenever that month moves on; seeding only
            // once left a demo opened a month later showing nothing at all.
            val today = LocalDate.now()
            val month = if (today.dayOfMonth >= 25) YearMonth.now() else YearMonth.now().minusMonths(1)
            val demoPrefs = getSharedPreferences("demo", MODE_PRIVATE)
            if (demoPrefs.getString("seeded_month", null) != month.toString()) {
                withContext(Dispatchers.IO) { LedgerDb.get(this@DemoActivity).clearAllTables() }
                val messages = mutableListOf<SmsImporter.Message>()
                fun sample(offset: Long, day: Int, sender: String, body: String) {
                    val date = month.minusMonths(offset).atDay(day)
                    val timestamp = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    messages += SmsImporter.Message(timestamp, sender, body)
                }
                for (offset in 5L downTo 1L) {
                    sample(offset, 12, "ADCB", "Purchase of AED ${1900 + offset * 175}.00 at CARREFOUR with card ending 1234")
                    sample(offset, 18, "EmiratesNBD", "Purchase of AED ${900 + offset * 60}.00 at IKEA with card ending 5678")
                }
                sample(0, 1, "ADCB", "Salary of AED 18000.00 credited to your account XXXX9000")
                sample(0, 3, "ADCB", "AED 780.00 paid to DEWA from your account XXXX9000")
                sample(0, 5, "EmiratesNBD", "Purchase of AED 1499.00 at IKEA with card ending 5678")
                sample(0, 10, "ADCB", "AED 2000.00 sent to beneficiary for transfer")
                sample(0, 11, "ADCB", "AED 500.00 received from family by transfer")
                sample(0, 14, "ADCB", "AED 3500.00 debited from your account for credit card payment")
                sample(0, 18, "ADCB", "Purchase of AED 328.50 at CARREFOUR with card ending 1234")
                sample(0, 20, "EmiratesNBD", "Purchase of AED 79.00 at NETFLIX with card ending 5678")
                sample(0, 21, "ADCB", "Purchase of AED 165.00 at ADNOC with card ending 1234")
                sample(0, 22, "ADCB", "AED 250.00 refunded to your card ending 1234")
                sample(0, 23, "EmiratesNBD", "Purchase of AED 245.00 at NOON.COM with card ending 5678")
                sample(0, 24, "ADCB", "Purchase of AED 87.50 at TALABAT with card ending 1234")
                sample(0, 25, "ADCB", "Your account has been debited with AED 150.00")
                SmsImporter.ingestAll(this@DemoActivity, messages)
                Prefs.setBudget(this@DemoActivity, month.toString(), 600_000L)
                val dao = LedgerDb.get(this@DemoActivity).dao()
                dao.findCard("ADCB", "1234")?.let { dao.renameCard(it.id, "Everyday card") }
                dao.findCard("Emirates NBD", "5678")?.let { dao.renameCard(it.id, "Shopping card") }
                Prefs.setBackfillDone(this@DemoActivity, true)
                demoPrefs.edit().putString("seeded_month", month.toString()).apply()
            }
            if (month != YearMonth.now()) vm.changeMonth(-1)
            setContent {
                LedgerTheme {
                    Column(Modifier.fillMaxSize().background(Ink.bg)) {
                        Text("DEMO · FICTIONAL TRANSACTIONS", color = Ink.warn, fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 5.dp))
                        Box(Modifier.weight(1f)) { HomeScreen(vm) }
                    }
                }
            }
        }
    }
}
