package com.meetdheeran.ledger.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetdheeran.ledger.data.Category
import com.meetdheeran.ledger.data.Direction
import com.meetdheeran.ledger.data.Txn

/**
 * A rounded surface. `content` is last on purpose because it is a slot; every
 * helper below that takes an onClick keeps it as a plain parameter and has no
 * slot at all, so a trailing lambda can never be mistaken for one.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Int = 18,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ink.surface)
            .padding(padding.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Ink.faint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Ink.muted,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        modifier = modifier.padding(vertical = 6.dp)
    )
}

/** One transaction in a list. Tapping it opens the category picker. */
@Composable
fun TxnRow(txn: Txn, onClick: () -> Unit) {
    val isDebit = txn.direction == Direction.DEBIT
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(categoryTint(txn.category).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(categoryTint(txn.category))
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                prettyMerchant(txn.merchant),
                color = Ink.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(formatDayTime(txn.timestamp))
                    txn.cardLast4?.let { append("  ·  ••$it") }
                },
                color = Ink.faint,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = (if (isDebit) "-" else "+") + formatMoney(txn.amountMinor, txn.currency, withCurrency = false),
            color = if (isDebit) Ink.text else Ink.credit,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** A labelled proportion bar, used for the category breakdown. */
@Composable
fun BarRow(
    label: String,
    amount: String,
    fraction: Float,
    tint: Color
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        label = "bar"
    )
    Column(Modifier.padding(vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Ink.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(amount, color = Ink.muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(Ink.line)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Ink.accent else Ink.surfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Color(0xFF05130D) else Ink.faint,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, color = Ink.muted, fontSize = 14.sp)
    }
}

@Composable
fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) categoryTint(category).copy(alpha = 0.22f) else Ink.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp)
    ) {
        Text(
            category.label,
            color = if (selected) categoryTint(category) else Ink.muted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
