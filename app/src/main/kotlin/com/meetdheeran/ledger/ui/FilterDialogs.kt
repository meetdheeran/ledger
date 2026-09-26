package com.meetdheeran.ledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetdheeran.ledger.core.ActivityQuery
import com.meetdheeran.ledger.core.Analytics
import com.meetdheeran.ledger.core.CardKey
import com.meetdheeran.ledger.data.Card
import com.meetdheeran.ledger.data.Category
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
internal fun BudgetDialog(current: Long, month: YearMonth, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var input by rememberSaveable { mutableStateOf(if (current > 0) BigDecimal.valueOf(current, 2).toPlainString() else "") }
    val parsed = Analytics.parseBudget(input)
    AlertDialog(onDismissRequest = onDismiss, containerColor = Ink.surface,
        title = { Text("Monthly budget", color = Ink.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = Ink.accent)
                Text("Your target for purchases, bills and fees. Transfers and repayments stay excluded.", color = Ink.muted, fontSize = 13.sp)
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Budget in AED") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = input.isNotBlank() && parsed == null,
                    supportingText = { if (input.isNotBlank() && parsed == null) Text("Enter a positive amount with up to 2 decimal places.") })
                if (current > 0) TextButton(onClick = { onSave(0L) }) { Text("Remove this month's budget", color = Ink.muted) }
            }
        }, confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed > 0) { Text("Save budget") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityFiltersDialog(query: ActivityQuery, cards: List<Card>, currencies: List<String>,
    onDismiss: () -> Unit, onApply: (ActivityQuery) -> Unit) {
    var draft by remember { mutableStateOf(query) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filter activity", fontSize = 21.sp, color = Ink.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onApply(draft) }) { Text("Apply", color = Ink.accent) }
            }
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 28.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("All history", color = Ink.text)
                            Text("Include activity outside the selected month", color = Ink.muted, fontSize = 11.sp)
                        }
                        Switch(checked = draft.allHistory, onCheckedChange = { draft = draft.copy(allHistory = it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Oldest first", color = Ink.text, modifier = Modifier.weight(1f))
                        Switch(checked = draft.oldestFirst, onCheckedChange = { draft = draft.copy(oldestFirst = it) })
                    }
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Card")
                    FilterChip(selected = draft.card == null, onClick = { draft = draft.copy(card = null) }, label = { Text("All cards & accounts") })
                }
                items(cards, key = { it.id }) { card ->
                    val key = CardKey(card.bank, card.last4)
                    FilterChip(selected = draft.card == key, onClick = { draft = draft.copy(card = key) },
                        label = { Text(card.displayName, fontSize = 13.sp) })
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Category")
                    FilterChip(selected = draft.category == null, onClick = { draft = draft.copy(category = null) }, label = { Text("All categories") })
                    Category.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { category ->
                                FilterChip(selected = draft.category == category, onClick = { draft = draft.copy(category = category) },
                                    label = { Text(category.label, fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Currency")
                    FilterChip(selected = draft.currency == null, onClick = { draft = draft.copy(currency = null) }, label = { Text("All currencies") })
                    currencies.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { currency -> FilterChip(selected = draft.currency == currency,
                                onClick = { draft = draft.copy(currency = currency) }, label = { Text(currency) }) }
                        }
                    }
                    TextButton(onClick = { draft = ActivityQuery() }) { Text("Reset all filters") }
                }
            }
        }
    }
}
