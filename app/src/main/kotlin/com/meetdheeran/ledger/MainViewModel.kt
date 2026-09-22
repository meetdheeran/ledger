package com.meetdheeran.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meetdheeran.ledger.core.Lock
import com.meetdheeran.ledger.core.Prefs
import com.meetdheeran.ledger.data.Category
import com.meetdheeran.ledger.data.LedgerDb
import com.meetdheeran.ledger.data.MerchantRule
import com.meetdheeran.ledger.data.Txn
import com.meetdheeran.ledger.debug.SampleMessages
import com.meetdheeran.ledger.sms.SmsImporter
import com.meetdheeran.ledger.ui.monthBounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which screen the app should be showing before any data is on display. */
sealed interface Gate {
    data object Loading : Gate
    data object CreatePassword : Gate
    data object Unlock : Gate
    data object NeedPermission : Gate
    data class Importing(val note: String) : Gate
    data object Ready : Gate
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = LedgerDb.get(app)
    private val dao = db.dao()

    private val _gate = MutableStateFlow<Gate>(Gate.Loading)
    val gate: StateFlow<Gate> = _gate

    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError

    /** Summary of the last import, shown once so the user knows what happened. */
    private val _lastImport = MutableStateFlow<SmsImporter.Result?>(null)
    val lastImport: StateFlow<SmsImporter.Result?> = _lastImport

    private val bounds = monthBounds()

    val cards = dao.cards().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent = dao.recentTxns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val spendThisMonth = dao.spendBetween(bounds.first, bounds.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val incomeThisMonth = dao.incomeBetween(bounds.first, bounds.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val byCategory = dao.spendByCategory(bounds.first, bounds.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val byCard = dao.spendByCard(bounds.first, bounds.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unparsed = dao.unparsed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unparsedCount = dao.unparsedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init { decideGate(unlocked = false) }

    private fun decideGate(unlocked: Boolean) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            when {
                !Lock.isSet(ctx) -> _gate.value = Gate.CreatePassword
                !unlocked -> _gate.value = Gate.Unlock
                !SmsImporter.hasReadPermission(ctx) -> _gate.value = Gate.NeedPermission
                !Prefs.backfillDone(ctx) -> runBackfill()
                else -> _gate.value = Gate.Ready
            }
        }
    }

    fun createPassword(password: String) {
        viewModelScope.launch {
            Lock.setPassword(getApplication(), password)
            decideGate(unlocked = true)
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch {
            if (Lock.verify(getApplication(), password)) {
                _unlockError.value = null
                decideGate(unlocked = true)
            } else {
                _unlockError.value = "That is not the password."
            }
        }
    }

    /**
     * Called when the app leaves the foreground. Only ever locks a session that
     * was already open - an import in progress is left to finish rather than
     * being thrown away half done.
     */
    fun relock() {
        if (_gate.value == Gate.Ready) {
            _unlockError.value = null
            _gate.value = Gate.Unlock
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) decideGate(unlocked = true)
        // If refused, the gate stays on NeedPermission: without the messages
        // there is genuinely nothing for the app to show.
    }

    fun runBackfill() {
        viewModelScope.launch {
            _gate.value = Gate.Importing("Reading the last ${SmsImporter.BACKFILL_DAYS} days")
            val result = SmsImporter.backfill(getApplication())
            Prefs.setBackfillDone(getApplication(), true)
            _lastImport.value = result
            _gate.value = Gate.Ready
        }
    }

    /** Manual re-scan from the dashboard. Idempotent, so it can be tapped freely. */
    fun rescan() {
        viewModelScope.launch {
            _lastImport.value = SmsImporter.backfill(getApplication())
        }
    }

    fun dismissImportNote() { _lastImport.value = null }

    /**
     * Debug builds only. Feeds invented bank messages straight into the importer
     * so the pipeline can be tested on a phone with no SIM. Idempotent like any
     * other import, so tapping it twice changes nothing.
     */
    fun loadSamples() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val messages = SampleMessages.all.map {
                SmsImporter.Message(now - it.daysAgo * 86_400_000L, it.sender, it.body)
            }
            _lastImport.value = SmsImporter.ingestAll(getApplication(), messages)
        }
    }

    /**
     * Re-categorising applies to every past transaction of that merchant and is
     * remembered for future imports, so the correction only has to be made once.
     */
    fun recategorise(txn: Txn, category: Category) {
        viewModelScope.launch {
            val key = txn.merchant.uppercase()
            dao.upsertMerchantRule(MerchantRule(key, category))
            dao.recategoriseMerchant(key, category)
        }
    }

    fun renameCard(cardId: Long, label: String?) {
        viewModelScope.launch { dao.renameCard(cardId, label?.takeIf { it.isNotBlank() }) }
    }

    fun dismissUnparsed(id: Long) {
        viewModelScope.launch { dao.deleteUnparsed(id) }
    }
}
