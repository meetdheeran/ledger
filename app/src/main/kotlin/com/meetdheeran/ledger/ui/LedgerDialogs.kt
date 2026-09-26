package com.meetdheeran.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.meetdheeran.ledger.data.TransactionKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionSheet(txn: Txn, onDismiss: () -> Unit, onKind: (TransactionKind) -> Unit, onCategory: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            item {
                Text(prettyMerchant(txn.merchant), color = Ink.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(formatMoney(txn.amountMinor, txn.currency), color = Ink.accent, fontSize = 24.sp)
                Spacer(Modifier.height(10.dp))
                Text(if (txn.classificationOverridden) "Type confirmed by you" else "Type detected from the bank message",
                    color = Ink.muted, fontSize = 12.sp)
                Text(if (txn.kind.countsAsSpending) "Included in spending when the currency is AED."
                    else "Excluded from spending.", color = Ink.muted, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                SectionTitle("Bank message")
                Text(txn.body, color = Ink.muted, fontSize = 13.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(18.dp))
                SectionTitle("Transaction type")
                Text("A correction applies only to this transaction and stays after rescanning.",
                    color = Ink.muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            items(TransactionKind.entries.toList()) { kind ->
                Text((if (txn.kind == kind) "✓  " else "") + kind.label,
                    color = if (txn.kind == kind) Ink.accent else Ink.text, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onKind(kind) }.padding(vertical = 12.dp))
            }
            item {
                Spacer(Modifier.height(12.dp))
                if (txn.merchant != txn.bank && TransactionKind.entries.none { it.label == txn.merchant }) {
                    QuietButton("Category: ${txn.category.label}", onClick = onCategory)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
internal fun AddBankDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var sender by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Ink.surface,
        title = { Text("Add a UAE bank sender", color = Ink.text) },
        text = {
            Column {
                Text("Enter the sender exactly as it appears above your bank's SMS. Ledger will scan its last 30 days of messages.",
                    color = Ink.muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = sender, onValueChange = { sender = it }, label = { Text("SMS sender") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = bank, onValueChange = { bank = it }, label = { Text("Bank name") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(sender, bank) },
            enabled = sender.any { it.isLetterOrDigit() } && bank.isNotBlank()) { Text("Save & rescan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

// ---- sheets and dialogs ----------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategorySheet(
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
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp)) {
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
internal fun RenameDialog(
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
