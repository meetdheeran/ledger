package com.meetdheeran.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MIN_PASSWORD = 4

@Composable
private fun GateFrame(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Ink.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(subtitle, color = Ink.muted, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(30.dp))
        content()
    }
}

@Composable
private fun PasswordField(
    value: String,
    label: String,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Ink.faint, fontSize = 13.sp) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink.text,
            unfocusedTextColor = Ink.text,
            focusedBorderColor = Ink.accent,
            unfocusedBorderColor = Ink.line,
            cursorColor = Ink.accent,
            focusedContainerColor = Ink.surface,
            unfocusedContainerColor = Ink.surface
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun CreatePasswordScreen(onCreate: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }

    val longEnough = first.length >= MIN_PASSWORD
    val matches = first == second
    val ready = longEnough && matches

    GateFrame(
        title = "Set a password",
        subtitle = "Ledger opens only with this password. It is stored as a hash on this " +
            "phone and cannot be recovered, so pick something you will remember."
    ) {
        PasswordField(first, "Password", ImeAction.Next) { first = it }
        Spacer(Modifier.height(12.dp))
        PasswordField(second, "Again", ImeAction.Done, onDone = { if (ready) onCreate(first) }) { second = it }
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                first.isEmpty() -> "At least $MIN_PASSWORD characters."
                !longEnough -> "At least $MIN_PASSWORD characters."
                second.isEmpty() -> "Type it once more."
                !matches -> "The two do not match."
                else -> "Ready."
            },
            color = if (ready) Ink.accent else Ink.faint,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Create", enabled = ready) { onCreate(first) }
    }
}

@Composable
fun UnlockScreen(error: String?, onUnlock: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    GateFrame(
        title = "Ledger",
        subtitle = "Enter your password."
    ) {
        PasswordField(password, "Password", ImeAction.Done, onDone = { onUnlock(password) }) {
            password = it
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = Ink.debit, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Open", enabled = password.isNotEmpty()) { onUnlock(password) }
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    GateFrame(
        title = "Read your bank messages",
        subtitle = "Ledger builds your spending record from the bank SMS already on this phone. " +
            "It reads them here and nowhere else — the app has no internet permission at all, " +
            "so nothing can be sent anywhere even by accident.\n\n" +
            "Messages that are not from a bank are ignored and never stored."
    ) {
        PrimaryButton("Allow message access") { onGrant() }
        Spacer(Modifier.height(14.dp))
        Text(
            "Without this there is nothing for Ledger to show.",
            color = Ink.faint,
            fontSize = 13.sp
        )
    }
}

@Composable
fun ImportingScreen(note: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Ink.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(18.dp))
            Text(note, color = Ink.muted, fontSize = 15.sp)
        }
    }
}
