package com.meetdheeran.ledger.ui

import com.meetdheeran.ledger.data.Category
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Amounts are stored in minor units; every display of money goes through here. */
fun formatMoney(minor: Long, currency: String = "AED", withCurrency: Boolean = true): String {
    val negative = minor < 0
    val abs = kotlin.math.abs(minor)
    val major = abs / 100
    val cents = abs % 100
    val grouped = major.toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
    val sign = if (negative) "-" else ""
    val body = "$sign$grouped.${cents.toString().padStart(2, '0')}"
    return if (withCurrency) "$currency $body" else body
}

/** The big headline number reads better without the fils. */
fun formatMoneyShort(minor: Long, currency: String = "AED"): String {
    val major = minor / 100
    val grouped = major.toString().reversed().chunked(3).joinToString(",").reversed()
    return "$currency $grouped"
}

private val dayFormat = SimpleDateFormat("d MMM", Locale.getDefault())
private val dayTimeFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

fun formatDay(ts: Long): String = dayFormat.format(ts)
fun formatDayTime(ts: Long): String = dayTimeFormat.format(ts)
fun formatMonth(ts: Long): String = monthFormat.format(ts)

/** Merchant names arrive SHOUTED; title case is easier to scan in a list. */
fun prettyMerchant(raw: String): String =
    raw.split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            if (word.length <= 3 && word.all { it.isUpperCase() }) word
            else word.lowercase().replaceFirstChar { it.uppercase() }
        }

/** Start and end of the calendar month containing [now]. */
fun monthBounds(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return start to (cal.timeInMillis - 1)
}

/** A stable colour per category so the breakdown reads the same every time. */
fun categoryTint(category: Category): androidx.compose.ui.graphics.Color = when (category) {
    Category.FOOD -> androidx.compose.ui.graphics.Color(0xFFFF9F7A)
    Category.GROCERIES -> androidx.compose.ui.graphics.Color(0xFF7ADFA0)
    Category.TRANSPORT -> androidx.compose.ui.graphics.Color(0xFF7AB8FF)
    Category.SHOPPING -> androidx.compose.ui.graphics.Color(0xFFC79BFF)
    Category.BILLS -> androidx.compose.ui.graphics.Color(0xFFFFD27A)
    Category.CASH -> androidx.compose.ui.graphics.Color(0xFF9FA8B2)
    Category.TRANSFER -> androidx.compose.ui.graphics.Color(0xFF7ADCE0)
    Category.HEALTH -> androidx.compose.ui.graphics.Color(0xFFFF8FB0)
    Category.ENTERTAINMENT -> androidx.compose.ui.graphics.Color(0xFFB0E07A)
    Category.INCOME -> Ink.credit
    Category.OTHER -> Ink.faint
}
