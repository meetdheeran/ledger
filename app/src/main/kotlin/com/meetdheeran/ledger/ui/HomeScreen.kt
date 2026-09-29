package com.meetdheeran.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetdheeran.ledger.MainViewModel
import com.meetdheeran.ledger.core.*
import com.meetdheeran.ledger.data.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home), ACTIVITY("Activity", Icons.AutoMirrored.Outlined.ReceiptLong),
    INSIGHTS("Insights", Icons.Outlined.BarChart), CARDS("Cards", Icons.Outlined.CreditCard),
    INBOX("Inbox", Icons.Outlined.Inbox)
}

private val contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)

@Composable
fun HomeScreen(vm: MainViewModel) {
    var tabName by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    val tab = Tab.valueOf(tabName)
    val all by vm.recent.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val query by vm.activityQuery.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val unread by vm.unparsedCount.collectAsStateWithLifecycle()
    val summary = remember(all, month) { Analytics.summary(all, month) }
    var picking by remember { mutableStateOf<Txn?>(null) }
    var categorising by remember { mutableStateOf<Txn?>(null) }
    var renaming by remember { mutableStateOf<Card?>(null) }
    var editingBudget by remember { mutableStateOf(false) }
    fun activity(q: ActivityQuery = ActivityQuery()) {
        vm.setActivityQuery(q)
        tabName = Tab.ACTIVITY.name
    }
    Column(Modifier.fillMaxSize().background(Ink.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ledger", color = Ink.text, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text("YOUR MONEY, CLEARLY", color = Ink.accent, fontSize = 9.sp, letterSpacing = 2.sp)
            }
            IconButton(onClick = vm::relock) { Icon(Icons.Outlined.Lock, "Lock Ledger", tint = Ink.muted) }
        }
        if (tab != Tab.INBOX && !(tab == Tab.ACTIVITY && query.allHistory)) MonthNavigator(month, vm::changeMonth)
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> Overview(vm, summary, budget, onBudget = { editingBudget = true },
                    onActivity = { activity(it) }, onTxn = { picking = it }, onInsights = { tabName = Tab.INSIGHTS.name })
                Tab.ACTIVITY -> ActivityScreen(all, cards, month, query, vm::setActivityQuery) { picking = it }
                Tab.INSIGHTS -> InsightsScreen(all, summary, month) { category -> activity(ActivityQuery(group = ActivityGroup.SPENDING, category = category)) }
                Tab.CARDS -> CardsScreen(cards, summary, onRename = { renaming = it }) { card ->
                    activity(ActivityQuery(card = CardKey(card.bank, card.last4)))
                }
                Tab.INBOX -> InboxScreen(vm, all.count { it.kind == TransactionKind.REVIEW }) {
                    activity(ActivityQuery(group = ActivityGroup.REVIEW, allHistory = true))
                }
            }
        }
        NavigationBar(containerColor = Ink.surface, tonalElevation = 0.dp) {
            Tab.entries.forEach { destination ->
                NavigationBarItem(selected = tab == destination, onClick = { tabName = destination.name },
                    icon = {
                        BadgedBox(badge = { if (destination == Tab.INBOX && unread > 0) Badge { Text(unread.toString()) } }) {
                            Icon(destination.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }, label = { Text(destination.label, fontSize = 11.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink.accent, selectedTextColor = Ink.accent,
                        indicatorColor = Ink.accent.copy(alpha = .12f), unselectedIconColor = Ink.muted, unselectedTextColor = Ink.muted))
            }
        }
    }
    picking?.let { txn ->
        TransactionSheet(txn, onDismiss = { picking = null }, onKind = { vm.classify(txn, it); picking = null },
            onCategory = { categorising = txn; picking = null })
    }
    categorising?.let { txn ->
        CategorySheet(txn.category, prettyMerchant(txn.merchant), onDismiss = { categorising = null }) {
            vm.recategorise(txn, it); categorising = null
        }
    }
    renaming?.let { card ->
        RenameDialog(card.label.orEmpty(), onDismiss = { renaming = null }) { vm.renameCard(card.id, it); renaming = null }
    }
    if (editingBudget) BudgetDialog(budget, month, onDismiss = { editingBudget = false }) {
        vm.setBudget(it); editingBudget = false
    }
}

@Composable
private fun MonthNavigator(month: YearMonth, onMove: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMove(-1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous month", tint = Ink.muted) }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = Ink.text, fontSize = 15.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onMove(1) }, enabled = month < YearMonth.now()) {
            Icon(Icons.Outlined.ChevronRight, "Next month", tint = if (month < YearMonth.now()) Ink.muted else Ink.line)
        }
    }
}

@Composable
private fun Overview(vm: MainViewModel, summary: MonthSummary, budget: Long, onBudget: () -> Unit,
    onActivity: (ActivityQuery) -> Unit, onTxn: (Txn) -> Unit, onInsights: () -> Unit) {
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val lastImport by vm.lastImport.collectAsStateWithLifecycle()
    val scanError by vm.scanError.collectAsStateWithLifecycle()
    val reviewCount by vm.reviewCount.collectAsStateWithLifecycle()
    val foreign = summary.transactions.count { it.currency != "AED" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF13392F), Color(0xFF14212D))))
                .border(1.dp, Ink.accent.copy(alpha = .18f), RoundedCornerShape(28.dp)).padding(23.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ACTUAL SPENDING", color = Ink.accent, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ShoppingBag, null, tint = Ink.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(formatMoney(summary.spending), color = Ink.text,
                    fontSize = if (summary.spending >= 100_000_000L) 28.sp else 36.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Text("Purchases, bills & fees · AED", color = Color(0xFFB3CBC3), fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${summary.purchases} expenses", color = Ink.text, fontSize = 12.sp)
                    Text("View activity  →", color = Ink.accent, fontSize = 12.sp,
                        modifier = Modifier.clickable { onActivity(ActivityQuery(group = ActivityGroup.SPENDING)) }.padding(4.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Income", formatMoney(summary.income), Ink.credit, Modifier.weight(1f))
                Metric("Daily average", formatMoney(summary.dailyAverage), Ink.blue, Modifier.weight(1f))
            }
        }
        item { BudgetPanel(summary.spending, budget, onBudget) }
        if (reviewCount > 0) item {
            Panel(modifier = Modifier.clickable { onActivity(ActivityQuery(group = ActivityGroup.REVIEW, allHistory = true)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.Rule, null, tint = Ink.warn)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("$reviewCount to review", color = Ink.warn, fontWeight = FontWeight.SemiBold)
                        Text("All history · excluded from totals", color = Ink.muted, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Spending breakdown", color = Ink.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onInsights) { Text("Insights", color = Ink.accent) }
            }
            Panel {
                if (summary.categories.isEmpty()) EmptyNote("Your spending categories will appear here.")
                summary.categories.take(3).forEach { (category, total) ->
                    Box(Modifier.clickable { onActivity(ActivityQuery(group = ActivityGroup.SPENDING, category = category)) }) {
                        BarRow(category.label, formatMoney(total), if (summary.spending > 0) total.toFloat() / summary.spending else 0f, categoryTint(category))
                    }
                }
            }
        }
        item {
            SectionTitle("Other money movements · AED")
            Panel {
                listOf(TransactionKind.TRANSFER_IN, TransactionKind.TRANSFER_OUT, TransactionKind.REFUND, TransactionKind.CASH_WITHDRAWAL).forEach { kind ->
                    val total = summary.transactions.filter { it.kind == kind && it.currency == "AED" }.sumOf { it.amountMinor }
                    MoneyRow(kind.label, formatMoney(total))
                }
                Spacer(Modifier.height(8.dp))
                Text("Transfers, repayments, refunds and cash withdrawals are separate from spending.", color = Ink.muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        if (foreign > 0) item {
            Notice("$foreign foreign-currency alerts", "Shown in Activity, excluded from AED totals. No conversion is assumed.")
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent activity", color = Ink.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onActivity(ActivityQuery()) }) { Text("See all", color = Ink.accent) }
            }
        }
        if (summary.transactions.isEmpty()) item { EmptyNote("No activity in this month. Rescan to read the last 30 days of bank messages.") }
        items(summary.transactions.sortedByDescending { it.timestamp }.take(5), key = { it.id }) { TxnRow(it) { onTxn(it) } }
        item {
            OutlinedButton(onClick = vm::rescan, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Sync, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(if (scanning) "Scanning messages…" else "Rescan bank messages")
            }
            scanError?.let { Text(it, color = Ink.warn, fontSize = 12.sp) }
            lastImport?.let { result ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Last scan: ${result.transactions} transactions matched; ${result.unreadable} unreadable.",
                        color = Ink.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::dismissImportNote) { Icon(Icons.Outlined.Close, "Dismiss scan summary", tint = Ink.muted) }
                }
            }
        }
    }
}

@Composable
private fun ActivityScreen(all: List<Txn>, cards: List<Card>, month: YearMonth, query: ActivityQuery,
    onQuery: (ActivityQuery) -> Unit, onTxn: (Txn) -> Unit) {
    val visible = remember(all, month, query) { Analytics.filter(all, month, query) }
    val groups = remember(visible) { visible.groupBy { Analytics.date(it.timestamp) } }
    var filters by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Activity", color = Ink.text, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = query.search, onValueChange = { onQuery(query.copy(search = it)) },
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search merchant, bank, card or amount", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                trailingIcon = { if (query.search.isNotEmpty()) IconButton(onClick = { onQuery(query.copy(search = "")) }) { Icon(Icons.Outlined.Close, "Clear search") } })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ActivityGroup.entries.forEach { group ->
                    FilterChip(selected = query.group == group, onClick = { onQuery(query.copy(group = group)) }, label = { Text(group.label, fontSize = 12.sp) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${visible.size} results · ${if (query.allHistory) "all history" else "selected month"}", color = Ink.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { filters = true }) { Icon(Icons.Outlined.Tune, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Filters") }
            }
            if (query.card != null || query.category != null || query.currency != null || query.allHistory || query.oldestFirst) {
                Text(listOfNotNull(query.card?.let { "${it.bank} ••${it.last4}" }, query.category?.label, query.currency,
                    if (query.oldestFirst) "Oldest first" else null).joinToString(" · "), color = Ink.accent, fontSize = 12.sp)
                TextButton(onClick = { onQuery(ActivityQuery()) }) { Text("Reset all filters") }
            }
        }
        if (visible.isEmpty()) item { Panel { EmptyNote("No matching transactions. Try another month or clear the filters.") } }
        groups.forEach { (day, rows) ->
            item(key = "date-$day") { SectionTitle(day.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), Modifier.padding(top = 12.dp)) }
            items(rows, key = { "txn-${it.id}" }) { TxnRow(it) { onTxn(it) } }
        }
    }
    if (filters) ActivityFiltersDialog(query, cards, all.map { it.currency }.distinct().sorted(),
        onDismiss = { filters = false }) { onQuery(it); filters = false }
}

@Composable
private fun InsightsScreen(all: List<Txn>, summary: MonthSummary, month: YearMonth, onCategory: (Category) -> Unit) {
    val history = remember(all, month) { (5L downTo 0L).map { month.minusMonths(it).let { m -> m to Analytics.summary(all, m).spending } } }
    val previous = history[4].second
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("Insights", color = Ink.text, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text("A closer look at your AED spending", color = Ink.muted, fontSize = 13.sp)
        }
        item {
            Panel {
                SectionTitle("Six-month spending · AED")
                val max = history.maxOf { it.second }.coerceAtLeast(1L)
                Row(Modifier.fillMaxWidth().height(155.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
                    history.forEach { (m, value) ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatMoneyShort(value).removePrefix("AED "), color = Ink.muted, fontSize = 9.sp, maxLines = 1)
                            Spacer(Modifier.height(5.dp))
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth().fillMaxHeight((value.toFloat() / max).coerceIn(.02f, 1f))
                                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                    .background(if (m == month) Ink.accent else Ink.blue.copy(alpha = .45f)))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(m.format(DateTimeFormatter.ofPattern("MMM")), color = Ink.muted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                MoneyRow("Selected month", formatMoney(summary.spending))
                MoneyRow("Previous full month", formatMoney(previous))
                Text("Based on imported messages. Older months may be incomplete; the current month is still in progress.", color = Ink.muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Largest expense", summary.transactions.filter(Analytics::spending).maxOfOrNull { it.amountMinor }?.let { formatMoney(it) } ?: "—", Ink.purple, Modifier.weight(1f))
                Metric("Expense count", summary.purchases.toString(), Ink.blue, Modifier.weight(1f))
            }
        }
        item {
            SectionTitle("Category share · tap to explore")
            Panel {
                if (summary.categories.isEmpty()) EmptyNote("No spending recorded for this month.")
                summary.categories.forEach { (cat, total) ->
                    Box(Modifier.clickable { onCategory(cat) }) {
                        BarRow(cat.label, "${if (summary.spending > 0) total * 100 / summary.spending else 0}% · ${formatMoney(total, withCurrency = false)}",
                            if (summary.spending > 0) total.toFloat() / summary.spending else 0f, categoryTint(cat))
                    }
                }
            }
        }
        item {
            SectionTitle("Top merchants")
            Panel {
                if (summary.merchants.isEmpty()) EmptyNote("Your top merchants will appear here.")
                summary.merchants.take(5).forEachIndexed { index, (merchant, total) -> MoneyRow("${index + 1}. ${prettyMerchant(merchant)}", formatMoney(total)) }
            }
        }
        item {
            SectionTitle("Spending by day")
            Panel {
                val days = summary.daily.mapIndexedNotNull { index, value -> if (value > 0) index + 1 to value else null }
                if (days.isEmpty()) EmptyNote("No spending days to show.")
                days.forEach { (day, value) ->
                    BarRow("${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} $day", formatMoney(value),
                        value.toFloat() / summary.daily.maxOrNull()!!.coerceAtLeast(1), Ink.blue)
                }
            }
        }
    }
}

@Composable
private fun CardsScreen(cards: List<Card>, summary: MonthSummary, onRename: (Card) -> Unit, onOpen: (Card) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Your cards", color = Ink.text, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text("${cards.size} detected · select a card to explore activity", color = Ink.muted, fontSize = 12.sp)
        }
        if (cards.isEmpty()) item { Panel { EmptyNote("Cards appear when a bank message identifies a card number. Account numbers are kept separate.") } }
        items(cards, key = { it.id }) { card ->
            val rows = summary.transactions.filter { it.bank == card.bank && it.cardLast4 == card.last4 }
            val spending = rows.filter(Analytics::spending)
            val tint = if (card.id % 2L == 0L) Ink.blue else Ink.purple
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = .22f), Ink.surface)))
                .border(1.dp, tint.copy(alpha = .2f), RoundedCornerShape(25.dp)).clickable { onOpen(card) }.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(card.bank.uppercase(), color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.CreditCard, null, tint = tint)
                }
                Spacer(Modifier.height(20.dp))
                Text("••••   ••••   ••••   ${card.last4}", color = Ink.text, fontSize = 20.sp, letterSpacing = 1.sp)
                Text(card.label ?: "Tap to view activity", color = Ink.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(20.dp))
                Text(formatMoney(spending.sumOf { it.amountMinor }), color = Ink.text, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("Spending this period · ${spending.size} expenses", color = Ink.muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onOpen(card) }) { Text("View activity", color = tint) }
                    TextButton(onClick = { onRename(card) }) { Text("Rename", color = Ink.muted) }
                }
            }
        }
    }
}

@Composable
private fun InboxScreen(vm: MainViewModel, reviewCount: Int, onReview: () -> Unit) {
    val unread by vm.unparsed.collectAsStateWithLifecycle()
    var addingBank by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Inbox", color = Ink.text, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text("Keep your numbers accurate", color = Ink.muted, fontSize = 13.sp)
        }
        if (reviewCount > 0) item { Panel(modifier = Modifier.clickable(onClick = onReview)) {
            Text("$reviewCount transactions need a type", color = Ink.warn, fontWeight = FontWeight.SemiBold)
            Text("Review now →", color = Ink.accent, modifier = Modifier.padding(top = 8.dp))
        } }
        item {
            OutlinedButton(onClick = { addingBank = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add a missing bank sender")
            }
            Text("Unreadable bank messages stay here and are excluded from totals.", color = Ink.muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
        if (unread.isEmpty()) item { Panel { EmptyNote("You're all caught up. No unreadable messages from recognised senders.") } }
        items(unread, key = { it.id }) { sms -> Panel {
            Text("${sms.sender} · ${formatDay(sms.timestamp)}", color = Ink.accent, fontSize = 12.sp)
            Text(sms.body, color = Ink.text, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
            TextButton(onClick = { vm.dismissUnparsed(sms.id) }) { Text("Dismiss") }
        } }
        item { Text("Ledger reads bank messages on this phone. No bank login or internet connection is used.", color = Ink.muted, fontSize = 12.sp, lineHeight = 18.sp) }
    }
    if (addingBank) AddBankDialog(onDismiss = { addingBank = false }) { sender, bank -> vm.addBankSender(sender, bank); addingBank = false }
}

@Composable
private fun Metric(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Panel(modifier, padding = 16) {
        Text(label, color = Ink.muted, fontSize = 11.sp)
        Text(value, color = tint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp), maxLines = 2)
    }
}

@Composable
private fun MoneyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = Ink.muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(value, color = Ink.text, fontSize = 13.sp)
    }
}

@Composable
private fun Notice(title: String, detail: String) {
    Panel { Text(title, color = Ink.warn, fontSize = 14.sp); Text(detail, color = Ink.muted, fontSize = 12.sp, lineHeight = 18.sp) }
}

@Composable
private fun BudgetPanel(spent: Long, budget: Long, onEdit: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Monthly budget", color = Ink.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(if (budget > 0) "Edit" else "Set budget", color = Ink.accent) }
        }
        if (budget > 0) {
            val over = spent > budget
            Text(if (over) "${formatMoney(spent - budget)} over budget" else "${formatMoney(budget - spent)} remaining",
                color = if (over) Ink.debit else Ink.accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { (spent.toFloat() / budget).coerceIn(0f, 1f) },
                color = if (over) Ink.debit else Ink.accent, trackColor = Ink.line,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).height(7.dp).clip(RoundedCornerShape(8.dp)))
            Text("${formatMoney(spent)} of ${formatMoney(budget)} · AED spending only", color = Ink.muted, fontSize = 11.sp)
        } else Text("Set a spending target for this month and track what's left.", color = Ink.muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}
