package com.meetdheeran.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetdheeran.ledger.BuildConfig
import com.meetdheeran.ledger.MainViewModel
import com.meetdheeran.ledger.data.Card
import com.meetdheeran.ledger.data.Category
import com.meetdheeran.ledger.data.Txn

private enum class Tab(val label: String) { HOME("Home"), CARDS("Cards"), INBOX("Inbox") }

@Composable
fun HomeScreen(vm: MainViewModel) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    val unparsedCount by vm.unparsedCount.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.bg)
            .statusBarsPadding()
    ) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> DashboardTab(vm)
                Tab.CARDS -> CardsTab(vm)
                Tab.INBOX -> UnparsedTab(vm)
            }
        }
        BottomBar(current = tab, inboxBadge = unparsedCount) { tab = it }
    }
}

@Composable
private fun BottomBar(current: Tab, inboxBadge: Int, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink.bg)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Tab.entries.forEach { t ->
            val selected = t == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(t) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (t == Tab.INBOX && inboxBadge > 0) "${t.label} · $inboxBadge" else t.label,
                    color = if (selected) Ink.text else Ink.faint,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ---- home ------------------------------------------------------------------

@Composable
private fun DashboardTab(vm: MainViewModel) {
    val spend by vm.spendThisMonth.collectAsStateWithLifecycle()
    val income by vm.incomeThisMonth.collectAsStateWithLifecycle()
    val byCategory by vm.byCategory.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val lastImport by vm.lastImport.collectAsStateWithLifecycle()

    var picking by remember { mutableStateOf<Txn?>(null) }
    val topCategory = byCategory.firstOrNull()?.totalMinor ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp
        )
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatMonth(System.currentTimeMillis()),
                    color = Ink.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                QuietButton("Rescan") { vm.rescan() }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatMoneyShort(spend),
                color = Ink.text,
                fontSize = 44.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (income > 0) "spent · ${formatMoney(income)} in" else "spent",
                color = Ink.faint,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(20.dp))
        }

        lastImport?.let { r ->
            item {
                Panel {
                    Text(
                        "Read ${r.scanned} messages: ${r.transactions} transactions" +
                            if (r.unreadable > 0) ", ${r.unreadable} it could not read" else "",
                        color = Ink.muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        if (r.unreadable > 0) {
                            Text(
                                "They are in Inbox.",
                                color = Ink.warn,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Text(
                            "Dismiss",
                            color = Ink.accent,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { vm.dismissImportNote() }
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        if (byCategory.isNotEmpty()) {
            item {
                SectionTitle("Where it went")
                Panel {
                    byCategory.forEach { row ->
                        BarRow(
                            label = row.category.label,
                            amount = formatMoney(row.totalMinor, withCurrency = false),
                            fraction = if (topCategory > 0) row.totalMinor.toFloat() / topCategory else 0f,
                            tint = categoryTint(row.category)
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        item { SectionTitle("Recent") }

        if (recent.isEmpty()) {
            item {
                Panel {
                    EmptyNote(
                        "Nothing yet. If your bank texts you about payments, they will appear " +
                            "here — pull Rescan above once a message has arrived."
                    )
                }
                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.height(12.dp))
                    DebugSampleButton(vm)
                }
            }
        } else {
            items(recent, key = { it.id }) { txn ->
                TxnRow(txn) { picking = txn }
            }
        }
    }

    picking?.let { txn ->
        CategorySheet(
            current = txn.category,
            merchant = prettyMerchant(txn.merchant),
            onDismiss = { picking = null }
        ) { chosen ->
            vm.recategorise(txn, chosen)
            picking = null
        }
    }
}

// ---- cards -----------------------------------------------------------------

@Composable
private fun CardsTab(vm: MainViewModel) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val byCard by vm.byCard.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Card?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp
        )
    ) {
        item {
            Text("Cards", color = Ink.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Added by themselves whenever a new card appears in a message.",
                color = Ink.faint,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(20.dp))
        }

        if (cards.isEmpty()) {
            item { Panel { EmptyNote("No cards seen yet.") } }
        } else {
            items(cards, key = { it.id }) { card ->
                val total = byCard.firstOrNull { it.cardLast4 == card.last4 && it.bank == card.bank }
                Panel(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                card.displayName,
                                color = Ink.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${card.bank} · ••${card.last4}" +
                                    (total?.let { "  ·  ${it.txnCount} this month" } ?: ""),
                                color = Ink.faint,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            formatMoney(total?.totalMinor ?: 0L, withCurrency = false),
                            color = Ink.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Rename",
                        color = Ink.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { renaming = card }
                    )
                }
            }
        }
    }

    renaming?.let { card ->
        RenameDialog(
            initial = card.label.orEmpty(),
            onDismiss = { renaming = null }
        ) { newName ->
            vm.renameCard(card.id, newName)
            renaming = null
        }
    }
}

// ---- inbox (unparsed) ------------------------------------------------------

@Composable
private fun UnparsedTab(vm: MainViewModel) {
    val items by vm.unparsed.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp
        )
    ) {
        item {
            Text("Inbox", color = Ink.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Bank messages Ledger could not read. They are shown rather than dropped, " +
                    "so a total is never quietly missing something.",
                color = Ink.faint,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(20.dp))
        }

        if (BuildConfig.DEBUG) {
            item {
                DebugSampleButton(vm)
                Spacer(Modifier.height(18.dp))
            }
        }

        if (items.isEmpty()) {
            item { Panel { EmptyNote("Nothing unread. Every bank message so far has been understood.") } }
        } else {
            items(items, key = { it.id }) { sms ->
                Panel(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        "${sms.sender} · ${formatDay(sms.timestamp)}",
                        color = Ink.faint,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(sms.body, color = Ink.muted, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Dismiss",
                        color = Ink.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { vm.dismissUnparsed(sms.id) }
                    )
                }
            }
        }
    }
}

// ---- debug -----------------------------------------------------------------

/**
 * Debug builds only. A phone with no SIM has no bank messages, so there is
 * nothing for the parser to work on and no way to see whether any of this is
 * right. This feeds invented messages through the real importer - same parser,
 * same card detection, same dashboard - so the pipeline is testable at all.
 */
@Composable
private fun DebugSampleButton(vm: MainViewModel) {
    Panel {
        Text("Debug build", color = Ink.warn, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "No bank messages on this phone. Load invented ones to exercise the parser, " +
                "card detection and the dashboard for real. Safe to tap twice — importing " +
                "the same message again does nothing.",
            color = Ink.muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Load sample messages") { vm.loadSamples() }
    }
}

// ---- sheets and dialogs ----------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    current: Category,
    merchant: String,
    onDismiss: () -> Unit,
    onPick: (Category) -> Unit
) {
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Ink.surface,
        dragHandle = null
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            Text(merchant, color = Ink.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Changing this also fixes every past and future transaction from here.",
                color = Ink.faint,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(18.dp))
            // A simple wrap: two per row keeps the labels readable at this width.
            Category.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
                    pair.forEach { cat ->
                        Box(Modifier.weight(1f).padding(end = 9.dp)) {
                            CategoryChip(cat, selected = cat == current) { onPick(cat) }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.surface,
        titleContentColor = Ink.text,
        textContentColor = Ink.muted,
        title = { Text("Name this card", fontSize = 18.sp) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("e.g. Salary card", color = Ink.faint, fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Ink.text,
                    unfocusedTextColor = Ink.text,
                    focusedBorderColor = Ink.accent,
                    unfocusedBorderColor = Ink.line,
                    cursorColor = Ink.accent,
                    focusedContainerColor = Ink.surfaceHigh,
                    unfocusedContainerColor = Ink.surfaceHigh
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Save", color = Ink.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ink.muted) }
        }
    )
}
