package com.meetdheeran.ledger.sms

import android.content.Context
import com.meetdheeran.ledger.data.Category
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.bankSenderStore by preferencesDataStore(name = "bank_senders")

/**
 * The parts of parsing that vary between banks and countries live in
 * `assets/rules.json`, not in code: which sender IDs belong to a bank, and
 * which merchant names belong to which category. The grammar of a transaction
 * message - an amount, a direction, a card, a merchant - is general, so that
 * stays in [SmsParser].
 *
 * Adding a bank or a merchant is therefore a data change, which is what makes
 * "works for other people's banks too" a realistic goal rather than a rewrite.
 */
@Serializable
data class BankRule(
    val name: String,
    val senders: List<String>,
    val exactSender: Boolean = false
)

@Serializable
data class RulesFile(
    val banks: List<BankRule> = emptyList(),
    val merchantCategories: Map<String, String> = emptyMap()
)

object Rules {
    private val senderKey = stringPreferencesKey("custom_senders")

    suspend fun loadForImport(context: Context): RulesFile {
        val base = load(context)
        val custom = customSenders(context)
        return base.copy(banks = custom + base.banks)
    }

    private suspend fun customSenders(context: Context): List<BankRule> =
        context.bankSenderStore.data.first()[senderKey]?.let {
            json.decodeFromString<List<BankRule>>(it)
        } ?: emptyList()

    /** Explicitly trusted by the user; an exact match never widens to other senders. */
    suspend fun addSender(context: Context, sender: String, bank: String) {
        require(normalise(sender).isNotEmpty() && bank.isNotBlank())
        context.bankSenderStore.edit { prefs ->
            val current = prefs[senderKey]?.let { json.decodeFromString<List<BankRule>>(it) } ?: emptyList()
            prefs[senderKey] = json.encodeToString(current.filterNot {
                it.senders.any { s -> normalise(s) == normalise(sender) }
            } + BankRule(bank.trim(), listOf(sender.trim()), exactSender = true))
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: RulesFile? = null

    /** Sender text reduced to letters and digits, so "ENBD-Alert" and "ENBD Alert" match. */
    fun normalise(s: String): String =
        s.uppercase().filter { it.isLetterOrDigit() }

    fun load(context: Context): RulesFile {
        cached?.let { return it }
        val parsed = runCatching {
            context.assets.open("rules.json").bufferedReader().use { it.readText() }
        }.mapCatching {
            json.decodeFromString<RulesFile>(it)
        }.getOrElse {
            // A broken or missing rules file must not take the app down; an
            // empty rule set degrades to "nothing recognised" and everything
            // lands in the unparsed bucket, which is visible and honest.
            RulesFile()
        }
        cached = parsed
        return parsed
    }

    /**
     * Which bank sent this, if any. Matches on the sender ID first because it is
     * the reliable signal; falls back to the bank's name appearing in the body,
     * which covers messages relayed through a generic gateway.
     */
    fun bankFor(rules: RulesFile, sender: String, body: String): String? {
        val s = normalise(sender)
        if (s.isNotEmpty()) {
            rules.banks.firstOrNull { bank ->
                (!bank.exactSender && s == normalise(bank.name)) || bank.senders.any { token ->
                    val t = normalise(token)
                    t.isNotEmpty() && (s == t || (!bank.exactSender &&
                        s.startsWith(t) && s.removePrefix(t) in setOf("ALERT", "ALERTS", "SMS", "UAE", "BANK")))
                }
            }?.let { return it.name }
        }
        val b = normalise(body)
        return rules.banks.firstOrNull { bank ->
            val t = normalise(bank.name)
            !bank.exactSender && t.length >= 6 && b.contains(t)
        }?.name
    }

    /**
     * Best-guess category from the merchant name. Substring matching, longest
     * key first so "LIFE PHARMACY" beats a bare "LIFE" if both are present.
     */
    fun categoryFor(rules: RulesFile, merchant: String): Category {
        val m = merchant.uppercase()
        val hit = rules.merchantCategories.entries
            .filter { m.contains(it.key.uppercase()) }
            .maxByOrNull { it.key.length }
            ?: return Category.OTHER
        return runCatching { Category.valueOf(hit.value) }.getOrDefault(Category.OTHER)
    }
}
