package com.meetdheeran.ledger.sms

import android.content.Context
import com.meetdheeran.ledger.data.Category
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    val senders: List<String>
)

@Serializable
data class RulesFile(
    val banks: List<BankRule> = emptyList(),
    val merchantCategories: Map<String, String> = emptyMap()
)

object Rules {

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
                bank.senders.any { token ->
                    val t = normalise(token)
                    t.isNotEmpty() && s.contains(t)
                }
            }?.let { return it.name }
        }
        val b = normalise(body)
        return rules.banks.firstOrNull { bank ->
            val t = normalise(bank.name)
            t.length >= 3 && b.contains(t)
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
