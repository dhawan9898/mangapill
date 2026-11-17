package com.mangapill.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.sync.CloudSync
import com.mangapill.app.ui.components.IconTile
import com.mangapill.app.ui.components.PillButton
import com.mangapill.app.ui.components.SectionLabel
import com.mangapill.app.ui.components.SettingsCard
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Settings: sign in to keep reading progress, My List and settings the same on every device. */
@Composable
fun CloudAccountCard(cloud: CloudSync) {
    val status by cloud.status.collectAsState()
    // Built without a sync server: nothing to offer.
    if (!status.configured) return

    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun submit(create: Boolean) {
        if (busy) return
        if (!Regex("^\\S+@\\S+\\.\\S+$").matches(email.trim())) { message = "Enter your email address."; return }
        if (password.length < 6) { message = "The password needs at least 6 characters."; return }
        busy = true
        message = null
        scope.launch {
            try {
                if (create) {
                    if (cloud.signUp(email, password)) message = "Account created. Open the confirmation email, then sign in here."
                } else {
                    cloud.signIn(email, password)
                }
                password = ""
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Something went wrong. Try again."
            } finally {
                busy = false
            }
        }
    }

    SectionLabel("Account & Sync")
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.CloudSync)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        status.email ?: "Sync between devices",
                        color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            status.email == null -> "Sign in to keep your reading progress, My List and settings the same on every device."
                            status.syncing -> "Syncing…"
                            else -> syncedLabel(status.lastSyncedAt)
                        },
                        color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (status.email != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        "Sync now", { scope.launch { cloud.syncNow() } }, Modifier.weight(1f),
                        icon = Icons.Outlined.Sync, background = PurpleAccent, contentColor = Color.White
                    )
                    PillButton("Sign out", { scope.launch { cloud.signOut() } }, Modifier.weight(1f), icon = Icons.Outlined.Logout)
                }
            } else {
                val colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurpleAccent,
                    unfocusedBorderColor = TextMuted.copy(alpha = 0.35f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = PurpleAccent
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, singleLine = true,
                    placeholder = { Text("Email", color = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = colors
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    placeholder = { Text("Password", color = TextMuted) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = colors
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        if (busy) "Please wait…" else "Sign in", { submit(create = false) }, Modifier.weight(1f),
                        background = PurpleAccent, contentColor = Color.White
                    )
                    PillButton("Create account", { submit(create = true) }, Modifier.weight(1f))
                }
            }

            val problem = status.error?.let { "Sync problem: $it" }
            (message ?: problem)?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = if (message != null && status.error == null) PurpleAccent else Color(0xFFE5484D), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
    Spacer(Modifier.height(26.dp))
}

private fun syncedLabel(at: Long?): String {
    if (at == null) return "Not synced yet"
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 1 -> "Synced just now"
        minutes < 60 -> "Synced $minutes min ago"
        else -> "Synced " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(at))
    }
}
